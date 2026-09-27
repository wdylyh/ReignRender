package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 云层控制：当 "Disable Clouds" 开关打开时取消云层渲染。
 */
@Mixin(CloudRenderer.class)
public class CloudDisable {

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void renderClouds(int rd, CloudRenderMode mode, float ch,
                              Vec3d cp, long td, float tp,
                              CallbackInfo ci) {
        if (RenderConfig.Toggles.DISABLE_CLOUDS.getBooleanValue()) {
            ci.cancel();
        }
    }
}