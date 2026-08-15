package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import com.wdylyh.config.FilterRules;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
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
public abstract class FogRendererMixin {

    @Inject(method = "getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)Lorg/joml/Vector4f;",
            at = @At("RETURN"), cancellable = true)
    private void onGetFogColor(Camera camera, float tickDelta, ClientWorld world, int ticks, float i,
                               CallbackInfoReturnable<Vector4f> cir) {
        if (Configs.Disable.DISABLE_FOG.getBooleanValue()
                && FilterRules.isFogFiltered(camera.getSubmersionType(), world, camera.getCameraPos())) {
            // Mutate the original Vector4f in place (w=0) instead of allocating
            // a new object every frame. The base method returns a fresh instance,
            // so in-place mutation is safe and avoids per-frame GC pressure.
            cir.getReturnValue().w = 0.0f;
        }
    }
}
