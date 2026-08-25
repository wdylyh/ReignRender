package com.wdylyh.mixin;

import com.wdylyh.config.FR;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD 元素独立控制：当 "Disable HUD Elements" 开关打开时，逐个隐藏配置在
 * HIDDEN_HUD_ELEMENTS 列表中的 HUD 元素（Boss条、快捷栏、聊天、药水图标、
 * 准星、标题、计分板、玩家列表、传送门扭曲、恶心叠加等）。元素 id 见
 * {@link #hudHidden} 支持的 id 集合。
 */
@Mixin(InGameHud.class)
public class HudM {

    /**
     * Returns true when the given HUD element id should be hidden: the master
     * "Disable HUD Elements" toggle must be on AND the id must appear in the
     * HIDDEN_HUD_ELEMENTS list (empty list hides nothing).
     */
    private static boolean hudHidden(String id) {
        return FR.isHudElementHidden(id);
    }

    @Inject(method = "renderBossBarHud", at = @At("HEAD"), cancellable = true)
    private void renderBossBarHud(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        if (hudHidden("bossbar")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSubtitlesHud", at = @At("HEAD"), cancellable = true)
    private void renderSubtitlesHud(DrawContext ctx, boolean f, CallbackInfo ci) {
        if (hudHidden("subtitles")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true)
    private void renderChat(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        if (hudHidden("chat")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void renderStatusEffectOverlay(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        if (hudHidden("statusEffects")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void renderCrosshair(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        if (hudHidden("crosshair")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void renderHotbar(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        if (hudHidden("hotbar")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void renderOverlayMessage(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        if (hudHidden("overlayMessage")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
    private void renderTitleAndSubtitle(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        if (hudHidden("title")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"), cancellable = true)
    private void renderScoreboardSidebar(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        if (hudHidden("scoreboard")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPlayerList", at = @At("HEAD"), cancellable = true)
    private void renderPlayerList(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        if (hudHidden("playerList")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderDemoTimer", at = @At("HEAD"), cancellable = true)
    private void renderDemoTimer(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        if (hudHidden("demoTimer")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void renderHeldItemTooltip(DrawContext ctx, CallbackInfo ci) {
        if (hudHidden("heldItemTooltip")) {
            ci.cancel();
        }
    }

    /**
     * 传送门屏幕扭曲：列入 HUD 元素列表中的 "portal" 时隐藏。
     */
    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void renderPortalOverlay(DrawContext ctx, float td, CallbackInfo ci) {
        if (hudHidden("portal")) {
            ci.cancel();
        }
    }

    /**
     * 传送门引发的恶心（绿色蜿蜒）效果叠加：列入 HUD 元素列表中的 "nausea" 时隐藏。
     */
    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void renderNauseaOverlay(DrawContext ctx, float td, CallbackInfo ci) {
        if (hudHidden("nausea")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void renderVignetteOverlay(DrawContext ctx, Entity e, CallbackInfo ci) {
        if (hudHidden("vignette")) {
            ci.cancel();
        }
    }

    /**
     * 南瓜头全屏效果叠加：加入迷雾禁止渲染体系。当佩戴南瓜头时会以
     * {@code pumpkinblur} 纹理调用 renderOverlay，这里在渲染前判断迷雾过滤
     * 规则（OFF/BLACKLIST/WHITELIST + Reveal 绕过）是否应隐藏。
     */
    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void renderOverlay(DrawContext ctx, Identifier id, float op, CallbackInfo ci) {
        if (id != null
                && id.getPath().endsWith("pumpkinblur")
                && FR.isPumpkinOverlayFiltered()) {
            ci.cancel();
        }
    }
}