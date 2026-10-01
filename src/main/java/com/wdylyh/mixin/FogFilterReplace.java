package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.ReplacementEngine;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.FogModifier;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.fog.LavaFogModifier;
import net.minecraft.client.render.fog.PowderSnowFogModifier;
import net.minecraft.client.render.fog.WaterFogModifier;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
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
public abstract class FogFilterReplace {

    // Cached target fog modifiers for the replacement system. All four concrete
    // FogModifier classes have public no-arg constructors and their getFogColor
    // generates the exact vanilla fog color for that fog type (atmospheric uses
    // the camera's current environment attribute interpolation).
    @Unique
    private static final FogModifier WATER_FOG_MODIFIER = new WaterFogModifier();
    @Unique
    private static final FogModifier LAVA_FOG_MODIFIER = new LavaFogModifier();
    @Unique
    private static final FogModifier POWDER_SNOW_FOG_MODIFIER = new PowderSnowFogModifier();
    @Unique
    private static final FogModifier ATMOSPHERIC_FOG_MODIFIER = new AtmosphericFogModifier();

    // Reused across frames so the per-frame fog identity list does not allocate.
    // Only the render thread touches it, and it is consumed immediately.
    @Unique
    private static final List<String> FOG_IDENTITIES = new ArrayList<>(4);

    @Inject(method = "getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)Lorg/joml/Vector4f;",
            at = @At("RETURN"), cancellable = true)
    private void onFogColor(Camera cam, float td, ClientWorld world, int tk, float i,
                            CallbackInfoReturnable<Vector4f> cir) {
        // 每帧只查询一次原生按键状态，供下面的过滤与替换判断共用。
        boolean keyDown = FilterEngine.revealDown();
        // 合并后的行为码只求值一次：FORCE（enable 模式按住）强制隐藏所有雾；
        // NORMAL 时再看"禁用雾"开关与 list 过滤；BYPASS 时过滤不生效。
        int behavior = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_FOG, keyDown);
        boolean hide = behavior == FilterEngine.HOTKEY_BEHAVIOR_FORCE
                || (behavior == FilterEngine.HOTKEY_BEHAVIOR_NORMAL
                && RenderConfig.Toggles.DISABLE_FOG.getBooleanValue()
                && FilterEngine.isFogFiltered(cam.getSubmersionType(), world, cam.getCameraPos(),
                keyDown, behavior));
        if (hide) {
            // Mutate the original Vector4f in place (w=0) instead of allocating
            // a new object every frame. The base method returns a fresh instance,
            // so in-place mutation is safe and avoids per-frame GC pressure.
            cir.getReturnValue().w = 0.0f;
        }

        // Universal fog replacement: replace the RGB of the computed fog color
        // with the color of the target fog identity. The first identity (in the
        // same priority order as the fog filter) that has a rule wins.
        // No master-switch gate here: the coordinate rules below are gated by
        // the coordinate replace toggle (global switch OFF), the global
        // fallback checks the master switch internally.
        if (!FilterEngine.isReplaceBlocked(FilterEngine.TYPE_FOG)) {
            FOG_IDENTITIES.clear();
            FilterEngine.getFogIdentities(cam.getSubmersionType(), world, cam.getCameraPos(), FOG_IDENTITIES);
            Vector4f col = cir.getReturnValue();
            // The camera position decides the region for coordinate aware fog
            // rules; a rule inside it wins over the global list.
            Vec3d camPos = cam.getCameraPos();
            for (String id : FOG_IDENTITIES) {
                String tid = ReplacementEngine.getReplacementFogAt(id, camPos.x, camPos.y, camPos.z, FilterEngine.TYPE_FOG);
                if (tid == null) {
                    tid = ReplacementEngine.getReplacementFog(id);
                }
                if (tid == null) {
                    continue;
                }
                FogModifier mod = fogMod(tid);
                if (mod != null) {
                    int c = mod.getFogColor(world, cam, tk, td);
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
            case "water" -> WATER_FOG_MODIFIER;
            case "lava" -> LAVA_FOG_MODIFIER;
            case "powder_snow" -> POWDER_SNOW_FOG_MODIFIER;
            default -> ATMOSPHERIC_FOG_MODIFIER;
        };
    }
}