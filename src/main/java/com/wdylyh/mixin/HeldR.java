package com.wdylyh.mixin;

import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemFeatureRenderer.class)
public class HeldR {

    /**
     * Hides the item rendered in a mob's hand while the "disable held items"
     * toggle is on. The reveal hotkey temporarily shows them again; in
     * force-hide mode the hotkey hides them regardless of the toggle.
     */
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/EntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void onHeld(MatrixStack m, OrderedRenderCommandQueue q, int light,
                        EntityRenderState st, float td, float asp, CallbackInfo ci) {
        boolean kd = FR.revealDown();
        // 合并后的行为码只求值一次，替代原来 isRevealHeld + isFilterForced 各读一次模式。
        int bhv = FR.bhv(FR.TYPE_HELD_ITEMS, kd);
        if (bhv == FR.HOTKEY_BEHAVIOR_FORCE
                || (Cfg.Off.DISABLE_HELD_ITEMS.getBooleanValue()
                && bhv != FR.HOTKEY_BEHAVIOR_BYPASS)) {
            ci.cancel();
        }
    }
}