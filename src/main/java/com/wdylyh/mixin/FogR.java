package com.wdylyh.mixin;

import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.FogModifier;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.fog.LavaFogModifier;
import net.minecraft.client.render.fog.PowderSnowFogModifier;
import net.minecraft.client.render.fog.WaterFogModifier;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ColorHelper;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Filters fog per type (water / lava / powder snow / atmospheric) or per biome.
 *
 * getFogColor computes the fog color for the current camera submersion type and
 * biome, so the blacklist/whitelist can decide per fog instance. A filtered fog
 * keeps the original fog RGB but forces alpha to 0:
 *  - In fog.glsl the applied fog amount is scaled by the fog color alpha
 *    (fogValue * fogColor.a), so an alpha of 0 removes all fog blending.
 *  - terrain.fsh mixes FogColor.rgb (ignoring alpha) while a rebuilt chunk fades
 *    in via ChunkVisibility. Keeping the original RGB avoids a black skyline ring
 *    in biomes such as old growth pine taiga and birch forest.
 */
@Mixin(FogRenderer.class)
public abstract class FogR {

    // Cached target fog modifiers for the replacement system. All four concrete
    // FogModifier classes have public no-arg constructors and their getFogColor
    // generates the exact vanilla fog color for that fog type (atmospheric uses
    // the camera's current environment attribute interpolation).
    @Unique
    private static final FogModifier WM = new WaterFogModifier();
    @Unique
    private static final FogModifier LM = new LavaFogModifier();
    @Unique
    private static final FogModifier PSM = new PowderSnowFogModifier();
    @Unique
    private static final FogModifier AM = new AtmosphericFogModifier();

    // Reused across frames so the per-frame fog identity list does not allocate.
    // Only the render thread touches it, and it is consumed immediately.
    @Unique
    private static final List<String> FIDS = new ArrayList<>(4);

    @Inject(method = "getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)Lorg/joml/Vector4f;",
            at = @At("RETURN"), cancellable = true)
    private void onFogColor(Camera cam, float td, ClientWorld w, int tk, float i,
                            CallbackInfoReturnable<Vector4f> cir) {
        // 每帧只查询一次原生按键状态，供下面的过滤与替换判断共用。
        boolean kd = FR.revealDown();
        // 合并后的行为码只求值一次：FORCE（enable 模式按住）强制隐藏所有雾；
        // NORMAL 时再看"禁用雾"开关与 list 过滤；BYPASS 时过滤不生效。
        int bh = FR.bhv(FR.TYPE_FOG, kd);
        boolean hide = bh == FR.HOTKEY_BEHAVIOR_FORCE
                || (bh == FR.HOTKEY_BEHAVIOR_NORMAL
                && Cfg.Off.DISABLE_FOG.getBooleanValue()
                && FR.isFogFiltered(cam.getSubmersionType(), w, cam.getCameraPos(),
                kd, bh));
        if (hide) {
            // Mutate the original Vector4f in place (w=0) instead of allocating
            // a new object every frame. The base method returns a fresh instance,
            // so in-place mutation is safe and avoids per-frame GC pressure.
            cir.getReturnValue().w = 0.0f;
        }

        // Universal fog replacement: replace the RGB of the computed fog color
        // with the color of the target fog identity. The first identity (in the
        // same priority order as the fog filter) that has a rule wins.
        if (Rpl.isReplaceEnabled()
                // The reveal hotkey skips the replacement (shows the original fog).
                && !FR.isReplaceBlocked(FR.TYPE_FOG)) {
            FIDS.clear();
            FR.getFogIdentities(cam.getSubmersionType(), w, cam.getCameraPos(), FIDS);
            Vector4f col = cir.getReturnValue();
            for (String id : FIDS) {
                String tid = Rpl.getReplacementFog(id);
                if (tid == null) {
                    continue;
                }
                FogModifier mod = fogMod(tid);
                if (mod != null) {
                    int c = mod.getFogColor(w, cam, tk, td);
                    col.x = ColorHelper.getRedFloat(c);
                    col.y = ColorHelper.getGreenFloat(c);
                    col.z = ColorHelper.getBlueFloat(c);
                }
                break;
            }
        }
    }

    // Maps a target fog identity to the FogModifier that produces its color.
    // Submersion-type targets map to their exact modifiers; biome, dimension
    // and effect targets fall back to the atmospheric modifier (the camera's
    // current environment color).
    @Unique
    private static FogModifier fogMod(String id) {
        return switch (id) {
            case "water" -> WM;
            case "lava" -> LM;
            case "powder_snow" -> PSM;
            default -> AM;
        };
    }
}