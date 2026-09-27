package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.state.WeatherRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 天气控制：当 "Disable WeatherDisable" 开关打开时，取消降水（雨/雪）的渲染，
 * 同时取消由天气产生的粒子与音效，避免"无雨滴却下雨声"的割裂感。
 */
@Mixin(WeatherRendering.class)
public class WeatherDisable {

    @Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true)
    private void renderPrecipitation(VertexConsumerProvider pv, Vec3d p,
                                     WeatherRenderState wrs, CallbackInfo ci) {
        if (RenderConfig.Toggles.DISABLE_WEATHER.getBooleanValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "addParticlesAndSound", at = @At("HEAD"), cancellable = true)
    private void addParticlesAndSound(ClientWorld w, Camera cam, int cx,
                                      ParticlesMode mode, int cz, CallbackInfo ci) {
        if (RenderConfig.Toggles.DISABLE_WEATHER.getBooleanValue()) {
            ci.cancel();
        }
    }
}