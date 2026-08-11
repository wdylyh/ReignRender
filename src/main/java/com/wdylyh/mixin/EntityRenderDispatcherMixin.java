package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderManager.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderEntity(EntityRenderState renderState, CameraRenderState cameraState,
                                double offsetX, double offsetY, double offsetZ,
                                MatrixStack matrices, OrderedRenderCommandQueue queue,
                                CallbackInfo ci) {
        if (Configs.Disable.DISABLE_ENTITIES.getBooleanValue()) {
            ci.cancel();
        }
    }
}