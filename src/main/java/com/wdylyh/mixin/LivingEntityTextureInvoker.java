package com.wdylyh.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker of {@link LivingEntityRenderer#getTexture}, so the region
 * texture replace mixin can resolve the vanilla texture of the render state.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityTextureInvoker {

    @Invoker("getTexture")
    Identifier invokeGetTexture(LivingEntityRenderState state);
}
