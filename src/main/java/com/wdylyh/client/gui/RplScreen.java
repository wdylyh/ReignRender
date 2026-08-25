package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.wdylyh.config.Cfg;
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
 * ({@link GPickText}) instead of the icon grid.
 */
public class RplScreen extends GuiBase
{
    public static final int LH = 20;
    public static final int LTOP = 60;
    public static final int DWB = 44;

    /**
     * Maps every replacement list onto the candidate source used by its view
     * selection flow. {@code fk} selects the icon picker kind; {@code null}
     * means the list has no icon candidates and falls back to typed names.
     */
    public enum RKind
    {
        PARTICLES(Cfg.F.REPLACE_PARTICLES, GPicker.FKind.PARTICLES),
        BLOCKS(Cfg.F.REPLACE_BLOCKS, GPicker.FKind.BLOCKS),
        ENTITIES(Cfg.F.REPLACE_ENTITIES, GPicker.FKind.ENTITIES),
        FOGS(Cfg.F.REPLACE_FOGS, GPicker.FKind.FOGS),
        ARMOR(Cfg.F.REPLACE_ARMOR, GPicker.FKind.ARMOR),
        FLUIDS(Cfg.F.REPLACE_FLUIDS, GPicker.FKind.FLUIDS),
        BLOCK_ENTITIES(Cfg.F.REPLACE_BLOCK_ENTITIES, GPicker.FKind.BLOCK_ENTITIES),
        FALLING_BLOCKS(Cfg.F.REPLACE_FALLING_BLOCKS, GPicker.FKind.FALLING_BLOCKS),
        ITEM_ENTITIES(Cfg.F.REPLACE_ITEM_ENTITIES, GPicker.FKind.ITEM_ENTITIES),
        NAME_TAGS(Cfg.F.REPLACE_NAME_TAGS, null),
        PLAYER_NAMES(Cfg.F.REPLACE_PLAYER_NAMES, null);

        private final ConfigStringList cfg;
        private final GPicker.FKind fk;

        RKind(ConfigStringList cfg, GPicker.FKind fk)
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

        /** Returns the RKind for a config, or null when it is no replace list. */
        public static RKind of(IConfigStringList cfg)
        {
            for (RKind r : RKind.values())
            {
                if (r.cfg == cfg)
                {
                    return r;
                }
            }

            return null;
        }
    }

    private final RKind rk;
    private int so;

    public RplScreen(RKind rk)
    {
        this.rk = rk;
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.setParent(new GConfigs());
        this.setTitle(StringUtils.translate("reignrender.gui.replace.title"));
        this.so = 0;
        this.rebuild();
    }

    @Override
    public boolean onMouseScrolled(double mx, double my, double ha, double va)
    {
        if (GuiBase.isMouseOver((int) mx, (int) my, 20, LTOP, this.width - 40, this.listH()))
        {
            this.so = Math.max(0, this.so - (int) va * LH);
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
                       (b, m) -> GuiBase.openGui(new GConfigs()));

        List<String> rules = this.rk.getCfg().getStrings();
        int vis = Math.max(1, this.listH() / LH + 1);
        this.so = Math.min(this.so, Math.max(0, rules.size() - vis) * LH);

        if (rules.isEmpty())
        {
            this.addLabel(20, LTOP + 8, this.width - 40, 10,
                    0x80FFFFFF, StringUtils.translate("reignrender.gui.replace.empty"));
            return;
        }

        int top = this.so / LH;

        for (int i = top; i < Math.min(rules.size(), top + vis + 1); i++)
        {
            this.addWidget(new Row(i, rules.get(i), 20, LTOP + i * LH - this.so));
        }
    }

    /** Opens the source picker (icon grid or text). */
    private void startAdd()
    {
        if (this.rk.isText())
        {
            GuiBase.openGui(new GPickText(this::onSrc,
                    "reignrender.gui.replace.source.title",
                    "reignrender.gui.replace.confirm", this));
        }
        else
        {
            GuiBase.openGui(new GPicker(this.rk.fk, this::onSrc,
                    "reignrender.gui.replace.source.title",
                    "reignrender.gui.replace.confirm", this));
        }
    }

    /** Opens the target picker once the source id is known. */
    private void onSrc(String src)
    {
        if (this.rk.isText())
        {
            GuiBase.openGui(new GPickText(dst -> this.addRule(src, dst),
                    "reignrender.gui.replace.target.title",
                    "reignrender.gui.replace.finish", this));
        }
        else
        {
            GuiBase.openGui(new GPicker(this.rk.fk, dst -> this.addRule(src, dst),
                    "reignrender.gui.replace.target.title",
                    "reignrender.gui.replace.finish", this));
        }
    }

    /** Appends "src=dst" and returns to the list so the new entry is visible. */
    private void addRule(String src, String dst)
    {
        List<String> cur = new ArrayList<>(this.rk.getCfg().getStrings());
        cur.add(src + "=" + dst);
        this.rk.getCfg().setStrings(cur);
        GuiBase.openGui(new RplScreen(this.rk));
    }

    private class Row extends WidgetBase
    {
        private final int idx;
        private final String rule;

        Row(int idx, String rule, int x, int y)
        {
            super(x, y, RplScreen.this.width - 40, LH);
            this.idx = idx;
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
                    List<String> cur = new ArrayList<>(RplScreen.this.rk.getCfg().getStrings());

                    if (this.idx >= 0 && this.idx < cur.size())
                    {
                        cur.remove(this.idx);
                        RplScreen.this.rk.getCfg().setStrings(cur);
                    }

                    RplScreen.this.rebuild();
                }

                return true;
            }

            return false;
        }
    }
}