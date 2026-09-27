package com.wdylyh.api;

import net.minecraft.particle.ParticleType;
import net.minecraft.util.math.Vec3d;

/**
 * Accessor for the pick-a-particle functionality injected into
 * {@link net.minecraft.client.particle.ParticleManager} by
 * {@link com.wdylyh.mixin.ParticleFilter}.
 *
 * Minecraft has no ray-cast for particles, so the mixin walks every live
 * particle and finds the one closest to the given ray. The returned type is
 * matched back from the particle instance that was recorded when it was
 * spawned.
 *
 * <p>This interface intentionally lives outside the {@code com.wdylyh.mixin}
 * package: the mixin system forbids direct references to classes inside a
 * defined mixin package that are not themselves mixins.
 */
public interface ParticlePickAccessor {

    /**
     * Finds the {@link ParticleType} of the live particle closest to the ray
     * starting at {@code o} and going along the normalized vector {@code d}.
     *
     * @param mp maximum perpendicular distance (blocks) of a particle from the
     *           ray for it to be considered
     * @param mr maximum distance along the ray (blocks) for a particle to be
     *           considered
     * @return the result with the particle type and its distance along the ray,
     *         or null when no particle is near the ray
     */
    PickRes pick(Vec3d o, Vec3d d, double mp, double mr);

    /**
     * A particle found by {@link #pick(Vec3d, Vec3d, double, double)}.
     *
     * @param type     the particle type
     * @param distance the distance along the ray ({@code t >= 0})
     */
    record PickRes(ParticleType<?> type, double distance) {
    }
}