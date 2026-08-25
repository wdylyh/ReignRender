package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * Multi-select picker for the render categories the reveal hotkey can affect.
 *
 * Lists every category from {@link FR#ALL_TYPES} as a row. Each row
 * shows the category name above and a button below whose label reads
 * "enabled" when the category is in the
 * {@link Cfg.G.REVEAL_AFFECTED_TYPES} string list and "disabled"
 * otherwise; clicking the button toggles it. Only the enabled categories are
 * bypassed (release mode) or force-hidden (enable mode) while the reveal
 * hotkey is held. The toggle button is drawn green when enabled and red when
 * disabled.
 */
public class GTypePick extends GuiBase
{
    private static final int RH = 32;
    private static final int RW = 150;
    private static final int LH = 10;
    private static final int LX = 20;
    // Below the done button, which occupies y 26..46, so the rows never overlap it
    private static final int LY = 60;

    private final Map<String, ColorBtn> btns = new HashMap<>();
    private Set<String> sel = new HashSet<>();

    @Override
    public void initGui()
    {
        super.initGui();

        this.setParent(new GConfigs());
        this.setTitle(StringUtils.translate("reignrender.config.generic.prettyName.revealAffectedTypes"));
        this.sel = new HashSet<>(Cfg.G.REVEAL_AFFECTED_TYPES.getStrings());

        this.addButton(new ButtonGeneric(this.width - 10, 26, 120, true, "reignrender.gui.filter.done"),
                       new Done());

        this.rebuild();
    }

    private void rebuild()
    {
        this.btns.clear();

        int x = LX;
        int y = LY;
        int c = 0;

        for (String t : FR.ALL_TYPES)
        {
            String nm = StringUtils.translate("reignrender.config.generic.affectedType." + t);

            // Category name above the toggle button
            this.addLabel(x, y, RW, LH, 0xFFFFFFFF, nm);

            ColorBtn b = new ColorBtn(x, y + LH + 2, RW,
                    RH - LH - 8, this.rowLabel(t), this.sel.contains(t));
            b.setHoverStrings(nm);
            this.addButton(b, new TBtn(t));
            this.btns.put(t, b);

            if (++c >= 2)
            {
                c = 0;
                x = LX;
                y += RH;
            }
            else
            {
                x += RW + 10;
            }
        }
    }

    private String rowLabel(String t)
    {
        return StringUtils.translate(this.sel.contains(t)
                ? "reignrender.gui.affectedTypes.enabled"
                : "reignrender.gui.affectedTypes.disabled");
    }

    private void toggle(String t)
    {
        // Copy to a new list so setStrings() detects the change and fires the callback
        List<String> cur = new ArrayList<>(Cfg.G.REVEAL_AFFECTED_TYPES.getStrings());

        if (!cur.remove(t))
        {
            cur.add(t);
        }

        Cfg.G.REVEAL_AFFECTED_TYPES.setStrings(cur);
        this.sel = new HashSet<>(cur);
        FR.invalidateCaches();

        ColorBtn b = this.btns.get(t);

        if (b != null)
        {
            b.setState(this.sel.contains(t));
            b.setDisplayString(this.rowLabel(t));
        }
    }

    /**
     * A toggle button whose text is drawn green when the option is enabled and
     * red when it is disabled. The default colored text drawn by
     * {@link ButtonGeneric} is covered by a second draw in the state color.
     */
    private static class ColorBtn extends ButtonGeneric
    {
        private static final int COL_ON = 0xFF55FF55;
        private static final int COL_OFF = 0xFFFF5555;

        private boolean on;

        ColorBtn(int x, int y, int width, int height, String label, boolean on)
        {
            super(x, y, width, height, label);
            this.on = on;
        }

        void setState(boolean on)
        {
            this.on = on;
        }

        @Override
        public void render(GuiContext ctx, int mx, int my, boolean selected)
        {
            super.render(ctx, mx, my, selected);

            if (this.visible && this.displayString != null && !this.displayString.isEmpty())
            {
                int ty = this.y + (this.height - 8) / 2;
                this.drawCenteredStringWithShadow(ctx, this.x + this.width / 2, ty,
                        this.on ? COL_ON : COL_OFF, this.displayString);
            }
        }
    }

    private static class Done implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase b, int m)
        {
            GuiBase.openGui(new GConfigs());
        }
    }

    private class TBtn implements IButtonActionListener
    {
        private final String t;

        TBtn(String t)
        {
            this.t = t;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase b, int m)
        {
            GTypePick.this.toggle(this.t);
        }
    }
}