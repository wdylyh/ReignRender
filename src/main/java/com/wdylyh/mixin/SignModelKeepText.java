package com.wdylyh.mixin;

import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.RenderConfig;
import net.minecraft.block.WoodType;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.block.entity.AbstractSignBlockEntityRenderer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignBlockEntityRenderer.class)
public class SignModelKeepText {

    /**
     * Skips the sign model (the wooden board) while keeping the text: only
     * when every block entity is hidden (filter mode OFF) and "Keep Sign
     * Text" is enabled. With a black/whitelist the filter in
     * BlockEntityRenderManagerMixin already hides the signs that should not
     * be visible.
     * <p>
     * The conditions are evaluated directly at this injection instead of
     * being stashed into a ThreadLocal flag by HEAD/RETURN injectors on
     * render(): they are plain config reads with no per-state dependency,
     * and a flag set at HEAD but never reset at RETURN (the target method
     * throwing) would keep skipping the wooden board of every later sign
     * for the rest of the session.
     * <p>
     * The reveal hotkey behavior is checked first: in any temporary state
     * (release mode with the key held = bypass, enable mode with the key
     * up = filters inactive) the block entity filter is suspended, so the
     * sign must render completely (board AND text). In enable mode with the
     * key held (force-hide) this injection is never reached because
     * BlockEntityFilterReplace already cancelled the whole block entity.
     */
    @Inject(method = "renderSign(Lnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/block/WoodType;Lnet/minecraft/client/model/Model$SinglePartModel;Lnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V",
            at = @At("HEAD"), cancellable = true)
    private void onSign(MatrixStack m, int lt, WoodType wt,
                        Model.SinglePartModel mdl,
                        ModelCommandRenderer.CrumblingOverlayCommand co,
                        OrderedRenderCommandQueue q, CallbackInfo ci) {
        if (FilterEngine.hotkey_Behavior(FilterEngine.TYPE_BLOCK_ENTITIES,
                FilterEngine.revealDown()) != FilterEngine.HOTKEY_BEHAVIOR_NORMAL) {
            return;
        }
        if (RenderConfig.Toggles.DISABLE_BLOCK_ENTITIES.getBooleanValue()
                && RenderConfig.General.KEEP_SIGN_TEXT.getBooleanValue()
                && RenderConfig.Filters.BLOCK_ENTITY_MODE.getOptionValue() == RenderConfig.Filters.MODE_OFF) {
            ci.cancel();
        }
    }
}
