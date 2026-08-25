package com.wdylyh.mixin;

import com.wdylyh.client.gui.Toast;
import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
import fi.dy.masa.malilib.config.IHotkeyTogglable;
import fi.dy.masa.malilib.config.options.ConfigOptionValues;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.config.value.BaseOptionListConfigValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a compact status box in the top-left corner listing every filter that
 * is currently active, so the player can see at a glance what is hidden. It is
 * a plain translucent rectangle (no texture) with small text lines, plus a
 * highlighted line while the reveal hotkey is held so the temporary
 * bypass/force-hide state is visible too.
 * <p>
 * Enabled by the "Show Filter Status" option. While the F3 debug screen is
 * visible the overlay is skipped so it never overlaps the debug text.
 */
@Mixin(InGameHud.class)
public class IGH {

    private static final int CT = 0xFFFFFFFF;
    private static final int CL = 0xFFAAAAAA;
    private static final int CR = 0xFF55FF55;
    private static final int CFH = 0xFFFF5555;
    private static final int LH = 9;
    private static final int PAD = 4;
    private static final int MINW = 80;

    // Fully static overlay texts. Text.translatable resolves the translation
    // lazily at render time (through the translation storage), so the cached
    // instances stay correct across language / resource-pack reloads and no
    // per-frame Text object is allocated for them.
    @Unique
    private static final Text TT = Text.translatable("reignrender.hud.title");
    @Unique
    private static final Text NT = Text.translatable("reignrender.hud.none");
    @Unique
    private static final Text RT = Text.translatable("reignrender.hud.revealActive");
    @Unique
    private static final Text FHT = Text.translatable("reignrender.hud.forceHideActive");
    @Unique
    private static final Text REP_T = Text.translatable("reignrender.hud.replaceTitle");

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("RETURN"))
    private void onHud(DrawContext ctx, RenderTickCounter tc, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // Custom notifications (picker results) are drawn unconditionally, so
        // they still show up when every filter toggle is off (an empty status
        // box previously returned early and skipped them). They are only
        // hidden while the F3 debug screen is visible.
        if (!mc.inGameHud.getDebugHud().shouldShowDebugHud()) {
            Toast.render(ctx);
        }

        if (!Cfg.G.SHOW_HUD_STATE.getBooleanValue()) {
            return;
        }

        // Pre-size: with every master toggle on the box can hold ~17 lines.
        List<SL> ls = new ArrayList<>(20);

        // Master toggles that also carry a blacklist/whitelist mode.
        addTgl(ls, Cfg.Off.DISABLE_ENTITIES,
                Cfg.F.ENTITY_MODE, Cfg.F.FILTERED_ENTITIES.getStrings().size());
        addTgl(ls, Cfg.Off.DISABLE_BLOCKS,
                Cfg.F.BLOCK_MODE, Cfg.F.FILTERED_BLOCKS.getStrings().size());
        addTgl(ls, Cfg.Off.DISABLE_FLUIDS,
                Cfg.F.FLUID_MODE, Cfg.F.FILTERED_FLUIDS.getStrings().size());
        addTgl(ls, Cfg.Off.DISABLE_BLOCK_ENTITIES,
                Cfg.F.BLOCK_ENTITY_MODE, Cfg.F.FILTERED_BLOCK_ENTITIES.getStrings().size());
        addTgl(ls, Cfg.Off.DISABLE_PARTICLES,
                Cfg.F.PARTICLE_MODE, Cfg.F.FILTERED_PARTICLES.getStrings().size());
        addTgl(ls, Cfg.Off.DISABLE_ARMOR,
                Cfg.F.ARMOR_MODE, Cfg.F.FILTERED_ARMOR.getStrings().size());
        addTgl(ls, Cfg.Off.DISABLE_FOG,
                Cfg.F.FOG_MODE, Cfg.F.FILTERED_FOGS.getStrings().size());

        // Name based lists: the master toggle + mode, no list size.
        addTgl(ls, Cfg.Off.DISABLE_NAME_TAGS, Cfg.F.NAME_TAG_MODE, -1);
        addTgl(ls, Cfg.Off.HIDE_OTHER_PLAYERS, Cfg.F.PLAYER_MODE, -1);

        // Simple toggles without a list.
        addS(ls, Cfg.Off.DISABLE_FALLING_BLOCKS);
        addS(ls, Cfg.Off.HIDE_SELF);
        addS(ls, Cfg.Off.DISABLE_HELD_ITEMS);
        addS(ls, Cfg.Off.DISABLE_ELYTRA);

        // Replace-system status: while the master replace switch is on, list
        // every replacement category that has active rules, so it is visible
        // that the shown content is being replaced. If the reveal hotkey is
        // held for a category its replacement is temporarily disabled (the
        // original is shown again), so that category is marked as overridden.
        if (Cfg.G.REPLACE_ENABLED.getBooleanValue()) {
            int before = ls.size();
            addRep(ls, Cfg.F.REPLACE_PARTICLES, FR.TYPE_PARTICLES);
            addRep(ls, Cfg.F.REPLACE_BLOCKS, FR.TYPE_BLOCKS);
            addRep(ls, Cfg.F.REPLACE_ENTITIES, FR.TYPE_ENTITIES);
            addRep(ls, Cfg.F.REPLACE_FOGS, FR.TYPE_FOG);
            addRep(ls, Cfg.F.REPLACE_ARMOR, FR.TYPE_ARMOR);
            addRep(ls, Cfg.F.REPLACE_NAME_TAGS, FR.TYPE_NAME_TAGS);
            addRep(ls, Cfg.F.REPLACE_PLAYER_NAMES, FR.TYPE_NAME_TAGS);
            addRep(ls, Cfg.F.REPLACE_FLUIDS, FR.TYPE_FLUIDS);
            addRep(ls, Cfg.F.REPLACE_BLOCK_ENTITIES, FR.TYPE_BLOCK_ENTITIES);
            addRep(ls, Cfg.F.REPLACE_FALLING_BLOCKS, FR.TYPE_FALLING_BLOCKS);
            addRep(ls, Cfg.F.REPLACE_ITEM_ENTITIES, FR.TYPE_ENTITIES);
            if (ls.size() > before) {
                ls.add(before, new SL(CT, REP_T));
            }
        }

        // While the reveal hotkey is held its effect is worth showing at the
        // top of the box: reveal mode bypasses the filters, force-hide mode
        // hides everything regardless of the individual toggles.
        if (FR.revealDown()
                && !Cfg.G.REVEAL_AFFECTED_TYPES.getStrings().isEmpty()) {
            boolean rel = Cfg.G.REVEAL_HOTKEY_MODE.getOptionValue()
                    == Cfg.G.HOTKEY_MODE_RELEASE;
            ls.add(0, new SL(rel ? CR : CFH, rel ? RT : FHT));
        }

        drawBox(ctx, mc.textRenderer, mc.getWindow().getScaledWidth(), ls);
    }

    private static void drawBox(DrawContext ctx, TextRenderer tr,
                                int sw, List<SL> ls) {
        Text t = TT;

        int mt = tr.getWidth(t);
        for (SL l : ls) {
            mt = Math.max(mt, tr.getWidth(l.tx()));
        }

        int w = Math.max(MINW, mt + PAD * 2);
        w = Math.min(w, sw - 8);
        int h = PAD + LH + (ls.isEmpty() ? LH : ls.size() * LH) + PAD;

        int x = 4;
        int y = 2;

        // Plain translucent rectangle with a thin border, no texture.
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF555555);
        ctx.fill(x, y, x + w, y + h, 0x90000000);

        int ty = y + PAD;

        ctx.drawText(tr, t, x + PAD, ty, CT, true);
        ty += LH;

        if (ls.isEmpty()) {
            ctx.drawText(tr, NT,
                    x + PAD, ty, CL, true);
            return;
        }

        for (SL l : ls) {
            ctx.drawText(tr, l.tx(), x + PAD, ty, l.cl(), true);
            ty += LH;
        }
    }

    private static void addTgl(List<SL> ls, IHotkeyTogglable tg,
                               ConfigOptionValues<BaseOptionListConfigValue> mo, int sz) {
        if (!tg.getBooleanValue()) {
            return;
        }

        BaseOptionListConfigValue opt = mo.getOptionValue();
        Text n = Text.translatable("reignrender.config.disable.prettyName." + tg.getName());

        if (opt == Cfg.F.MODE_OFF) {
            ls.add(new SL(CL, Text.translatable("reignrender.hud.allHidden", n)));
        } else if (sz >= 0) {
            ls.add(new SL(CL, Text.translatable("reignrender.hud.modeCount",
                    n,
                    Text.translatable("reignrender.config.filter.mode." + opt.getName()),
                    sz)));
        } else {
            ls.add(new SL(CL, Text.translatable("reignrender.hud.mode",
                    n,
                    Text.translatable("reignrender.config.filter.mode." + opt.getName()))));
        }
    }

    private static void addS(List<SL> ls, IHotkeyTogglable tg) {
        if (tg.getBooleanValue()) {
            ls.add(new SL(CL,
                    Text.translatable("reignrender.config.disable.prettyName." + tg.getName())));
        }
    }

    private static void addRep(List<SL> ls, ConfigStringList rc, String type) {
        int n = rc.getStrings().size();
        if (n == 0) {
            return;
        }
        Text nm = Text.translatable("reignrender.config.filter.prettyName." + rc.getName());
        // While the reveal override is active the category's replacement is
        // temporarily disabled (the original is shown again), so mark the line.
        if (FR.isReplaceBlocked(type)) {
            ls.add(new SL(CFH, Text.translatable("reignrender.hud.replaceOff", nm)));
        } else {
            ls.add(new SL(CR, Text.translatable("reignrender.hud.replaceLine", nm, n)));
        }
    }

    private record SL(int cl, Text tx) {}
}