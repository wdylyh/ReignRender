package com.wdylyh.mixin;

import com.wdylyh.config.Cfg;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 云层控制：当 "Disable Clouds" 开关打开时取消云层渲染；同时可限制云层的
 * 渲染距离（CAMERA_RENDER_DISTANCE 之外通过缩小云层单元半径实现）。
 */
@Mixin(CloudRenderer.class)
public class Cloud {

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void renderClouds(int rd, CloudRenderMode mode, float ch,
                              Vec3d cp, long td, float tp,
                              CallbackInfo ci) {
        if (Cfg.Off.DISABLE_CLOUDS.getBooleanValue()) {
            ci.cancel();
        }
    }

    /**
     * 云层渲染距离限制：renderClouds 的第一个 int 参数为云层网格的渲染距离，
     * 将其裁剪到 CLOUD_RENDER_DISTANCE 的取值（-1 时不做限制）。
     */
    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), index = 1, argsOnly = true)
    private int limitCloudDist(int rd) {
        int max = Cfg.G.CLOUD_RENDER_DISTANCE.getIntegerValue();
        return max >= 0 ? Math.min(rd, max) : rd;
    }
}