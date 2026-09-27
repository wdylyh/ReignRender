package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 天空天体控制：当 "Disable SkyDisable" 开关打开时，取消天空盒、天体（太阳/月亮）、
 * 星星以及末地天空的渲染。各渲染阶段均被独立拦截。
 */
@Mixin(SkyRendering.class)
public class SkyDisable {

    @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true)
    private void renderSkyDark(CallbackInfo ci) {
        if (RenderConfig.Toggles.DISABLE_SKY.getBooleanValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true)
    private void renderCelestialBodies(MatrixStack m, float td, float a,
                                       float b, MoonPhase mp,
                                       float si, float cInt, CallbackInfo ci) {
        if (RenderConfig.Toggles.DISABLE_SKY.getBooleanValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void renderEndSky(CallbackInfo ci) {
        if (RenderConfig.Toggles.DISABLE_SKY.getBooleanValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTopSky", at = @At("HEAD"), cancellable = true)
    private void renderTopSky(int rd, CallbackInfo ci) {
        // 空中俯视的天空穹顶，作为单色天空的一部分，随主开关一并关闭。
        if (RenderConfig.Toggles.DISABLE_SKY.getBooleanValue()) {
            ci.cancel();
        }
    }
}