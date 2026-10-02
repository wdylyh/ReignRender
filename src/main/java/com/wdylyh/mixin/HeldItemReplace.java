package com.wdylyh.mixin;

import com.wdylyh.client.RegionFaceItemPos;
import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.ReplacementEngine;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Held item (third person hand) render replacement.
 *
 * <p>{@code ArmedEntityRenderState.updateRenderState} reads both hand stacks
 * through {@code LivingEntity.getStackInArm(Arm)}; each returned stack feeds
 * the {@code ItemRenderState} bake AND the state's item fields, so redirecting
 * those two calls is a single clean injection point: the replacement item is
 * baked and rendered exactly like the original would be, only the model
 * changes. The swing animation reads the main hand stack separately (see
 * {@code getMainHandStack}), so the swing animation keeps following the
 * original item's swing component.</p>
 *
 * <p>Each {@code "source=target"} rule in {@code RenderConfig.Filters.REPLACE_HELD_ITEMS}
 * (e.g. {@code "minecraft:iron_sword=minecraft:diamond_sword"}) makes the
 * source item held by an entity render as the target item.</p>
 */
@Mixin(ArmedEntityRenderState.class)
public class HeldItemReplace {

    private static final Logger REPLACEMENT_LOGGER = LoggerFactory.getLogger(HeldItemReplace.class);

    // One-off log per source item so a frame flood does not spam the console.
    private static final Set<String> mapped = new HashSet<>();

    // Region face-mod position context: the region item model wrapper runs
    // inside ItemModel.update (no entity access), so publish the holder's
    // position around the whole state update (the ItemRenderState bake happens
    // after getStackInArm returns, so clearing inside the redirect is too early).
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/item/ItemModelManager;F)V",
            at = @At("HEAD"))
    private static void rfPosHead(LivingEntity ent, ArmedEntityRenderState st,
                                  net.minecraft.client.item.ItemModelManager mm, float td, CallbackInfo ci) {
        RegionFaceItemPos.set(ent.getX(), ent.getY(), ent.getZ());
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/item/ItemModelManager;F)V",
            at = @At("RETURN"))
    private static void rfPosReturn(LivingEntity ent, ArmedEntityRenderState st,
                                    net.minecraft.client.item.ItemModelManager mm, float td, CallbackInfo ci) {
        RegionFaceItemPos.clear();
    }

    @Redirect(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/item/ItemModelManager;F)V",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/entity/LivingEntity;getStackInArm(Lnet/minecraft/util/Arm;)Lnet/minecraft/item/ItemStack;"))
    private static ItemStack getStackInArm(LivingEntity ent, Arm arm) {
        ItemStack stack = ent.getStackInArm(arm);

        // No master-switch gate: the coordinate rules below are gated by the
        // coordinate replace toggle (global switch OFF), the global fallback
        // checks the master switch internally.
        if (stack.isEmpty() || !ReplacementEngine.anyReplaceActive()) {
            return stack;
        }
        // The reveal hotkey skips the replacement (shows the original item),
        // following the same mode logic as the filters.
        if (FilterEngine.isReplaceBlocked(FilterEngine.TYPE_HELD_ITEMS)) {
            return stack;
        }

        String src = FilterEngine.getItemId(stack.getItem());
        if (src == null) {
            return stack;
        }

        // Coordinate aware rules win over the global list; the holder's
        // position decides.
        String tId = ReplacementEngine.getReplacementHeldItemAt(src, ent.getX(), ent.getY(), ent.getZ(), FilterEngine.TYPE_HELD_ITEMS);
        if (tId == null) {
            tId = ReplacementEngine.getReplacementHeldItem(src);
        }
        if (tId == null) {
            return stack;
        }

        Item target = ReplacementEngine.getItemTarget(tId);
        if (target == null || target == stack.getItem()) {
            return stack;
        }

        if (mapped.add(src)) {
            REPLACEMENT_LOGGER.info("[ReignRender] held item replace: {} -> {}", src, tId);
        }

        // Keep the original stack's count (and other properties such as the
        // custom name if any) by copying, then swap the item.
        return new ItemStack(target, stack.getCount());
    }
}
