package com.wdylyh.mixin;

import com.wdylyh.client.gui.ToastRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the custom toast notifications (e.g. picker results) on the HUD.
 * They are drawn unconditionally — the filter toggles never hide them — and
 * are only skipped while the F3 debug screen is visible.
 */
@Mixin(InGameHud.class)
public class ToastOverlay {

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("RETURN"))
    private void onHudRender(DrawContext ctx, RenderTickCounter tc, CallbackInfo ci) {
        if (!MinecraftClient.getInstance().inGameHud.getDebugHud().shouldShowDebugHud()) {
            ToastRenderer.render(ctx);
        }
    }
}
