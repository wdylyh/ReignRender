package com.wdylyh.mixin;

import com.wdylyh.config.Cfg;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 第一人称屏幕底部火焰控制：着火时画面下方的火苗由
 * {@link InGameOverlayRenderer#renderOverlays} 内的 {@code client.player.isOnFire()}
 * 判定后调用 renderFireOverlay 绘制（与环绕实体的火焰动画是两条独立渲染路径）。
 * 这里重定向该判定，当 "Disable Fire Animation" 开关打开时返回 false，仅跳过
 * 火焰覆盖，方块卡头 / 水下覆盖不受影响。
 */
@Mixin(InGameOverlayRenderer.class)
public class FireOv {

    @Redirect(method = "renderOverlays",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/network/ClientPlayerEntity;isOnFire()Z"))
    private boolean skipFire(ClientPlayerEntity p) {
        if (Cfg.Off.DISABLE_FIRE_ANIMATION.getBooleanValue()) {
            return false;
        }
        return p.isOnFire();
    }
}