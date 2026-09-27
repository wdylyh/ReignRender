package com.wdylyh.mixin;

import com.wdylyh.client.RegionFaceItemPos;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Region face-mod position context for the first-person held item.
 *
 * <p>{@code HeldItemRenderer.renderItem} is the common entry point of the
 * first-person hand rendering; the region item model wrapper
 * ({@code FaceModItemModels.RegionShadowItemModel}) runs inside
 * {@code ItemModel.update} without any entity access, so the carrier's
 * position is published here around the render call.</p>
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemFacePos {

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"))
    private void rfPosHead(LivingEntity ent, net.minecraft.item.ItemStack stack,
                           net.minecraft.item.ItemDisplayContext ctx,
                           net.minecraft.client.util.math.MatrixStack matrices,
                           net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                           int light, CallbackInfo ci) {
        RegionFaceItemPos.set(ent.getX(), ent.getY(), ent.getZ());
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("RETURN"))
    private void rfPosReturn(LivingEntity ent, net.minecraft.item.ItemStack stack,
                             net.minecraft.item.ItemDisplayContext ctx,
                             net.minecraft.client.util.math.MatrixStack matrices,
                             net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                             int light, CallbackInfo ci) {
        RegionFaceItemPos.clear();
    }
}
