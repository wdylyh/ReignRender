package com.wdylyh.mixin;

import com.wdylyh.api.PickAcc;
import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleRenderer;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@Mixin(ParticleManager.class)
public abstract class ParticleM implements PickAcc {

    @Shadow
    private Map<ParticleTextureSheet, ParticleRenderer<?>> particles;

    /**
     * Particle instance -> ParticleType mapping used by the pick tool.
     * Minecraft has no way to look a particle's type up from its instance, so
     * the mapping is recorded when the particle is spawned. Weak keys let dead
     * particles be collected automatically; the ParticleType value never
     * references the Particle key, so no retention cycle exists.
     */
    @Unique
    private static final Map<Particle, ParticleType<?>> PICS = new WeakHashMap<>();

    // Prevents particles of filtered types from being spawned at all,
    // which is cheaper and cleaner than filtering them during rendering.
    // This 7-arg overload (ParticleEffect + 6 doubles) is what commands and
    // most game logic go through (e.g. command-generated block particles).
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void onAddP(ParticleEffect fx, double x, double y, double z,
                        double vx, double vy, double vz,
                        CallbackInfoReturnable<Particle> cir) {
        // Fast path: with both the filter toggle and the count cap off there
        // is nothing to decide, so the native GLFW keyboard query (the most
        // expensive step of the filter chain) is skipped entirely.
        if (!Cfg.Off.DISABLE_PARTICLES.getBooleanValue()
                && Cfg.G.MAX_PARTICLES.getIntegerValue() < 0) {
            return;
        }
        boolean kd = FR.revealDown();
        // 单个行为码供过滤与数量上限共用，避免每个粒子求值两次。
        int bh = FR.bhv(FR.TYPE_PARTICLES, kd);
        if (Cfg.Off.DISABLE_PARTICLES.getBooleanValue() &&
                FR.isParticleFiltered(fx.getType(), kd, bh)) {
            cir.cancel();
            return;
        }
        if (overLimit(bh)) {
            cir.cancel();
        }
    }

    // Records the freshly built particle together with its type so the pick
    // tool can later match a particle instance back to a ParticleType.
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("RETURN"))
    private void onAddPR(ParticleEffect fx, double x, double y, double z,
                         double vx, double vy, double vz,
                         CallbackInfoReturnable<Particle> cir) {
        Particle p = cir.getReturnValue();

        if (p != null) {
            PICS.put(p, fx.getType());
        }
    }

    // Universal particle replacement: while the master replace switch is on and
    // the source particle id has a rule, the ParticleEffect is swapped for the
    // target particle before the particle is spawned. Only SimpleParticleType
    // targets are supported, because those are their own ParticleEffect without
    // extra spawn parameters (dust, item, block particles need parameters the
    // source spawn call does not provide).
    @ModifyVariable(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), argsOnly = true, index = 1)
    private ParticleEffect rplFX(ParticleEffect fx) {
        return rplP(fx);
    }

    // Emitter particles (campfires, enchanting tables, portals, lava, ...) are
    // created by addEmitter as EmitterParticle instances. The emitter re-emits
    // the stored effect every tick, and each re-emit falls through
    // ClientWorld.addParticleClient -> ClientWorld.addParticle ->
    // addParticle(ParticleEffect, 6 doubles) above, so the effect is replaced by
    // rplFX at the single choke point. No dedicated emitter injection is needed.

    @Unique
    private static final Logger RLOGGER = LoggerFactory.getLogger(ParticleM.class);

    // One-time diagnostic per replaced source id: distinguishes a missing rule
    // from an unsupported target type without spamming the log every particle.
    @Unique
    private static final Set<String> rpLogged = new HashSet<>();

    // One-time preamble diagnostic: prints the master replace switch and the
    // particle rule count on the first particle that is spawned. This makes the
    // "no rule matched" case distinguishable from a hard failure, because this
    // line appears even when every lookup misses.
    @Unique
    private static boolean rpInit;

    @Unique
    private static ParticleEffect rplP(ParticleEffect fx) {
        if (fx == null) {
            return fx;
        }
        if (!rpInit) {
            rpInit = true;
            RLOGGER.info("[ReignRender] particle replace ready: enabled={}, rules={}", Rpl.isReplaceEnabled(), Rpl.particleRuleCount());
        }
        if (!Rpl.isReplaceEnabled() || fx == null) {
            return fx;
        }
        // The reveal hotkey skips the replacement (shows the original particle).
        if (FR.isReplaceBlocked(FR.TYPE_PARTICLES)) {
            return fx;
        }
        String src = FR.getParticleId(fx.getType());
        String tid = Rpl.getReplacementParticle(src);
        if (tid == null) {
            return fx;
        }
        ParticleType<?> pt = Rpl.getParticleTypeTarget(tid);
        if (pt instanceof SimpleParticleType sp) {
            if (rpLogged.add(src)) {
                RLOGGER.info("[ReignRender] particle replace: {} -> {} (SimpleParticleType)", src, tid);
            }
            return sp;
        }
        if (rpLogged.add(src)) {
            RLOGGER.warn("[ReignRender] particle replace: {} -> {} skipped: target is not a SimpleParticleType ({})",
                    src, tid, pt);
        }
        return fx;
    }

    // Every particle eventually goes through this 1-arg addParticle(Particle)
    // when it is queued for rendering. The Particle base class has no way to
    // look up its ParticleType, but break particles are BlockDustParticle
    // instances which map to the "minecraft:block" particle type, so they are
    // checked by that type here. This catches particles that are constructed
    // directly and bypass the 7-arg overload above (block break particles).
    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddP(Particle p, CallbackInfo ci) {
        // Block break particles never go through the 7-arg overload, so their
        // type cannot be recorded there; map them explicitly for the pick tool.
        if (p instanceof BlockDustParticle) {
            PICS.put(p, ParticleTypes.BLOCK);
        }

        // Block break / digging particles are constructed directly (new
        // BlockDustParticle) and enter ParticleManager through this 1-arg
        // overload, bypassing the 7-arg addParticle above. The universal
        // replacement therefore has to be applied here: when a rule matches
        // "minecraft:block" the particle is cancelled and a fresh particle of
        // the target SimpleParticleType is spawned through the 7-arg overload
        // at the same position (the dust's random sprinkle velocity is not
        // worth replicating, zero is fine). The spawned particle takes the
        // 7-arg path, so the filter / count-cap logic still applies to it.
        if (p instanceof BlockDustParticle && Rpl.isReplaceEnabled()) {
            String src = FR.getParticleId(ParticleTypes.BLOCK);
            if (FR.isReplaceBlocked(FR.TYPE_PARTICLES)) {
                return;
            }
            String tid = Rpl.getReplacementParticle(src);
            if (tid != null) {
                ParticleType<?> pt = Rpl.getParticleTypeTarget(tid);
                if (pt instanceof SimpleParticleType sp) {
                    if (rpLogged.add(src)) {
                        RLOGGER.info("[ReignRender] particle replace: {} -> {} (SimpleParticleType)", src, tid);
                    }
                    Vec3d c = p.getBoundingBox().getCenter();
                    ci.cancel();
                    ((ParticleManager) (Object) this).addParticle(sp, c.x, c.y, c.z, 0.0, 0.0, 0.0);
                    return;
                }
                if (rpLogged.add(src)) {
                    RLOGGER.warn("[ReignRender] particle replace: {} -> {} skipped: target is not a SimpleParticleType ({})",
                            src, tid, pt);
                }
            }
        }

        // Same fast path as the 7-arg overload: skip the GLFW query when
        // neither the filter toggle nor the count cap is active.
        if (!Cfg.Off.DISABLE_PARTICLES.getBooleanValue()
                && Cfg.G.MAX_PARTICLES.getIntegerValue() < 0) {
            return;
        }
        boolean kd = FR.revealDown();
        // 单个行为码供过滤与数量上限共用，避免每个粒子求值两次。
        int bh = FR.bhv(FR.TYPE_PARTICLES, kd);
        if (Cfg.Off.DISABLE_PARTICLES.getBooleanValue()
                && p instanceof BlockDustParticle
                && FR.isParticleFiltered(ParticleTypes.BLOCK, kd, bh)) {
            ci.cancel();
            return;
        }
        if (overLimit(bh)) {
            ci.cancel();
        }
    }

    // Particle count cap. The current total is the sum of every particle sheet
    // renderer's size (ParticleManager.particles). While reveal is held the
    // limit is bypassed just like the other per-frame filters; in force-hide
    // mode every particle is cancelled. The reveal behavior is evaluated once
    // by the caller and passed down, and the count exits as soon as the cap is
    // reached instead of summing every renderer.
    @Unique
    private boolean overLimit(int bh) {
        if (bh == FR.HOTKEY_BEHAVIOR_FORCE) {
            return true;
        }
        if (bh == FR.HOTKEY_BEHAVIOR_BYPASS) {
            return false;
        }
        int max = Cfg.G.MAX_PARTICLES.getIntegerValue();
        if (max < 0) {
            return false;
        }
        int cnt = 0;
        for (ParticleRenderer<?> r : particles.values()) {
            cnt += r.size();
            if (cnt >= max) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Unique
    public PickRes pick(Vec3d o, Vec3d d, double mp, double mr) {
        Particle b = null;
        double bd = mp * mp;
        double bt = 0.0;

        for (ParticleRenderer<?> r : particles.values()) {
            for (Particle p : r.getParticles()) {
                if (p == null || !p.isAlive()) {
                    continue;
                }

                // Distance from the particle center to the ray. Particles that
                // sit behind the camera origin or beyond the ray range are
                // skipped.
                Vec3d tp = p.getBoundingBox().getCenter().subtract(o);
                double t = tp.dotProduct(d);

                if (t < 0.0 || t > mr) {
                    continue;
                }

                double dsq = tp.squaredDistanceTo(d.multiply(t));

                if (dsq < bd) {
                    bd = dsq;
                    b = p;
                    bt = t;
                }
            }
        }

        if (b == null) {
            return null;
        }

        ParticleType<?> pt = PICS.get(b);

        // Safety net for particles whose type was never recorded
        if (pt == null && b instanceof BlockDustParticle) {
            pt = ParticleTypes.BLOCK;
        }

        return new PickRes(pt, bt);
    }
}