package com.wdylyh.mixin;

import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.RenderConfig;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the first-person held item while the "disable held items" toggle is
 * on. {@link HeldItemHide} only covers the third-person feature renderer
 * ({@code HeldItemFeatureRenderer}); the local player's hand is drawn by
 * {@code HeldItemRenderer.renderFirstPersonItem}, which needs its own cancel.
 */
@Mixin(HeldItemRenderer.class)
public class FirstPersonHeldItemHide {

    @Inject(method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"), cancellable = true)
    private void onFirstPersonHeld(net.minecraft.client.network.AbstractClientPlayerEntity player, float td, float pitch,
                                   net.minecraft.util.Hand hand, float swing,
                                   net.minecraft.item.ItemStack stack, float equip,
                                   MatrixStack m, OrderedRenderCommandQueue q, int light, CallbackInfo ci) {
        boolean kd = FilterEngine.revealDown();
        int bhv = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_HELD_ITEMS, kd);
        if (bhv == FilterEngine.HOTKEY_BEHAVIOR_FORCE
                || (RenderConfig.Toggles.DISABLE_HELD_ITEMS.getBooleanValue()
                && bhv == FilterEngine.HOTKEY_BEHAVIOR_NORMAL)) {
            ci.cancel();
        }
    }
}
