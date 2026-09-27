package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.FilterEngine;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.util.math.MathHelper;

/**
 * Multi-select picker for the render categories the reveal hotkey can affect.
 *
 * Lists every category from {@link FilterEngine#ALL_TYPES} as a row. Each row
 * shows the category name above and a button below whose label reads
 * "enabled" when the category is in the
 * {@link RenderConfig.General.REVEAL_AFFECTED_TYPES} string list and "disabled"
 * otherwise; clicking the button toggles it. Only the enabled categories are
 * bypassed (release mode) or force-hidden (enable mode) while the reveal
 * hotkey is held. The toggle button is drawn green when enabled and red when
 * disabled.
 */
public class AffectedTypePicker extends GuiBase
{
    private static final int RH = 32;
    private static final int RW = 150;
    private static final int LH = 10;
    private static final int LX = 20;
    // Below the done button, which occupies y 8..28, so the rows never overlap it
    private static final int LY = 34;
    // Rows scrolled fully above this line are not added at all: the widgets
    // have no clipping, so a row inside the done button zone would draw
    // straight over it while scrolling.
    private static final int TOP = 30;

    private final Map<String, ColorBtn> btns = new HashMap<>();
    private Set<String> sel = new HashSet<>();
    // Column count adapts to the window width (1..3), so narrow GUIs (small
    // windows or a high GUI scale) keep every button inside the screen.
    private int cols = 2;
    // Pixel offset of the list content; the wheel scrolls when the grid is
    // taller than the screen (14 categories can overflow at GUI scale 4 or
    // in small windows, which used to cut off the bottom rows entirely).
    private int scroll = 0;

    @Override
    public void initGui()
    {
        super.initGui();

        this.setParent(new ConfigScreen());
        this.setTitle(StringUtils.translate("reignrender.config.generic.prettyName.revealAffectedTypes"));
        this.sel = new HashSet<>(RenderConfig.General.REVEAL_AFFECTED_TYPES.getStrings());

        // At least LX space on each side of the grid must remain visible.
        this.cols = Math.max(1, Math.min(3, (this.width - 2 * LX + 10) / (RW + 10)));
        this.scroll = 0;

        this.rebuild();
    }

    private void rebuild()
    {
        // Rebuilds also serve the wheel scrolling, so everything is cleared
        // and re-added against the new offsets. The category name labels live
        // in the widget list, not the button list - clearing only the buttons
        // leaves every old label stuck at its previous scroll position while
        // the new ones stack up on top.
        this.btns.clear();
        this.clearButtons();
        this.clearElements();

        this.addButton(new ButtonGeneric(this.width - 10, 8, 120, true, "reignrender.gui.filter.done"),
                       new Done());

        int x = LX;
        int y = LY - this.scroll;
        int c = 0;

        for (String t : FilterEngine.ALL_TYPES)
        {
            String nm = StringUtils.translate("reignrender.config.generic.affectedType." + t);

            // Only rows fully inside the list area (below the done button,
            // above the screen bottom) are added; scrolled-out rows are
            // skipped entirely since the widgets are not clipped.
            if (y >= TOP && y < this.height)
            {
                // Category name above the toggle button
                this.addLabel(x, y, RW, LH, 0xFFFFFFFF, nm);

                ColorBtn b = new ColorBtn(x, y + LH + 2, RW,
                        RH - LH - 8, this.rowLabel(t), this.sel.contains(t));
                b.setHoverStrings(nm);
                this.addButton(b, new TBtn(t));
                this.btns.put(t, b);
            }

            if (++c >= this.cols)
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

    /** Total grid bottom (without the scroll offset applied). */
    private int gridBottom()
    {
        int rows = (FilterEngine.ALL_TYPES.size() + this.cols - 1) / this.cols;
        return LY + rows * RH;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        // The limit is rounded up to a multiple of the row height: a partial
        // final step would leave the rows off their 32px grid, and the
        // whole-row culling against TOP would then skip a row that is still
        // mostly visible ("a row disappears after scrolling").
        int raw = Math.max(0, this.gridBottom() - (this.height - 8));
        int maxScroll = raw > 0 ? (raw + RH - 1) / RH * RH : 0;

        if (maxScroll > 0)
        {
            this.scroll = MathHelper.clamp(this.scroll - (int) Math.signum(verticalAmount) * RH, 0, maxScroll);
            this.rebuild();
            return true;
        }

        return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
        List<String> cur = new ArrayList<>(RenderConfig.General.REVEAL_AFFECTED_TYPES.getStrings());

        if (!cur.remove(t))
        {
            cur.add(t);
        }

        RenderConfig.General.REVEAL_AFFECTED_TYPES.setStrings(cur);
        this.sel = new HashSet<>(cur);
        FilterEngine.invalidateCaches();

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
            GuiBase.openGui(new ConfigScreen());
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
            AffectedTypePicker.this.toggle(this.t);
        }
    }
}