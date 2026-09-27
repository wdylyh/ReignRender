package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.wdylyh.config.RenderConfig;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.Click;

/**
 * Full screen coordinate filter list editor, reached in view selection mode
 * from the coordinate entries config button. Shows every entry of the backing
 * {@link RenderConfig.Filters#COORD_ENTRIES} list with a delete button each, plus an add
 * button.
 *
 * Clicking the body of a row re-opens it in the entry editor
 * ({@link CoordEntryEditor}) so the coordinate target and the optional attached ids
 * can be changed in place; clicking the delete button removes the whole
 * entry. Adding opens the same editor with the fields empty.
 */
public class CoordEntryListScreen extends GuiBase
{
    public static final int LH = 20;
    public static final int LTOP = 60;
    public static final int DWB = 44;

    private int scrollOffset;

    public CoordEntryListScreen()
    {
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.setParent(new ConfigScreen());
        this.setTitle(StringUtils.translate("reignrender.gui.coord.title"));
        this.scrollOffset = 0;
        this.rebuild();
    }

    @Override
    public boolean onMouseScrolled(double mx, double my, double ha, double va)
    {
        if (GuiBase.isMouseOver((int) mx, (int) my, 20, LTOP, this.width - 40, this.listH()))
        {
            this.scrollOffset = Math.max(0, this.scrollOffset - (int) va * LH);
            this.rebuild();
            return true;
        }

        return super.onMouseScrolled(mx, my, ha, va);
    }

    private int listH()
    {
        return Math.max(0, this.height - LTOP - 12);
    }

    private void rebuild()
    {
        this.clearElements();

        this.addButton(new ButtonGeneric(20, 26, 100, false, "reignrender.gui.coord.add"),
                       (b, m) -> this.startAdd());
        this.addButton(new ButtonGeneric(this.width - 10, 26, 120, true, "reignrender.gui.filter.done"),
                       (b, m) -> GuiBase.openGui(new ConfigScreen()));

        List<String> entries = RenderConfig.Filters.COORD_ENTRIES.getStrings();
        int vis = Math.max(1, this.listH() / LH + 1);
        this.scrollOffset = Math.min(this.scrollOffset, Math.max(0, entries.size() - vis) * LH);

        if (entries.isEmpty())
        {
            this.addLabel(20, LTOP + 8, this.width - 40, 10,
                    0x80FFFFFF, StringUtils.translate("reignrender.gui.coord.empty"));
            return;
        }

        int top = this.scrollOffset / LH;

        for (int i = top; i < Math.min(entries.size(), top + vis + 1); i++)
        {
            this.addWidget(new Row(i, entries.get(i), 20, LTOP + i * LH - this.scrollOffset));
        }
    }

    /** Opens the entry editor with empty fields to append a new entry. */
    private void startAdd()
    {
        GuiBase.openGui(new CoordEntryEditor(null, this));
    }

    /** Opens the entry editor pre-filled with the given entry. */
    private void editRow(int index)
    {
        GuiBase.openGui(new CoordEntryEditor(index, this));
    }

    private class Row extends WidgetBase
    {
        private final int index;
        private final String entry;

        Row(int index, String entry, int x, int y)
        {
            super(x, y, CoordEntryListScreen.this.width - 40, LH);
            this.index = index;
            this.entry = entry;
        }

        @Override
        public void render(GuiContext ctx, int mx, int my, boolean selected)
        {
            super.render(ctx, mx, my, selected);

            boolean hov = this.isMouseOver(mx, my);
            int dbx = this.x + this.width - DWB;
            boolean doh = hov && mx >= dbx;

            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20202020);

            if (hov)
            {
                RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x0FFFFFFF);
            }

            this.drawString(ctx, this.x + 4, this.y + 6, 0xFFFFFFFF, this.entry);
            RenderUtils.drawRect(ctx, dbx, this.y, DWB, this.height, doh ? 0x66FF3030 : 0x26803030);
            this.drawCenteredString(ctx, dbx + DWB / 2, this.y + 6, 0xFFFFFFFF, "X");
        }

        @Override
        protected boolean onMouseClickedImpl(Click c, boolean dc)
        {
            if (c.getKeycode() == 0)
            {
                if ((int) c.x() >= this.x + this.width - DWB)
                {
                    List<String> cur = new ArrayList<>(RenderConfig.Filters.COORD_ENTRIES.getStrings());

                    if (this.index >= 0 && this.index < cur.size())
                    {
                        cur.remove(this.index);
                        RenderConfig.Filters.COORD_ENTRIES.setStrings(cur);
                    }

                    CoordEntryListScreen.this.rebuild();
                }
                else
                {
                    CoordEntryListScreen.this.editRow(this.index);
                }

                return true;
            }

            return false;
        }
    }
}