package com.wdylyh.mixin;

import com.wdylyh.SignRenderStateHolder;
import com.wdylyh.config.Configs;
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
public class SignBlockEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/render/block/entity/state/SignBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"))
    private void onRenderHead(SignBlockEntityRenderState renderState, MatrixStack matrices,
                              OrderedRenderCommandQueue queue, CameraRenderState cameraState,
                              CallbackInfo ci) {
        // Only skip the sign model when everything is hidden (filter mode OFF);
        // with a black/whitelist the filter in BlockEntityRenderManagerMixin
        // already hides the signs that should not be visible.
        if (Configs.Disable.DISABLE_BLOCK_ENTITIES.getBooleanValue() &&
                Configs.Generic.KEEP_SIGN_TEXT.getBooleanValue() &&
                Configs.Filter.BLOCK_ENTITY_MODE.getOptionValue() == Configs.Filter.MODE_OFF) {
            SignRenderStateHolder.setSkipSignModel(true);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/block/entity/state/SignBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("RETURN"))
    private void onRenderReturn(SignBlockEntityRenderState renderState, MatrixStack matrices,
                                OrderedRenderCommandQueue queue, CameraRenderState cameraState,
                                CallbackInfo ci) {
        SignRenderStateHolder.setSkipSignModel(false);
    }

    /**
     * Skip the sign model rendering (the wooden board) while keeping the text.
     */
    @Inject(method = "renderSign(Lnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/block/WoodType;Lnet/minecraft/client/model/Model$SinglePartModel;Lnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V",
            at = @At("HEAD"), cancellable = true)
    private void onRenderSign(MatrixStack matrices, int light, WoodType woodType,
                              Model.SinglePartModel model,
                              ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay,
                              OrderedRenderCommandQueue queue, CallbackInfo ci) {
        if (SignRenderStateHolder.shouldSkipSignModel()) {
            ci.cancel();
        }
    }
}
