package com.wdylyh.mixin;

import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
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
 * <p>Each {@code "source=target"} rule in {@code Cfg.F.REPLACE_ITEM_ENTITIES}
 * (e.g. {@code "minecraft:porkchop=minecraft:slime_ball"}) makes the source
 * item's drops render as the target item.</p>
 */
@Mixin(ItemEntityRenderer.class)
public class ItemEntM {

    private static final Logger RLOGGER = LoggerFactory.getLogger(ItemEntM.class);

    // One-off log per source-trigger so a frame flood does not spam the console.
    private static final Set<String> mapped = new HashSet<>();

    @Redirect(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/entity/ItemEntity;getStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack getStack(ItemEntity ent) {
        ItemStack stack = ent.getStack();

        if (!Rpl.isReplaceEnabled() || stack.isEmpty()) {
            return stack;
        }
        // The reveal hotkey skips the replacement (shows the original drop),
        // following the same mode logic as the filters.
        if (FR.isReplaceBlocked(FR.TYPE_ENTITIES)) {
            return stack;
        }

        String src = FR.getItemId(stack.getItem());
        if (src == null) {
            return stack;
        }

        String tId = Rpl.getReplacementItem(src);
        if (tId == null) {
            return stack;
        }

        Item target = Rpl.getItemTarget(tId);
        if (target == null || target == stack.getItem()) {
            return stack;
        }

        if (mapped.add(src)) {
            RLOGGER.info("[ReignRender] item entity replace: {} -> {}", src, tId);
        }

        // Keep the original stack's count (and other properties such as the
        // custom name if any) by copying, then swap the item. Rendering only
        // reads the item, so the freshly made stack carries the same amount.
        return new ItemStack(target, stack.getCount());
    }
}
