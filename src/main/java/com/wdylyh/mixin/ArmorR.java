package com.wdylyh.mixin;

import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public class ArmorR {

    /**
     * Filters armor per item: renderArmor receives the equipped stack and slot,
     * so the blacklist/whitelist can decide for each armor piece individually.
     */
    @Inject(method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void filterArmor(MatrixStack m, OrderedRenderCommandQueue q, ItemStack stk,
                             EquipmentSlot slot, int light, BipedEntityRenderState st, CallbackInfo ci) {
        boolean kd = FR.revealDown();
        if (Cfg.Off.DISABLE_ARMOR.getBooleanValue() && FR.isArmorFiltered(stk, kd)) {
            ci.cancel();
        }
    }

    // Universal armor replacement: while the master replace switch is on and
    // the source item id has a rule, the armor stack is swapped for the target
    // item's default stack before the armor model is rendered. The target item
    // is rendered with the source piece's transform, which is exactly what a
    // "one armor rendered as another" replacement should look like. Only the
    // source's visual components (enchantment glint, dye, trim and custom
    // name) are carried over, so the replacement is a pure rendering swap and
    // no gameplay data (damage, durability, attributes) is copied.
    @ModifyVariable(method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At("HEAD"), argsOnly = true, index = 3)
    private ItemStack repStack(ItemStack stk) {
        if (!Rpl.isReplaceEnabled() || stk == null || stk.isEmpty()) {
            return stk;
        }
        // The reveal hotkey skips the replacement (shows the original armor).
        if (FR.isReplaceBlocked(FR.TYPE_ARMOR)) {
            return stk;
        }
        Item srcI = stk.getItem();
        String tId = Rpl.getReplacementArmor(FR.getItemId(srcI));
        if (tId == null) {
            return stk;
        }
        Item t = Rpl.getItemTarget(tId);
        if (t == null || t == srcI) {
            return stk;
        }
        ItemStack tStk = t.getDefaultStack();
        copyVis(stk, tStk);
        return tStk;
    }

    @SuppressWarnings("unchecked")
    private static void copyVis(ItemStack src, ItemStack tgt) {
        for (ComponentType<?> t : new ComponentType[]{
                DataComponentTypes.ENCHANTMENTS,
                DataComponentTypes.DYED_COLOR,
                DataComponentTypes.TRIM,
                DataComponentTypes.CUSTOM_NAME}) {
            if (src.getComponents().contains(t)) {
                Object v = src.get((ComponentType) t);
                if (v != null) {
                    tgt.set((ComponentType) t, v);
                }
            }
        }
    }
}