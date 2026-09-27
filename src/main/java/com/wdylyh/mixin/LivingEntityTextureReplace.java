package com.wdylyh.mixin;

import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.RegionFaceEngine;
import com.wdylyh.config.RegionFaceIndex;
import com.wdylyh.config.RegionFacePacks;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Region face modification for living entity textures ("区域面修改：实体").
 *
 * <p>{@code LivingEntityRenderer.getRenderLayer} resolves the entity texture
 * through a single {@code getTexture(state)} call, so redirecting that call
 * covers every living entity in one place. The render state carries the
 * entity type and position, so the region lookup needs no extra state:</p>
 *
 * <p>When the region face-mod is active, the entity stands inside a matched
 * region and its id is attached to the entry (or the entry has no ids), the
 * vanilla texture path is mapped to its region edit
 * ({@code minecraft:textures/entity/zombie/zombie.png} ->
 * {@code reignrender:rface/entity/zombie/zombie.png}) and returned when the
 * region index lists the edit. Textures without a region edit are returned
 * unchanged, so unedited entities never change.</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityTextureReplace {

    // getTexture is resolved through the invoker interface (an @Shadow
    // abstract method cannot be declared in a non-abstract mixin class).
    @Redirect(method = "getRenderLayer",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getTexture(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)Lnet/minecraft/util/Identifier;"))
    private Identifier regionTexture(LivingEntityRenderer instance, LivingEntityRenderState state) {
        Identifier tex = ((LivingEntityTextureInvoker) (Object) this).invokeGetTexture(state);

        if (tex == null || state == null || !RegionFacePacks.active()) {
            return tex;
        }

        String eid = FilterEngine.getEntityId(state.entityType);

        if (eid == null || !RegionFaceEngine.isEntityFaceAt(eid, state.x, state.y, state.z)) {
            return tex;
        }

        // Map the vanilla texture path onto its region edit path and swap it
        // only when the index records an edit for exactly this texture.
        String savePath = regionSavePath(tex);

        if (savePath != null && RegionFaceIndex.hasOverride(RegionFaceIndex.ENTITIES, eid, savePath)) {
            return Identifier.of(savePath);
        }

        return tex;
    }

    /** "minecraft:textures/entity/zombie/zombie.png" -> "reignrender:textures/rface/entity/zombie/zombie.png", or null. */
    private static String regionSavePath(Identifier tex) {
        String p = tex.getPath();

        if (!tex.getNamespace().equals("minecraft") || !p.startsWith("textures/entity/")) {
            return null;
        }

        return "reignrender:textures/rface/entity/" + p.substring("textures/entity/".length());
    }
}
