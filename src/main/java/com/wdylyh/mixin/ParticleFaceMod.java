package com.wdylyh.mixin;

import com.wdylyh.client.ParticleFaceContext;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spawning context for the region particle face modification: publishes the
 * spawn type and position of the current {@code ParticleManager.addParticle}
 * call into {@link ParticleFaceContext} so the billboard sprite swap can look
 * it up. The state itself lives in the plain helper class, since a mixin class
 * must not contain non-private static methods.
 */
@Mixin(ParticleManager.class)
public class ParticleFaceMod {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"))
    private void rfSpawnHead(ParticleEffect fx, double x, double y, double z,
                             double vx, double vy, double vz,
                             CallbackInfoReturnable<Particle> cir) {
        ParticleFaceContext.set(fx.getType(), x, y, z);
    }

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("RETURN"))
    private void rfSpawnReturn(ParticleEffect fx, double x, double y, double z,
                               double vx, double vy, double vz,
                               CallbackInfoReturnable<Particle> cir) {
        ParticleFaceContext.clear();
    }
}
