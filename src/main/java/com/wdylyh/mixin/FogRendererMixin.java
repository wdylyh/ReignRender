package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/**
 * Disables fog by zeroing the alpha of the fog color right before it is
 * written into the fog UBO.
 *
 * In fog.glsl the applied fog amount is scaled by the fog color alpha
 * (fogValue * fogColor.a), so an alpha of 0 removes all fog blending while the
 * fog color RGB stays untouched. This keeps the terrain shader's
 * "mix(FogColor * ..., color, ChunkVisibility)" from blending towards black
 * (which happened with the old fogEnabled=false / empty-buffer approach and
 * made the render slightly darker).
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"))
    private void onApplyFog(ByteBuffer buffer, int ticks, Vector4f fogColor,
                            float environmentalStart, float environmentalEnd,
                            float renderDistanceStart, float renderDistanceEnd,
                            float skyEnd, float cloudEnd, CallbackInfo ci) {
        if (Configs.Disable.DISABLE_FOG.getBooleanValue()) {
            fogColor.w = 0.0f;
        }
    }
}
