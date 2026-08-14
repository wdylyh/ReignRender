package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    // ==================== 粒子 (Particles) ====================

    @Inject(method = "renderParticles", at = @At("HEAD"), cancellable = true)
    private void onRenderParticles(CallbackInfo ci) {
        // Full kill only applies when the master toggle is on AND the filter
        // mode is OFF (everything hidden); otherwise per-type filtering in
        // ParticleManager.addParticle handles the blacklist/whitelist.
        if (Configs.Disable.DISABLE_PARTICLES.getBooleanValue() &&
                Configs.Filter.PARTICLE_MODE.getOptionValue() == Configs.Filter.MODE_OFF) {
            MinecraftClient.getInstance().particleManager.clearParticles();
            ci.cancel();
        }
    }
}