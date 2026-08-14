package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import com.wdylyh.config.FilterRules;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {

    // Prevents particles of filtered types from being spawned at all,
    // which is cheaper and cleaner than filtering them during rendering.
    // This 7-arg overload (ParticleEffect + 6 doubles) is what commands and
    // most game logic go through (e.g. command-generated block particles).
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void onAddParticle(ParticleEffect effect, double x, double y, double z,
                               double velocityX, double velocityY, double velocityZ,
                               CallbackInfoReturnable<Particle> cir) {
        if (Configs.Disable.DISABLE_PARTICLES.getBooleanValue() &&
                FilterRules.isParticleFiltered(effect.getType())) {
            cir.cancel();
        }
    }

    // Every particle eventually goes through this 1-arg addParticle(Particle)
    // when it is queued for rendering. The Particle base class has no way to
    // look up its ParticleType, but break particles are BlockDustParticle
    // instances which map to the "minecraft:block" particle type, so they are
    // checked by that type here. This catches particles that are constructed
    // directly and bypass the 7-arg overload above (block break particles).
    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddParticle(Particle particle, CallbackInfo ci) {
        if (!Configs.Disable.DISABLE_PARTICLES.getBooleanValue()) {
            return;
        }
        if (particle instanceof BlockDustParticle) {
            if (FilterRules.isParticleFiltered(ParticleTypes.BLOCK)) {
                ci.cancel();
            }
        }
    }
}
