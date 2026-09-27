package com.wdylyh.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;

@Mixin(ItemRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {

    @Mutable
    @Accessor("quads")
    void setQuads(List<BakedQuad> quads);
}
