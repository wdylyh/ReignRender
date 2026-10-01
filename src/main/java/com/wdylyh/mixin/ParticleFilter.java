package com.wdylyh.mixin;

import com.wdylyh.api.ParticlePickAccessor;
import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.ConditionEngine;
import com.wdylyh.config.CoordinateFilter;
import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.ReplacementEngine;
import net.minecraft.client.MinecraftClient;
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
public abstract class ParticleFilter implements ParticlePickAccessor {

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
    private static final Map<Particle, ParticleType<?>> particleTypeByInstance = new WeakHashMap<>();

    // Prevents particles of filtered types from being spawned at all,
    // which is cheaper and cleaner than filtering them during rendering.
    // This 7-arg overload (ParticleEffect + 6 doubles) is what commands and
    // most game logic go through (e.g. command-generated block particles).
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void onSpawnParticle7Args(ParticleEffect fx, double x, double y, double z,
                        double vx, double vy, double vz,
                        CallbackInfoReturnable<Particle> cir) {
        // Hand the spawn position to the replacement handler (replaceParticleFX / replaceParticle,
        // declared after this one and therefore injected after it) so the
        // coordinate aware replacement rules can match the spawn point.
        double[] loc = particleSpawnLocation.get();
        loc[0] = x;
        loc[1] = y;
        loc[2] = z;
        // Per-id count/distance limits from the condition system are
        // independent of both the particle filter toggle and the coordinate
        // filter, so they are checked before the fast path below (which would
        // otherwise skip them entirely). The count limit shares the per-frame
        // budget with the entities; the distance limit compares the spawn
        // position against the camera.
        String limitId = FilterEngine.getParticleId(fx.getType());
        if (ConditionEngine.isCountExceeded(limitId, x, y, z)) {
            cir.cancel();
            return;
        }
        Vec3d cp = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
        if (ConditionEngine.isDistanceExceeded(limitId, x, y, z, cp.x, cp.y, cp.z)) {
            cir.cancel();
            return;
        }
        // Fast path: with both the filter toggle and the coordinate filter off
        // there is nothing to decide, so the native GLFW keyboard query (the
        // most expensive step of the filter chain) is skipped entirely.
        if (!RenderConfig.Toggles.DISABLE_PARTICLES.getBooleanValue()
                && !CoordinateFilter.active()) {
            return;
        }
        boolean kd = FilterEngine.revealDown();
        int bh = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_PARTICLES, kd);
        if (RenderConfig.Toggles.DISABLE_PARTICLES.getBooleanValue() &&
                FilterEngine.isParticleFiltered(fx.getType(), kd, bh)) {
            cir.cancel();
            return;
        }
        // Coordinate filter: independent of the particle filter master toggle,
        // using the spawn position (x/y/z are the absolute coordinates).
        if (bh == FilterEngine.HOTKEY_BEHAVIOR_NORMAL &&
                CoordinateFilter.isParticleHidden(x, y, z, fx.getType())) {
            cir.cancel();
            return;
        }
    }

    // Records the freshly built particle together with its type so the pick
    // tool can later match a particle instance back to a ParticleType.
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("RETURN"))
    private void onSpawnParticleRecordType(ParticleEffect fx, double x, double y, double z,
                         double vx, double vy, double vz,
                         CallbackInfoReturnable<Particle> cir) {
        Particle p = cir.getReturnValue();

        if (p != null) {
            particleTypeByInstance.put(p, fx.getType());
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
    private ParticleEffect replaceParticleFX(ParticleEffect fx) {
        return replaceParticle(fx);
    }

    // Emitter particles (campfires, enchanting tables, portals, lava, ...) are
    // created by addEmitter as EmitterParticle instances. The emitter re-emits
    // the stored effect every tick, and each re-emit falls through
    // ClientWorld.addParticleClient -> ClientWorld.addParticle ->
    // addParticle(ParticleEffect, 6 doubles) above, so the effect is replaced by
    // replaceParticleFX at the single choke point. No dedicated emitter injection is needed.

    @Unique
    private static final Logger REPLACEMENT_LOGGER = LoggerFactory.getLogger(ParticleFilter.class);

    // One-time diagnostic per replaced source id: distinguishes a missing rule
    // from an unsupported target type without spamming the log every particle.
    @Unique
    private static final Set<String> replacementLoggedSources = new HashSet<>();

    // Spawn position handed from the 7-arg HEAD handler (which has the
    // doubles) to replaceParticleFX/replaceParticle (which only sees the ParticleEffect parameter).
    // Reused across calls instead of allocating a double[] per particle.
    @Unique
    private static final ThreadLocal<double[]> particleSpawnLocation = ThreadLocal.withInitial(() -> new double[3]);

    // One-time preamble diagnostic: prints the master replace switch and the
    // particle rule count on the first particle that is spawned. This makes the
    // "no rule matched" case distinguishable from a hard failure, because this
    // line appears even when every lookup misses.
    @Unique
    private static boolean replacementInitialized;

    @Unique
    private static ParticleEffect replaceParticle(ParticleEffect fx) {
        if (fx == null) {
            return fx;
        }
        if (!replacementInitialized) {
            replacementInitialized = true;
            REPLACEMENT_LOGGER.info("[ReignRender] particle replace ready: enabled={}, rules={}", ReplacementEngine.isReplaceEnabled(), ReplacementEngine.particleRuleCount());
        }
        // No master-switch gate: the coordinate rules below are gated by the
        // coordinate replace toggle (global switch OFF), the global fallback
        // checks the master switch internally.
        if (fx == null) {
            return fx;
        }
        // The reveal hotkey skips the replacement (shows the original particle).
        if (FilterEngine.isReplaceBlocked(FilterEngine.TYPE_PARTICLES)) {
            return fx;
        }
        String src = FilterEngine.getParticleId(fx.getType());
        // Coordinate aware rules win over the global list; the spawn position
        // was stashed by the 7-arg HEAD handler above.
        double[] loc = particleSpawnLocation.get();
        String tid = ReplacementEngine.getReplacementParticleAt(src, loc[0], loc[1], loc[2], FilterEngine.TYPE_PARTICLES);
        if (tid == null) {
            tid = ReplacementEngine.getReplacementParticle(src);
        }
        if (tid == null) {
            return fx;
        }
        ParticleType<?> pt = ReplacementEngine.getParticleTypeTarget(tid);
        if (pt instanceof SimpleParticleType sp) {
            if (replacementLoggedSources.add(src)) {
                REPLACEMENT_LOGGER.info("[ReignRender] particle replace: {} -> {} (SimpleParticleType)", src, tid);
            }
            return sp;
        }
        if (replacementLoggedSources.add(src)) {
            REPLACEMENT_LOGGER.warn("[ReignRender] particle replace: {} -> {} skipped: target is not a SimpleParticleType ({})",
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
    private void onSpawnParticle1Arg(Particle p, CallbackInfo ci) {
        // Block break particles never go through the 7-arg overload, so their
        // type cannot be recorded there; map them explicitly for the pick tool.
        if (p instanceof BlockDustParticle) {
            particleTypeByInstance.put(p, ParticleTypes.BLOCK);
        }

        // Block break / digging particles are constructed directly (new
        // BlockDustParticle) and enter ParticleManager through this 1-arg
        // overload, bypassing the 7-arg addParticle above. The universal
        // replacement therefore has to be applied here: when a rule matches
        // "minecraft:block" the particle is cancelled and a fresh particle of
        // the target SimpleParticleType is spawned through the 7-arg overload
        // at the same position (the dust's random sprinkle velocity is not
        // worth replicating, zero is fine). The spawned particle takes the
        // 7-arg path, so the filter logic still applies to it.
        // No master-switch gate: the coordinate rules below are gated by the
        // coordinate replace toggle (global switch OFF), the global fallback
        // checks the master switch internally.
        if (p instanceof BlockDustParticle) {
            String src = FilterEngine.getParticleId(ParticleTypes.BLOCK);
            if (FilterEngine.isReplaceBlocked(FilterEngine.TYPE_PARTICLES)) {
                return;
            }
            Vec3d c = p.getBoundingBox().getCenter();
            // Coordinate aware rules win over the global list.
            String tid = ReplacementEngine.getReplacementParticleAt(src, c.x, c.y, c.z, FilterEngine.TYPE_PARTICLES);
            if (tid == null) {
                tid = ReplacementEngine.getReplacementParticle(src);
            }
            if (tid != null) {
                ParticleType<?> pt = ReplacementEngine.getParticleTypeTarget(tid);
                if (pt instanceof SimpleParticleType sp) {
                    if (replacementLoggedSources.add(src)) {
                        REPLACEMENT_LOGGER.info("[ReignRender] particle replace: {} -> {} (SimpleParticleType)", src, tid);
                    }
                    ci.cancel();
                    ((ParticleManager) (Object) this).addParticle(sp, c.x, c.y, c.z, 0.0, 0.0, 0.0);
                    return;
                }
                if (replacementLoggedSources.add(src)) {
                    REPLACEMENT_LOGGER.warn("[ReignRender] particle replace: {} -> {} skipped: target is not a SimpleParticleType ({})",
                            src, tid, pt);
                }
            }
        }

        // Per-id count/distance limits from the condition system, checked
        // before the fast path below for the same independence reason as in
        // the 7-arg overload. Only the block break particles have a known type
        // here ("minecraft:block"), directly constructed particles of unknown
        // types are skipped by both limits.
        if (p instanceof BlockDustParticle) {
            String limitId = FilterEngine.getParticleId(ParticleTypes.BLOCK);
            Vec3d c = p.getBoundingBox().getCenter();
            if (ConditionEngine.isCountExceeded(limitId, c.x, c.y, c.z)) {
                ci.cancel();
                return;
            }
            Vec3d cp = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
            if (ConditionEngine.isDistanceExceeded(limitId, c.x, c.y, c.z, cp.x, cp.y, cp.z)) {
                ci.cancel();
                return;
            }
        }
        // Same fast path as the 7-arg overload: skip the GLFW query when
        // neither the filter toggle nor the coordinate filter is active.
        if (!RenderConfig.Toggles.DISABLE_PARTICLES.getBooleanValue()
                && !CoordinateFilter.active()) {
            return;
        }
        boolean kd = FilterEngine.revealDown();
        int bh = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_PARTICLES, kd);
        // Coordinate filter on the particles that enter through this 1-arg
        // overload (block break particles and any directly constructed ones).
        // Block dust particles map to "minecraft:block"; particles with an
        // unknown type can only be hidden in the OFF ("关闭") mode, which
        // hides the whole region regardless of any ids.
        if (bh == FilterEngine.HOTKEY_BEHAVIOR_NORMAL) {
            Vec3d c = p.getBoundingBox().getCenter();
            if (p instanceof BlockDustParticle
                    ? CoordinateFilter.isParticleHidden(c.x, c.y, c.z, ParticleTypes.BLOCK)
                    : CoordinateFilter.isRegionHiddenAt(c.x, c.y, c.z)) {
                ci.cancel();
                return;
            }
        }
        if (RenderConfig.Toggles.DISABLE_PARTICLES.getBooleanValue()
                && p instanceof BlockDustParticle
                && FilterEngine.isParticleFiltered(ParticleTypes.BLOCK, kd, bh)) {
            ci.cancel();
            return;
        }
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

        ParticleType<?> pt = particleTypeByInstance.get(b);

        // Safety net for particles whose type was never recorded
        if (pt == null && b instanceof BlockDustParticle) {
            pt = ParticleTypes.BLOCK;
        }

        return new PickRes(pt, bt);
    }
}