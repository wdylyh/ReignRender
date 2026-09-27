package com.wdylyh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.item.ItemRenderState;

@Mixin(ItemRenderState.class)
public interface ItemRenderStateAccessor {

    @Accessor("layers")
    ItemRenderState.LayerRenderState[] getLayers();

    @Accessor("layerCount")
    int getLayerCount();
}
