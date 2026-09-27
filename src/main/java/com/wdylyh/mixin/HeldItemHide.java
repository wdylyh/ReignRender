package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.FilterEngine;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemFeatureRenderer.class)
public class HeldItemHide {

    /**
     * Hides the item rendered in a mob's hand while the "disable held items"
     * toggle is on. The reveal hotkey temporarily shows them again; in
     * force-hide mode the hotkey hides them regardless of the toggle.
     */
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/EntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void onHeld(MatrixStack m, OrderedRenderCommandQueue q, int light,
                        EntityRenderState st, float td, float asp, CallbackInfo ci) {
        boolean kd = FilterEngine.revealDown();
        // 合并后的行为码只求值一次，替代原来 isRevealHeld + isFilterForced 各读一次模式。
        int bhv = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_HELD_ITEMS, kd);
        if (bhv == FilterEngine.HOTKEY_BEHAVIOR_FORCE
                || (RenderConfig.Toggles.DISABLE_HELD_ITEMS.getBooleanValue()
                && bhv == FilterEngine.HOTKEY_BEHAVIOR_NORMAL)) {
            ci.cancel();
        }
    }
}