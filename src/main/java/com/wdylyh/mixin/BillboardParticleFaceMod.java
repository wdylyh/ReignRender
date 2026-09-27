package com.wdylyh.mixin;

import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.RegionFaceEngine;
import com.wdylyh.config.RegionFaceIndex;
import com.wdylyh.config.RegionFacePacks;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.texture.Sprite;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Region face modification for billboard particle textures ("区域面修改：粒子").
 *
 * <p>A billboard particle's texture lives in its {@code sprite} field, set by
 * the particle factory (inside {@code ParticleManager.addParticle}, whose
 * spawn type / position {@link ParticleFaceMod} hands over through
 * ThreadLocals) and later by {@code updateSprite} for animated particles. The
 * injection below replaces the sprite with the region edit
 * ({@code minecraft:particle/generic_0} -> sprite
 * {@code reignrender:rface/particle/generic_0} from the particles atlas) when
 * the spawn happened inside a matched region and the index lists the edit for
 * this particle id and sprite. Outside the regions the vanilla sprite stays,
 * so the edit never leaks into the rest of the world.</p>
 *
 * <p>{@code setSprite} only assigns the sprite field, so the swap writes the
 * shadowed field directly instead of re-entering the method.</p>
 */
@Mixin(BillboardParticle.class)
public class BillboardParticleFaceMod {

    @Shadow
    protected Sprite sprite;

    @Inject(method = "setSprite", at = @At("HEAD"), cancellable = true)
    private void rfRegionSprite(Sprite vanilla, CallbackInfo ci) {
        if (vanilla == null || !RegionFacePacks.active()) {
            return;
        }

        ParticleType<?> type = com.wdylyh.client.ParticleFaceContext.spawnType();
        double[] pos = com.wdylyh.client.ParticleFaceContext.spawnPos();

        if (type == null || pos == null) {
            return;
        }

        String pid = FilterEngine.getParticleId(type);

        if (pid == null || !RegionFaceEngine.isParticleFaceAt(pid, pos[0], pos[1], pos[2])) {
            return;
        }

        Identifier sid = vanilla.getContents().getId();
        String savePath = "reignrender:textures/rface/" + sid.getPath() + ".png";

        if (!RegionFaceIndex.hasOverride(RegionFaceIndex.PARTICLES, pid, savePath)) {
            return;
        }

        Sprite region = RegionFacePacks.particleSprite(savePath);

        if (region != null) {
            this.sprite = region;
            ci.cancel();
        }
    }
}
