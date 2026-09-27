package com.wdylyh.client;

import net.minecraft.particle.ParticleType;

/**
 * Spawning context for the region particle face modification: the spawn type
 * and position of the current {@code ParticleManager.addParticle} call, handed
 * from {@code ParticleFaceMod} to {@code BillboardParticleFaceMod} through
 * ThreadLocals. The particle factory (which sets the sprite) runs inside the
 * addParticle call, so the values are set at HEAD and cleared at RETURN; a
 * value can never leak into a later call even when the factory throws.
 *
 * <p>Not a mixin class: mixin classes must not contain non-private static
 * methods, so the ThreadLocals and their accessors live here.</p>
 */
public final class ParticleFaceContext {

    private static final ThreadLocal<ParticleType<?>> SPAWN_TYPE = new ThreadLocal<>();
    private static final ThreadLocal<double[]> SPAWN_POS = new ThreadLocal<>();

    private ParticleFaceContext() {
    }

    public static void set(ParticleType<?> type, double x, double y, double z) {
        SPAWN_TYPE.set(type);

        double[] p = new double[] { x, y, z };
        SPAWN_POS.set(p);
    }

    public static void clear() {
        SPAWN_TYPE.remove();
        SPAWN_POS.remove();
    }

    /** The type of the particle currently being spawned, or null outside addParticle. */
    public static ParticleType<?> spawnType() {
        return SPAWN_TYPE.get();
    }

    /** The spawn position (x, y, z) of the particle currently being spawned, or null. */
    public static double[] spawnPos() {
        return SPAWN_POS.get();
    }
}
