package com.wdylyh.mixin;

import com.wdylyh.SignState;
import com.wdylyh.config.Cfg;
import net.minecraft.block.WoodType;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.block.entity.state.SignBlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.render.block.entity.AbstractSignBlockEntityRenderer")
public class SignR {

    // The three conditions below are read per sign render; when the block-entity
    // hiding is off, the ThreadLocal flag is never touched at all.
    private static boolean skipEn() {
        return Cfg.Off.DISABLE_BLOCK_ENTITIES.getBooleanValue()
                && Cfg.G.KEEP_SIGN_TEXT.getBooleanValue()
                && Cfg.F.BLOCK_ENTITY_MODE.getOptionValue() == Cfg.F.MODE_OFF;
    }

    @Inject(method = "render(Lnet/minecraft/client/render/block/entity/state/SignBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"))
    private void onHead(SignBlockEntityRenderState rs, MatrixStack m,
                        OrderedRenderCommandQueue q, CameraRenderState cs,
                        CallbackInfo ci) {
        // Only skip the sign model when everything is hidden (filter mode OFF);
        // with a black/whitelist the filter in BlockEntityRenderManagerMixin
        // already hides the signs that should not be visible.
        if (skipEn()) {
            SignState.setSkip(true);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/block/entity/state/SignBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("RETURN"))
    private void onRet(SignBlockEntityRenderState rs, MatrixStack m,
                       OrderedRenderCommandQueue q, CameraRenderState cs,
                       CallbackInfo ci) {
        // Reset only when the flag may have been set by the HEAD injector.
        if (skipEn()) {
            SignState.setSkip(false);
        }
    }

    /**
     * Skip the sign model rendering (the wooden board) while keeping the text.
     */
    @Inject(method = "renderSign(Lnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/block/WoodType;Lnet/minecraft/client/model/Model$SinglePartModel;Lnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V",
            at = @At("HEAD"), cancellable = true)
    private void onSign(MatrixStack m, int lt, WoodType wt,
                        Model.SinglePartModel mdl,
                        ModelCommandRenderer.CrumblingOverlayCommand co,
                        OrderedRenderCommandQueue q, CallbackInfo ci) {
        if (SignState.skip()) {
            ci.cancel();
        }
    }
}