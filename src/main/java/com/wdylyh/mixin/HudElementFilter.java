package com.wdylyh.mixin;

import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.ReplacementEngine;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * HUD 元素独立控制：当 "Disable HUD Elements" 开关打开时，逐个隐藏配置在
 * HIDDEN_HUD_ELEMENTS 列表中的 HUD 元素（Boss条、快捷栏、聊天、药水图标、
 * 准星、标题、计分板、玩家列表、恶心叠加等）。
 *
 * <p>替换渲染：当替换系统开启且 REPLACE_HUD_ELEMENTS 中有 "源元素=目标元素"
 * 规则时，源元素被取消，改为以原始调用参数渲染目标元素（见
 * {@link #hudReplace} 与 {@link InGameHudInvoker}）。rendering 标志防止
 * 目标元素自身的注入回调再次进入替换逻辑（防循环）。</p>
 */
@Mixin(InGameHud.class)
public class HudElementFilter {

    /** Guards against re-entrant replacement while the target element renders. */
    private static boolean rendering;

    /**
     * Returns true when the given HUD element id should be hidden: the master
     * "Disable HUD Elements" toggle must be on AND the id must appear in the
     * HIDDEN_HUD_ELEMENTS list (empty list hides nothing).
     */
    private static boolean hudHidden(String id) {
        return FilterEngine.isHudElementHidden(id);
    }

    private InGameHudInvoker invoker() {
        return (InGameHudInvoker) this;
    }

    /**
     * Shared entry for every element injection: hides the element while the
     * filter asks for it, otherwise cancels the original method and renders
     * the replacement element when a rule matches. {@code dispatch} renders
     * the given target element id with the captured call arguments.
     */
    private void hudReplace(String id, CallbackInfo ci, Consumer<String> dispatch) {
        if (hudHidden(id)) {
            ci.cancel();
            return;
        }

        String tgt = ReplacementEngine.getReplacementHudElement(id);
        if (tgt == null || tgt.equals(id) || rendering) {
            return;
        }
        // The reveal hotkey skips the replacement, like every other category.
        if (FilterEngine.isReplaceBlocked(FilterEngine.TYPE_HUD_ELEMENTS)) {
            return;
        }

        rendering = true;
        try {
            dispatch.accept(tgt);
        } finally {
            rendering = false;
        }
        ci.cancel();
    }

    /**
     * Renders the given element id. Most elements share the
     * (DrawContext, RenderTickCounter) signature; the special ones reuse the
     * captured values of whichever handler triggered the replacement.
     */
    private void renderElement(String tgt, DrawContext ctx, RenderTickCounter cnt,
                               boolean compact, Entity vignetteEntity, float distortion) {
        switch (tgt) {
            case "bossbar" -> this.invoker().reignRender$bossbar(ctx, cnt);
            case "subtitles" -> this.invoker().reignRender$subtitles(ctx, compact);
            case "chat" -> this.invoker().reignRender$chat(ctx, cnt);
            case "statusEffects" -> this.invoker().reignRender$statusEffects(ctx, cnt);
            case "crosshair" -> this.invoker().reignRender$crosshair(ctx, cnt);
            case "hotbar" -> this.invoker().reignRender$hotbar(ctx, cnt);
            case "overlayMessage" -> this.invoker().reignRender$overlayMessage(ctx, cnt);
            case "title" -> this.invoker().reignRender$title(ctx, cnt);
            case "scoreboard" -> this.invoker().reignRender$scoreboard(ctx, cnt);
            case "playerList" -> this.invoker().reignRender$playerList(ctx, cnt);
            case "demoTimer" -> this.invoker().reignRender$demoTimer(ctx, cnt);
            case "heldItemTooltip" -> this.invoker().reignRender$heldItemTooltip(ctx);
            case "nausea" -> this.invoker().reignRender$nausea(ctx, distortion);
            case "vignette" -> this.invoker().reignRender$vignette(ctx, vignetteEntity);
        }
    }

    @Inject(method = "renderBossBarHud", at = @At("HEAD"), cancellable = true)
    private void renderBossBarHud(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        this.hudReplace("bossbar", ci, t -> this.renderElement(t, ctx, cnt, false, null, 1.0F));
    }

    @Inject(method = "renderSubtitlesHud", at = @At("HEAD"), cancellable = true)
    private void renderSubtitlesHud(DrawContext ctx, boolean f, CallbackInfo ci) {
        this.hudReplace("subtitles", ci, t -> this.renderElement(t, ctx, null, f, null, 1.0F));
    }

    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true)
    private void renderChat(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        this.hudReplace("chat", ci, t -> this.renderElement(t, ctx, cnt, false, null, 1.0F));
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void renderStatusEffectOverlay(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        this.hudReplace("statusEffects", ci, t -> this.renderElement(t, ctx, cnt, false, null, 1.0F));
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void renderCrosshair(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        this.hudReplace("crosshair", ci, t -> this.renderElement(t, ctx, cnt, false, null, 1.0F));
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void renderHotbar(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        this.hudReplace("hotbar", ci, t -> this.renderElement(t, ctx, cnt, false, null, 1.0F));
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void renderOverlayMessage(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        this.hudReplace("overlayMessage", ci, t -> this.renderElement(t, ctx, cnt, false, null, 1.0F));
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
    private void renderTitleAndSubtitle(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        this.hudReplace("title", ci, t -> this.renderElement(t, ctx, cnt, false, null, 1.0F));
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"), cancellable = true)
    private void renderScoreboardSidebar(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        this.hudReplace("scoreboard", ci, t -> this.renderElement(t, ctx, cnt, false, null, 1.0F));
    }

    @Inject(method = "renderPlayerList", at = @At("HEAD"), cancellable = true)
    private void renderPlayerList(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        this.hudReplace("playerList", ci, t -> this.renderElement(t, ctx, cnt, false, null, 1.0F));
    }

    @Inject(method = "renderDemoTimer", at = @At("HEAD"), cancellable = true)
    private void renderDemoTimer(DrawContext ctx, RenderTickCounter cnt, CallbackInfo ci) {
        this.hudReplace("demoTimer", ci, t -> this.renderElement(t, ctx, cnt, false, null, 1.0F));
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void renderHeldItemTooltip(DrawContext ctx, CallbackInfo ci) {
        this.hudReplace("heldItemTooltip", ci, t -> this.renderElement(t, ctx, null, false, null, 1.0F));
    }

    /**
     * 传送门引发的恶心（绿色蜿蜒）效果叠加：列入 HUD 元素列表中的 "nausea" 时隐藏。
     */
    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void renderNauseaOverlay(DrawContext ctx, float td, CallbackInfo ci) {
        this.hudReplace("nausea", ci, t -> this.renderElement(t, ctx, null, false, null, td));
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void renderVignetteOverlay(DrawContext ctx, Entity e, CallbackInfo ci) {
        this.hudReplace("vignette", ci, t -> this.renderElement(t, ctx, null, false, e, 1.0F));
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
                && FilterEngine.isPumpkinOverlayFiltered()) {
            ci.cancel();
        }
    }
}
