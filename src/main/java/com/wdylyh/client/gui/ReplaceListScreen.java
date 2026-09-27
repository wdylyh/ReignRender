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
 * Full screen replacement list editor, reached in view selection mode from the
 * replace config buttons. Shows every "source=target" entry of the backing
 * {@link ConfigStringList} with a delete button each, plus an add button.
 *
 * Adding runs a two-step picker flow: pick the source id and confirm, then pick
 * the target id and finish; the entry is appended as "source=target". Lists
 * without icon candidates (name tags, player names) use the text picker
 * ({@link NamePickScreen}) instead of the icon grid.
 */
public class ReplaceListScreen extends GuiBase
{
    public static final int LH = 20;
    public static final int LTOP = 60;
    public static final int DWB = 44;

    /**
     * Maps every replacement list onto the candidate source used by its view
     * selection flow. {@code fk} selects the icon picker kind; {@code null}
     * means the list has no icon candidates and falls back to typed names.
     */
    public enum ReplaceKind
    {
        PARTICLES(RenderConfig.Filters.REPLACE_PARTICLES, IconGridPicker.FKind.PARTICLES),
        BLOCKS(RenderConfig.Filters.REPLACE_BLOCKS, IconGridPicker.FKind.BLOCKS),
        ENTITIES(RenderConfig.Filters.REPLACE_ENTITIES, IconGridPicker.FKind.ENTITIES),
        FOGS(RenderConfig.Filters.REPLACE_FOGS, IconGridPicker.FKind.FOGS),
        ARMOR(RenderConfig.Filters.REPLACE_ARMOR, IconGridPicker.FKind.ARMOR),
        FLUIDS(RenderConfig.Filters.REPLACE_FLUIDS, IconGridPicker.FKind.FLUIDS),
        BLOCK_ENTITIES(RenderConfig.Filters.REPLACE_BLOCK_ENTITIES, IconGridPicker.FKind.BLOCK_ENTITIES),
        FALLING_BLOCKS(RenderConfig.Filters.REPLACE_FALLING_BLOCKS, IconGridPicker.FKind.FALLING_BLOCKS),
        ITEM_ENTITIES(RenderConfig.Filters.REPLACE_ITEM_ENTITIES, IconGridPicker.FKind.ITEM_ENTITIES),
        HELD_ITEMS(RenderConfig.Filters.REPLACE_HELD_ITEMS, IconGridPicker.FKind.ITEM_ENTITIES),
        HUD_ELEMENTS(RenderConfig.Filters.REPLACE_HUD_ELEMENTS, IconGridPicker.FKind.HUD),
        NAME_TAGS(RenderConfig.Filters.REPLACE_NAME_TAGS, null),
        PLAYER_NAMES(RenderConfig.Filters.REPLACE_PLAYER_NAMES, null);

        private final ConfigStringList cfg;
        private final IconGridPicker.FKind fk;

        ReplaceKind(ConfigStringList cfg, IconGridPicker.FKind fk)
        {
            this.cfg = cfg;
            this.fk = fk;
        }

        public ConfigStringList getCfg()
        {
            return this.cfg;
        }

        /** True for the name based lists, which have no icon candidates. */
        public boolean isText()
        {
            return this.fk == null;
        }

        /** The icon picker kind backing this list, or null for the text lists. */
        public IconGridPicker.FKind getKind()
        {
            return this.fk;
        }

        /** Returns the ReplaceKind for a config, or null when it is no replace list. */
        public static ReplaceKind of(IConfigStringList cfg)
        {
            for (ReplaceKind r : ReplaceKind.values())
            {
                if (r.cfg == cfg)
                {
                    return r;
                }
            }

            return null;
        }
    }

    private final ReplaceKind replaceKind;
    private int scrollOffset;

    public ReplaceListScreen(ReplaceKind replaceKind)
    {
        this.replaceKind = replaceKind;
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.setParent(new ConfigScreen());
        this.setTitle(StringUtils.translate("reignrender.gui.replace.title"));
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

        this.addButton(new ButtonGeneric(20, 26, 100, false, "reignrender.gui.replace.add"),
                       (b, m) -> this.startAdd());
        this.addButton(new ButtonGeneric(this.width - 10, 26, 120, true, "reignrender.gui.filter.done"),
                       (b, m) -> GuiBase.openGui(new ConfigScreen()));

        List<String> rules = this.replaceKind.getCfg().getStrings();
        int vis = Math.max(1, this.listH() / LH + 1);
        this.scrollOffset = Math.min(this.scrollOffset, Math.max(0, rules.size() - vis) * LH);

        if (rules.isEmpty())
        {
            this.addLabel(20, LTOP + 8, this.width - 40, 10,
                    0x80FFFFFF, StringUtils.translate("reignrender.gui.replace.empty"));
            return;
        }

        int top = this.scrollOffset / LH;

        for (int i = top; i < Math.min(rules.size(), top + vis + 1); i++)
        {
            this.addWidget(new Row(i, rules.get(i), 20, LTOP + i * LH - this.scrollOffset));
        }
    }

    /**
     * Opens the source picker (icon grid or text). Also called directly from
     * {@link ReplaceListButton} so a click on the config row skips this list
     * screen and starts the two-step add flow right away (Esc returns here).
     */
    public void startAdd()
    {
        if (this.replaceKind.isText())
        {
            GuiBase.openGui(new NamePickScreen(this::onSrc,
                    "reignrender.gui.replace.source.title",
                    "reignrender.gui.replace.confirm", this));
        }
        else
        {
            GuiBase.openGui(new IconGridPicker(this.replaceKind.getKind(), this::onSrc,
                    "reignrender.gui.replace.source.title",
                    "reignrender.gui.replace.confirm", this));
        }
    }

    /** Opens the target picker once the source id is known. */
    private void onSrc(String src)
    {
        if (this.replaceKind.isText())
        {
            GuiBase.openGui(new NamePickScreen(dst -> this.addRule(src, dst),
                    "reignrender.gui.replace.target.title",
                    "reignrender.gui.replace.finish", this));
        }
        else
        {
            GuiBase.openGui(new IconGridPicker(this.replaceKind.getKind(), dst -> this.addRule(src, dst),
                    "reignrender.gui.replace.target.title",
                    "reignrender.gui.replace.finish", this));
        }
    }

    /** Appends "src=dst" and returns to the list so the new entry is visible. */
    private void addRule(String src, String dst)
    {
        List<String> cur = new ArrayList<>(this.replaceKind.getCfg().getStrings());
        cur.add(src + "=" + dst);
        this.replaceKind.getCfg().setStrings(cur);
        GuiBase.openGui(new ReplaceListScreen(this.replaceKind));
    }

    private class Row extends WidgetBase
    {
        private final int index;
        private final String rule;

        Row(int index, String rule, int x, int y)
        {
            super(x, y, ReplaceListScreen.this.width - 40, LH);
            this.index = index;
            this.rule = rule;
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

            this.drawString(ctx, this.x + 4, this.y + 6, 0xFFFFFFFF, this.rule);
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
                    List<String> cur = new ArrayList<>(ReplaceListScreen.this.replaceKind.getCfg().getStrings());

                    if (this.index >= 0 && this.index < cur.size())
                    {
                        cur.remove(this.index);
                        ReplaceListScreen.this.replaceKind.getCfg().setStrings(cur);
                    }

                    ReplaceListScreen.this.rebuild();
                }

                return true;
            }

            return false;
        }
    }
}