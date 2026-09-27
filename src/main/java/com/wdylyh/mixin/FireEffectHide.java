package com.wdylyh.mixin;

import com.wdylyh.config.FilterEngine;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.FireCommandRenderer;
import net.minecraft.client.texture.AtlasManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 燃烧火焰动画控制（HUD 名单）：当 "fire" 列入 HUD 隐藏元素列表（同时
 * "Disable HUD Elements" 总开关开启）时，取消实体着火时环绕实体的火焰
 * 动画的渲染。
 */
@Mixin(FireCommandRenderer.class)
public class FireEffectHide {

    // 目标类存在私有 render 重载，必须用完整描述符指向公开的三参方法，
    // 否则 Mixin 在运行时可能解析到私有重载导致 InvalidInjectionException。
    @Inject(method = "render(Lnet/minecraft/client/render/command/BatchingRenderCommandQueue;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/texture/AtlasManager;)V",
            at = @At("HEAD"), cancellable = true)
    private void renderFire(BatchingRenderCommandQueue q,
                            VertexConsumerProvider.Immediate imm,
                            AtlasManager am, CallbackInfo ci) {
        if (FilterEngine.isHudElementHidden("fire")) {
            ci.cancel();
        }
    }
}