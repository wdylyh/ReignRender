package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.FilterEngine;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraFeatureRenderer.class)
public class ElytraHide {

    /**
     * Hides the elytra rendered on players and mobs while the "disable elytra"
     * toggle is on. The reveal hotkey temporarily shows them again; in
     * force-hide mode the hotkey hides them regardless of the toggle.
     */
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/EntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void onElytra(MatrixStack m, OrderedRenderCommandQueue q, int light,
                          EntityRenderState st, float td, float asp, CallbackInfo ci) {
        boolean kd = FilterEngine.revealDown();
        // 合并后的行为码只求值一次，替代原来 isRevealHeld + isFilterForced 各读一次模式。
        int bhv = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_ELYTRA, kd);
        if (bhv == FilterEngine.HOTKEY_BEHAVIOR_FORCE
                || (RenderConfig.Toggles.DISABLE_ELYTRA.getBooleanValue()
                && bhv == FilterEngine.HOTKEY_BEHAVIOR_NORMAL)) {
            ci.cancel();
        }
    }
}