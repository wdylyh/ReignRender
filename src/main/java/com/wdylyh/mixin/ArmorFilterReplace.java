package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.ReplacementEngine;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public class ArmorFilterReplace {

    // Coordinates for the coordinate aware armor replacement: the render
    // state (which carries the entity position) is only visible to the
    // renderArmor HEAD injection, while the ModifyVariable below only sees the
    // stack. Both handlers run at HEAD in this mixin, so the injection stores
    // the position and the ModifyVariable reads it back on the same call.
    @Unique
    private static final ThreadLocal<double[]> ARMOR_POS = new ThreadLocal<double[]>() {
        @Override
        protected double[] initialValue() {
            return new double[3];
        }
    };

    /**
     * Filters armor per item: renderArmor receives the equipped stack and slot,
     * so the blacklist/whitelist can decide for each armor piece individually.
     */
    @Inject(method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void filterArmor(MatrixStack m, OrderedRenderCommandQueue q, ItemStack stk,
                             EquipmentSlot slot, int light, BipedEntityRenderState st, CallbackInfo ci) {
        double[] pos = ARMOR_POS.get();
        pos[0] = st.x;
        pos[1] = st.y;
        pos[2] = st.z;
        boolean kd = FilterEngine.revealDown();
        if (RenderConfig.Toggles.DISABLE_ARMOR.getBooleanValue() && FilterEngine.isArmorFiltered(stk, kd)) {
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
        if (!ReplacementEngine.isReplaceEnabled() || stk == null || stk.isEmpty()) {
            return stk;
        }
        // The reveal hotkey skips the replacement (shows the original armor).
        if (FilterEngine.isReplaceBlocked(FilterEngine.TYPE_ARMOR)) {
            return stk;
        }
        Item srcI = stk.getItem();
        double[] pos = ARMOR_POS.get();
        String tId = ReplacementEngine.getReplacementArmorAt(FilterEngine.getItemId(srcI), pos[0], pos[1], pos[2]);
        if (tId == null) {
            tId = ReplacementEngine.getReplacementArmor(FilterEngine.getItemId(srcI));
        }
        if (tId == null) {
            return stk;
        }
        Item t = ReplacementEngine.getItemTarget(tId);
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