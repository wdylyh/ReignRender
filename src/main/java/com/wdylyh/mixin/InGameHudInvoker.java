package com.wdylyh.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessors for the private InGameHud element renderers, used by
 * {@link HudElementReplace} to render the replacement element in place of the
 * source element (the source method was cancelled, the target one is invoked
 * with the original call arguments).
 */
@Mixin(InGameHud.class)
public interface InGameHudInvoker {

    @Invoker("renderBossBarHud")
    void reignRender$bossbar(DrawContext ctx, RenderTickCounter cnt);

    @Invoker("renderSubtitlesHud")
    void reignRender$subtitles(DrawContext ctx, boolean compact);

    @Invoker("renderChat")
    void reignRender$chat(DrawContext ctx, RenderTickCounter cnt);

    @Invoker("renderStatusEffectOverlay")
    void reignRender$statusEffects(DrawContext ctx, RenderTickCounter cnt);

    @Invoker("renderCrosshair")
    void reignRender$crosshair(DrawContext ctx, RenderTickCounter cnt);

    @Invoker("renderHotbar")
    void reignRender$hotbar(DrawContext ctx, RenderTickCounter cnt);

    @Invoker("renderOverlayMessage")
    void reignRender$overlayMessage(DrawContext ctx, RenderTickCounter cnt);

    @Invoker("renderTitleAndSubtitle")
    void reignRender$title(DrawContext ctx, RenderTickCounter cnt);

    @Invoker("renderScoreboardSidebar")
    void reignRender$scoreboard(DrawContext ctx, RenderTickCounter cnt);

    @Invoker("renderPlayerList")
    void reignRender$playerList(DrawContext ctx, RenderTickCounter cnt);

    @Invoker("renderDemoTimer")
    void reignRender$demoTimer(DrawContext ctx, RenderTickCounter cnt);

    @Invoker("renderHeldItemTooltip")
    void reignRender$heldItemTooltip(DrawContext ctx);

    @Invoker("renderNauseaOverlay")
    void reignRender$nausea(DrawContext ctx, float distortion);

    @Invoker("renderVignetteOverlay")
    void reignRender$vignette(DrawContext ctx, Entity entity);
}
