package com.wdylyh.mixin;

import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.ReplacementEngine;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;
import java.util.Set;

/**
 * Item drop (ItemEntity) render replacement.
 *
 * <p>{@code ItemEntityRenderer.updateRenderState} builds the render state from
 * {@code ItemEntity.getStack()}. That stack is read exactly once per update
 * (the returned value is passed straight into {@code ItemEntityRenderState}
 * .update(...)), so redirecting its call is a clean single injection point:
 * the replacement stack keeps the same count, position and physics as the
 * original, only the rendered model changes.</p>
 *
 * <p>Each {@code "source=target"} rule in {@code RenderConfig.Filters.REPLACE_ITEM_ENTITIES}
 * (e.g. {@code "minecraft:porkchop=minecraft:slime_ball"}) makes the source
 * item's drops render as the target item.</p>
 */
@Mixin(ItemEntityRenderer.class)
public class ItemEntityReplace {

    private static final Logger REPLACEMENT_LOGGER = LoggerFactory.getLogger(ItemEntityReplace.class);

    // One-off log per source-trigger so a frame flood does not spam the console.
    private static final Set<String> mapped = new HashSet<>();

    // Region face-mod position context: the wrapped region item model runs
    // inside this call (the stack feeds the ItemRenderState bake) and needs the
    // drop's position for the region lookup. Set at HEAD, cleared at RETURN, so
    // a value can never leak into a later call.
    @org.spongepowered.asm.mixin.injection.Inject(
            method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At("HEAD"))
    private void rfPosHead(ItemEntity ent, net.minecraft.client.render.entity.state.ItemEntityRenderState st, float td,
                           org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.wdylyh.client.RegionFaceItemPos.set(ent.getX(), ent.getY(), ent.getZ());
    }

    @org.spongepowered.asm.mixin.injection.Inject(
            method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At("RETURN"))
    private void rfPosReturn(ItemEntity ent, net.minecraft.client.render.entity.state.ItemEntityRenderState st, float td,
                             org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.wdylyh.client.RegionFaceItemPos.clear();
    }

    @Redirect(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/entity/ItemEntity;getStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack getStack(ItemEntity ent) {
        ItemStack stack = ent.getStack();

        if (!ReplacementEngine.isReplaceEnabled() || stack.isEmpty()) {
            return stack;
        }
        // The reveal hotkey skips the replacement (shows the original drop),
        // following the same mode logic as the filters.
        if (FilterEngine.isReplaceBlocked(FilterEngine.TYPE_ITEM_ENTITIES)) {
            return stack;
        }

        String src = FilterEngine.getItemId(stack.getItem());
        if (src == null) {
            return stack;
        }

        // Coordinate aware rules win over the global list; the drop's current
        // position decides.
        String tId = ReplacementEngine.getReplacementItemAt(src, ent.getX(), ent.getY(), ent.getZ());
        if (tId == null) {
            tId = ReplacementEngine.getReplacementItem(src);
        }
        if (tId == null) {
            return stack;
        }

        Item target = ReplacementEngine.getItemTarget(tId);
        if (target == null || target == stack.getItem()) {
            return stack;
        }

        if (mapped.add(src)) {
            REPLACEMENT_LOGGER.info("[ReignRender] item entity replace: {} -> {}", src, tId);
        }

        // Keep the original stack's count (and other properties such as the
        // custom name if any) by copying, then swap the item. Rendering only
        // reads the item, so the freshly made stack carries the same amount.
        return new ItemStack(target, stack.getCount());
    }
}
