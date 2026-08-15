package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import com.wdylyh.config.FilterRules;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public class ArmorFeatureRendererMixin {

    /**
     * Filters armor per item: renderArmor receives the equipped stack and slot,
     * so the blacklist/whitelist can decide for each armor piece individually.
     */
    @Inject(method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void onRenderArmor(MatrixStack matrices, OrderedRenderCommandQueue queue, ItemStack stack,
                               EquipmentSlot slot, int light, BipedEntityRenderState state, CallbackInfo ci) {
        if (Configs.Disable.DISABLE_ARMOR.getBooleanValue() && FilterRules.isArmorFiltered(stack)) {
            ci.cancel();
        }
    }
}
