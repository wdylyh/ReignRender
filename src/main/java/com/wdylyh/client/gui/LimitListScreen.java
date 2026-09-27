package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.wdylyh.config.RenderConfig;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.Click;

/**
 * Full screen per-id limit editor, reached in view selection mode from the
 * count/distance limit config buttons. Shows every "id=number" entry of the
 * backing {@link ConfigStringList} with a delete button each, plus one add
 * button per render category the limits can affect.
 *
 * Adding runs a two-step picker flow: pick the id in the icon grid and
 * confirm, then enter the number on the {@link LimitValueScreen}; the entry is
 * appended as "id=number" (-1 meaning unlimited).
 */
public class LimitListScreen extends GuiBase
{
    public static final int LH = 20;
    public static final int LTOP = 60;
    public static final int DWB = 44;

    /**
     * Maps the two limit lists onto their screen titles and the add buttons.
     * The ids are chosen from the entity and particle pickers, because those
     * are the only render paths the limits are actually applied to.
     */
    public enum LimitKind
    {
        COUNT(RenderConfig.Filters.COUNT_LIMITS,
                "reignrender.gui.limit.count.title",
                "reignrender.gui.limit.count.value.title"),
        DISTANCE(RenderConfig.Filters.DISTANCE_LIMITS,
                "reignrender.gui.limit.distance.title",
                "reignrender.gui.limit.distance.value.title");

        private final ConfigStringList cfg;
        private final String titleKey;
        private final String valueTitleKey;

        LimitKind(ConfigStringList cfg, String titleKey, String valueTitleKey)
        {
            this.cfg = cfg;
            this.titleKey = titleKey;
            this.valueTitleKey = valueTitleKey;
        }

        public ConfigStringList getCfg()
        {
            return this.cfg;
        }

        public String getTitleKey()
        {
            return this.titleKey;
        }

        public String getValueTitleKey()
        {
            return this.valueTitleKey;
        }

        /** Returns the LimitKind for a config, or null when it is no limit list. */
        public static LimitKind of(IConfigStringList cfg)
        {
            for (LimitKind l : LimitKind.values())
            {
                if (l.cfg == cfg)
                {
                    return l;
                }
            }

            return null;
        }
    }

    private final LimitKind limitKind;
    private int scrollOffset;

    public LimitListScreen(LimitKind limitKind)
    {
        this.limitKind = limitKind;
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.setParent(new ConfigScreen());
        this.setTitle(StringUtils.translate(this.limitKind.getTitleKey()));
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

        this.addButton(new ButtonGeneric(20, 26, 130, false, "reignrender.gui.limit.addEntity"),
                       (b, m) -> this.startAdd(IconGridPicker.FKind.ENTITIES, "reignrender.gui.limit.entity.title"));
        this.addButton(new ButtonGeneric(155, 26, 130, false, "reignrender.gui.limit.addParticle"),
                       (b, m) -> this.startAdd(IconGridPicker.FKind.PARTICLES, "reignrender.gui.limit.particle.title"));
        this.addButton(new ButtonGeneric(this.width - 10, 26, 120, true, "reignrender.gui.filter.done"),
                       (b, m) -> GuiBase.openGui(new ConfigScreen()));

        List<String> limits = this.limitKind.getCfg().getStrings();
        int vis = Math.max(1, this.listH() / LH + 1);
        this.scrollOffset = Math.min(this.scrollOffset, Math.max(0, limits.size() - vis) * LH);

        if (limits.isEmpty())
        {
            this.addLabel(20, LTOP + 8, this.width - 40, 10,
                    0x80FFFFFF, StringUtils.translate("reignrender.gui.limit.empty"));
            return;
        }

        int top = this.scrollOffset / LH;

        for (int i = top; i < Math.min(limits.size(), top + vis + 1); i++)
        {
            this.addWidget(new LimitListScreen.Row(i, limits.get(i), 20, LTOP + i * LH - this.scrollOffset));
        }
    }

    /** Opens the icon picker for the given category in single-select mode. */
    private void startAdd(IconGridPicker.FKind fk, String titleKey)
    {
        GuiBase.openGui(new IconGridPicker(fk, this::onPicked,
                titleKey, "reignrender.gui.limit.confirm", this));
    }

    /** Opens the number screen once the id is known. */
    private void onPicked(String id)
    {
        GuiBase.openGui(new LimitValueScreen(this.limitKind, id, this));
    }

    private class Row extends WidgetBase
    {
        private final int index;
        private final String limit;

        Row(int index, String limit, int x, int y)
        {
            super(x, y, LimitListScreen.this.width - 40, LH);
            this.index = index;
            this.limit = limit;
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

            this.drawString(ctx, this.x + 4, this.y + 6, 0xFFFFFFFF, this.limit);
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
                    List<String> cur = new ArrayList<>(LimitListScreen.this.limitKind.getCfg().getStrings());

                    if (this.index >= 0 && this.index < cur.size())
                    {
                        cur.remove(this.index);
                        LimitListScreen.this.limitKind.getCfg().setStrings(cur);
                    }

                    LimitListScreen.this.rebuild();
                }

                return true;
            }

            return false;
        }
    }
}