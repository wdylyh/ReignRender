package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.wdylyh.config.FaceModIndex;
import com.wdylyh.config.FaceModPacks;
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
import net.minecraft.util.Identifier;

/**
 * Full screen id list editor for one face-mod category.
 * Shows every id listed in the backing {@link ConfigStringList} with a delete
 * and a per-id texture list button each, plus an add button.
 *
 * Adding runs the single-select icon picker in view selection mode (the
 * visualized id selection) or a text input screen in manual mode; both hand
 * the picked id to the texture list screen, where the actual textures are
 * chosen and edited.
 */
public class FaceModListScreen extends GuiBase
{
    /**
     * The 12 face-mod categories. Each maps its backing config list (which is
     * also the row source of this screen) onto the icon picker kind used for
     * the view selection flow and the resource pack folder the texture list
     * screen enumerates. The category enum name is the index key of
     * {@link FaceModIndex}.
     */
    public enum FaceKind
    {
        PARTICLES     (RenderConfig.Face.FACE_PARTICLES,      IconGridPicker.FKind.PARTICLES,      "textures/particle",    "reignrender.gui.title.face.particles"),
        ENTITIES      (RenderConfig.Face.FACE_ENTITIES,       IconGridPicker.FKind.ENTITIES,       "textures/entity",      "reignrender.gui.title.face.entities"),
        BLOCKS        (RenderConfig.Face.FACE_BLOCKS,         IconGridPicker.FKind.BLOCKS,         "textures/block",       "reignrender.gui.title.face.blocks"),
        FLUIDS        (RenderConfig.Face.FACE_FLUIDS,         IconGridPicker.FKind.FLUIDS,         "textures/block",       "reignrender.gui.title.face.fluids"),
        BLOCK_ENTITIES(RenderConfig.Face.FACE_BLOCK_ENTITIES, IconGridPicker.FKind.BLOCK_ENTITIES, "textures/entity",      "reignrender.gui.title.face.blockEntities"),
        FALLING_BLOCKS(RenderConfig.Face.FACE_FALLING_BLOCKS, IconGridPicker.FKind.FALLING_BLOCKS, "falling",              "reignrender.gui.title.face.fallingBlocks"),
        ARMOR         (RenderConfig.Face.FACE_ARMOR,          IconGridPicker.FKind.ARMOR,          "textures/entity/equipment", "reignrender.gui.title.face.armor"),
        HELD_ITEMS    (RenderConfig.Face.FACE_HELD_ITEMS,     IconGridPicker.FKind.ITEM_ENTITIES,  "shadow/held",          "reignrender.gui.title.face.heldItems"),
        ITEM_ENTITIES (RenderConfig.Face.FACE_ITEM_ENTITIES,  IconGridPicker.FKind.ITEM_ENTITIES,  "shadow/dropped",       "reignrender.gui.title.face.itemEntities"),
        ELYTRA        (RenderConfig.Face.FACE_ELYTRA,         IconGridPicker.FKind.ELYTRA,         "textures/entity/equipment", "reignrender.gui.title.face.elytra"),
        SKY           (RenderConfig.Face.FACE_SKY,            IconGridPicker.FKind.SKY,            "textures/environment", "reignrender.gui.title.face.sky"),
        HUD_ELEMENTS  (RenderConfig.Face.FACE_HUD_ELEMENTS,   IconGridPicker.FKind.HUD,            "textures/gui/sprites", "reignrender.gui.title.face.hudElements"),
        // 区域面修改类别 ("区域面修改"): no backing face list (the ids come from
        // RenderConfig.Filters.REGION_FACE_ENTRIES lines), the edits are stored
        // in the ReignRender_RegionFace pack under textures/rface/... (the
        // "rface" domain drives the generic shadowSavePath mapping).
        R_BLOCKS      (null,                                  IconGridPicker.FKind.BLOCKS,         "rface",                "reignrender.gui.title.face.regionBlocks"),
        R_ENTITIES    (null,                                  IconGridPicker.FKind.ENTITIES,       "rface",                "reignrender.gui.title.face.regionEntities"),
        R_PARTICLES   (null,                                  IconGridPicker.FKind.PARTICLES,      "rface",                "reignrender.gui.title.face.regionParticles"),
        R_ITEMS       (null,                                  IconGridPicker.FKind.ITEM_ENTITIES,  "rface",                "reignrender.gui.title.face.regionItems");

        private final ConfigStringList cfg;
        private final IconGridPicker.FKind fk;
        private final String domain;
        private final String key;

        FaceKind(ConfigStringList cfg, IconGridPicker.FKind fk, String domain, String key)
        {
            this.cfg = cfg;
            this.fk = fk;
            this.domain = domain;
            this.key = key;
        }

        public ConfigStringList getCfg()
        {
            return this.cfg;
        }

        /** The icon picker kind used in the view selection add flow. */
        public IconGridPicker.FKind getKind()
        {
            return this.fk;
        }

        /** The resource pack folder the texture list screen enumerates. */
        public String getDomain()
        {
            return this.domain;
        }

        public String getTitleKey()
        {
            return this.key;
        }

        /** The category key used by {@link FaceModIndex}. */
        public String indexKey()
        {
            // The region categories use the RegionFaceIndex constants
            // (BLOCKS / ENTITIES / PARTICLES / ITEMS).
            return this.isRegion() ? this.name().substring(2) : this.name();
        }

        /** True for the region face-mod categories (edits in RegionFaceIndex / RegionFacePacks). */
        public boolean isRegion()
        {
            return this == R_BLOCKS || this == R_ENTITIES || this == R_PARTICLES || this == R_ITEMS;
        }

        /** True when this category edits shadow textures under reignrender:<domain>. */
        public boolean isShadow()
        {
            return this == FALLING_BLOCKS || this == HELD_ITEMS || this == ITEM_ENTITIES || this.isRegion();
        }

        /**
         * The pack texture path an edit of the given texture is saved to.
         * Shadow categories list and edit the vanilla textures but store the
         * result under the shadow path the shadow models reference:
         * {@code minecraft:textures/block/sand.png} ->
         * {@code reignrender:textures/falling/sand.png} for falling blocks,
         * {@code minecraft:textures/item/diamond.png} ->
         * {@code reignrender:textures/shadow/held/item/diamond.png} for held
         * items. Non-shadow categories save to the same path.
         */
        public String shadowSavePath(String texturePath)
        {
            if (!this.isShadow())
            {
                return texturePath;
            }

            Identifier id = Identifier.tryParse(texturePath);

            if (id == null)
            {
                return null;
            }

            String p = id.getPath();

            if (this == FALLING_BLOCKS)
            {
                return p.startsWith("textures/block/")
                        ? "reignrender:textures/falling/" + p.substring("textures/block/".length())
                        : null;
            }

            return p.startsWith("textures/")
                    ? "reignrender:textures/" + this.getDomain() + "/" + p.substring("textures/".length())
                    : null;
        }

        /** The inverse of {@link #shadowSavePath}: the vanilla texture a stored shadow path displays as. */
        public String vanillaOfShadow(String shadowPath)
        {
            if (!this.isShadow())
            {
                return shadowPath;
            }

            Identifier id = Identifier.tryParse(shadowPath);

            if (id == null || !id.getNamespace().equals("reignrender"))
            {
                return null;
            }

            String p = id.getPath();

            if (this == FALLING_BLOCKS)
            {
                return p.startsWith("textures/falling/")
                        ? "minecraft:textures/block/" + p.substring("textures/falling/".length())
                        : null;
            }

            String prefix = "textures/" + this.getDomain() + "/";

            return p.startsWith(prefix)
                    ? "minecraft:textures/" + p.substring(prefix.length())
                    : null;
        }

        /** Returns the FaceKind for a config, or null when it is no face list. */
        public static FaceKind of(IConfigStringList cfg)
        {
            for (FaceKind k : FaceKind.values())
            {
                if (k.cfg == cfg)
                {
                    return k;
                }
            }

            return null;
        }
    }

    private final FaceKind kind;
    private int scrollOffset;
    private String error;

    public FaceModListScreen(FaceKind kind)
    {
        this.kind = kind;
    }

    @Override
    public void initGui()
    {
        super.initGui();
        // So the config GUI behind this screen (Esc / done) opens on the face tab
        ConfigScreen.setTab(ConfigScreen.GTab.FACE);
        this.setParent(new ConfigScreen());
        this.setTitle(StringUtils.translate(this.kind.getTitleKey()));
        this.scrollOffset = 0;
        this.rebuild();
    }

    @Override
    public boolean onMouseScrolled(double mx, double my, double ha, double va)
    {
        if (GuiBase.isMouseOver((int) mx, (int) my, 20, ReplaceListScreen.LTOP, this.width - 40, this.listH()))
        {
            this.scrollOffset = Math.max(0, this.scrollOffset - (int) va * ReplaceListScreen.LH);
            this.rebuild();
            return true;
        }

        return super.onMouseScrolled(mx, my, ha, va);
    }

    private int listH()
    {
        return Math.max(0, this.height - ReplaceListScreen.LTOP - 12);
    }

    private void rebuild()
    {
        this.clearElements();

        this.addButton(new ButtonGeneric(20, 26, 100, false, "reignrender.gui.replace.add"),
                       (b, m) -> this.startAdd());
        this.addButton(new ButtonGeneric(this.width - 10, 26, 120, true, "reignrender.gui.filter.done"),
                       (b, m) -> GuiBase.openGui(new ConfigScreen()));

        List<String> ids = this.kind.getCfg().getStrings();
        int vis = Math.max(1, this.listH() / ReplaceListScreen.LH + 1);
        this.scrollOffset = Math.min(this.scrollOffset, Math.max(0, ids.size() - vis) * ReplaceListScreen.LH);

        if (this.error != null)
        {
            this.addLabel(130, 30, this.width - 260, 10, 0xFFFF5050, this.error);
        }

        if (ids.isEmpty())
        {
            this.addLabel(20, ReplaceListScreen.LTOP + 8, this.width - 40, 10,
                    0x80FFFFFF, StringUtils.translate("reignrender.gui.face.empty"));
            return;
        }

        int top = this.scrollOffset / ReplaceListScreen.LH;

        for (int i = top; i < Math.min(ids.size(), top + vis + 1); i++)
        {
            this.addWidget(new Row(i, ids.get(i), 20, ReplaceListScreen.LTOP + i * ReplaceListScreen.LH - this.scrollOffset));
        }
    }

    /**
     * Opens the id picker for the add flow: the visualized icon grid in view
     * selection mode, a typed id in manual mode. Invalid manual ids are
     * rejected with an inline error message.
     */
    public void startAdd()
    {
        this.error = null;

        if (RenderConfig.General.FILTER_INPUT_MODE.getOptionValue() == RenderConfig.General.INPUT_MODE_MANUAL)
        {
            GuiBase.openGui(new NamePickScreen(this::onId,
                    "reignrender.gui.face.pick.title",
                    "reignrender.gui.replace.confirm", this));
        }
        else
        {
            GuiBase.openGui(new IconGridPicker(this.kind.getKind(), this::onId,
                    "reignrender.gui.face.pick.title",
                    "reignrender.gui.replace.confirm", this, true));
        }
    }

    /** Validates and appends the picked id, then opens its texture list. */
    private void onId(String id)
    {
        String t = id.trim();

        if (!IconGridPicker.matches(this.kind.getKind(), t))
        {
            // Registry ids are all lowercase; HUD element ids are camel case, so
            // only fall back to lowercasing when the typed form itself fails
            String lower = t.toLowerCase(Locale.ROOT);

            if (!lower.equals(t) && IconGridPicker.matches(this.kind.getKind(), lower))
            {
                t = lower;
            }
            else
            {
                this.error = StringUtils.translate("reignrender.gui.face.invalid", id);
                GuiBase.openGui(this);
                return;
            }
        }

        List<String> cur = new ArrayList<>(this.kind.getCfg().getStrings());

        if (!cur.contains(t))
        {
            cur.add(t);
            this.kind.getCfg().setStrings(cur);
        }

        GuiBase.openGui(new FaceModTextureScreen(this.kind, t));
    }

    private class Row extends WidgetBase
    {
        private final int index;
        private final String id;

        Row(int index, String id, int x, int y)
        {
            super(x, y, FaceModListScreen.this.width - 40, ReplaceListScreen.LH);
            this.index = index;
            this.id = id;
        }

        @Override
        public void render(GuiContext ctx, int mx, int my, boolean selected)
        {
            super.render(ctx, mx, my, selected);

            boolean hov = this.isMouseOver(mx, my);
            int tbx = this.x + this.width - ReplaceListScreen.DWB * 2;
            int dbx = this.x + this.width - ReplaceListScreen.DWB;
            boolean toh = hov && mx >= tbx && mx < dbx;
            boolean doh = hov && mx >= dbx;
            boolean mod = FaceModIndex.hasOverrides(FaceModListScreen.this.kind.indexKey(), this.id);

            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20202020);

            if (hov)
            {
                RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x0FFFFFFF);
            }

            this.drawString(ctx, this.x + 4, this.y + 6, 0xFFFFFFFF, this.id);

            if (mod)
            {
                this.drawString(ctx, tbx - 10, this.y + 6, 0xFF00E000, "*");
            }

            RenderUtils.drawRect(ctx, tbx, this.y, ReplaceListScreen.DWB, this.height, toh ? 0x2630FF30 : 0x20308030);
            this.drawCenteredString(ctx, tbx + ReplaceListScreen.DWB / 2, this.y + 6, 0xFFFFFFFF,
                    StringUtils.translate("reignrender.gui.face.textures"));
            RenderUtils.drawRect(ctx, dbx, this.y, ReplaceListScreen.DWB, this.height, doh ? 0x66FF3030 : 0x26803030);
            this.drawCenteredString(ctx, dbx + ReplaceListScreen.DWB / 2, this.y + 6, 0xFFFFFFFF, "X");
        }

        @Override
        protected boolean onMouseClickedImpl(Click c, boolean dc)
        {
            if (c.getKeycode() == 0)
            {
                int tbx = this.x + this.width - ReplaceListScreen.DWB * 2;
                int dbx = this.x + this.width - ReplaceListScreen.DWB;

                if ((int) c.x() >= dbx)
                {
                    List<String> cur = new ArrayList<>(FaceModListScreen.this.kind.getCfg().getStrings());

                    if (this.index >= 0 && this.index < cur.size())
                    {
                        cur.remove(this.index);
                        FaceModListScreen.this.kind.getCfg().setStrings(cur);
                        // The id row delete also drops its edited textures from
                        // the resource pack (and reloads when any were removed)
                        FaceModPacks.removeTextures(
                                FaceModIndex.removeId(FaceModListScreen.this.kind.indexKey(), this.id));
                    }

                    FaceModListScreen.this.rebuild();
                    return true;
                }

                if ((int) c.x() >= tbx)
                {
                    GuiBase.openGui(new FaceModTextureScreen(FaceModListScreen.this.kind, this.id));
                    return true;
                }
            }

            return false;
        }
    }
}
