package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.wdylyh.config.CoordinateFilter;
import com.wdylyh.config.RenderConfig;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Entry list screen of the region face-mod ("区域面修改"). Lists every id of
 * every {@link RenderConfig.Filters#REGION_FACE_ENTRIES} line as one row with
 * a texture list button (the pixel editor chain of the matching category) and
 * a per-id delete; new lines are added as "coordinate;id;id" text (the same
 * syntax as the coordinate filter entries) through the input field on top.
 *
 * The coordinate part of a line itself is edited as text (inline in the
 * config GUI or through this screen's add field); deleting the last id of a
 * line leaves a coordinate-only line, which matches every object in the
 * region.
 */
public class RegionFaceListScreen extends GuiBase
{
    private int scrollOffset;
    private String error;

    @Override
    public void initGui()
    {
        super.initGui();
        // So the config GUI behind this screen (Esc / done) opens on the filter tab
        ConfigScreen.setTab(ConfigScreen.GTab.FILTER);
        this.setParent(new ConfigScreen());
        this.setTitle(StringUtils.translate("reignrender.gui.title.regionFace"));
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
        // Keep the add text field alive: clearElements() also clears
        // textFields, which would wipe the box added in initGui
        this.clearChildren();
        this.clearButtons();

        GuiTextFieldGeneric sf = new GuiTextFieldGeneric(20, 26, Math.min(280, this.width - 20 - 190), 16, this.textRenderer);
        sf.setPlaceholder(Text.translatable("reignrender.gui.regionface.placeholder"));
        this.addTextField(sf, f -> false);

        this.addButton(new ButtonGeneric(20 + Math.min(280, this.width - 20 - 190) + 4, 24, 60, 20,
                StringUtils.translate("reignrender.gui.replace.add")), (b, m) -> this.addLine(sf.getValueWrapper()));
        this.addButton(new ButtonGeneric(this.width - 10, 26, 120, true, "reignrender.gui.filter.done"),
                       (b, m) -> GuiBase.openGui(new ConfigScreen()));

        if (this.error != null)
        {
            this.addLabel(20, 48, this.width - 40, 10, 0xFFFF5050, this.error);
        }

        List<String> lines = RenderConfig.Filters.REGION_FACE_ENTRIES.getStrings();

        // Flatten: one row per attached id (with its line index); coordinate
        // only lines show an info row (they affect every object in the region,
        // so there is no id to edit textures for).
        List<int[]> rows = new ArrayList<>();
        List<String> rowIds = new ArrayList<>();

        for (int li = 0; li < lines.size(); li++)
        {
            String[] parts = lines.get(li).split(";", -1);

            if (parts.length <= 1)
            {
                rows.add(new int[] { li, -1 });
                rowIds.add(null);
            }

            for (int pi = 1; pi < parts.length; pi++)
            {
                rows.add(new int[] { li, pi });
                rowIds.add(parts[pi].trim());
            }
        }

        int vis = Math.max(1, this.listH() / ReplaceListScreen.LH + 1);
        this.scrollOffset = Math.min(this.scrollOffset, Math.max(0, rows.size() - vis) * ReplaceListScreen.LH);

        if (rows.isEmpty())
        {
            this.addLabel(20, ReplaceListScreen.LTOP + 8, this.width - 40, 10,
                    0x80FFFFFF, StringUtils.translate("reignrender.gui.face.empty"));
            return;
        }

        int top = this.scrollOffset / ReplaceListScreen.LH;

        for (int i = top; i < Math.min(rows.size(), top + vis + 1); i++)
        {
            this.addWidget(new Row(i, rows.get(i)[0], rows.get(i)[1], rowIds.get(i),
                    20, ReplaceListScreen.LTOP + i * ReplaceListScreen.LH - this.scrollOffset));
        }
    }

    /** Validates and appends one "coordinate;id..." line. */
    private void addLine(String line)
    {
        if (line == null || line.trim().isEmpty())
        {
            return;
        }

        this.error = null;

        String t = line.trim();
        String coord = t.split(";", -1)[0].trim();

        if (CoordinateFilter.parseBox(coord) == null)
        {
            this.error = StringUtils.translate("reignrender.gui.regionface.invalid", coord);
            this.rebuild();
            return;
        }

        List<String> cur = new ArrayList<>(RenderConfig.Filters.REGION_FACE_ENTRIES.getStrings());
        cur.add(t);
        // Writing through the config object fires the change callback, which
        // regenerates the region resources and reloads.
        RenderConfig.Filters.REGION_FACE_ENTRIES.setStrings(cur);
        this.rebuild();
    }

    /** Removes the id at (line, part); the line itself is dropped when it was its last id. */
    private static void removeIdPart(int line, int part)
    {
        List<String> cur = new ArrayList<>(RenderConfig.Filters.REGION_FACE_ENTRIES.getStrings());

        if (line < 0 || line >= cur.size())
        {
            return;
        }

        String[] parts = cur.get(line).split(";", -1);

        if (part < 1 || part >= parts.length)
        {
            return;
        }

        String id = parts[part].trim();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0)
            {
                sb.append(';');
            }

            if (i != part)
            {
                sb.append(parts[i]);
            }
        }

        String nl = sb.toString();

        while (nl.endsWith(";"))
        {
            nl = nl.substring(0, nl.length() - 1);
        }

        if (nl.isEmpty())
        {
            cur.remove(line);
        }
        else
        {
            cur.set(line, nl);
        }

        RenderConfig.Filters.REGION_FACE_ENTRIES.setStrings(cur);

        // Drop the id's edited textures from the region pack as well.
        if (!id.isEmpty())
        {
            com.wdylyh.config.RegionFaceEngine.Id parsed = parseRegionId(id);

            if (parsed != null)
            {
                for (String cat : new String[] { com.wdylyh.config.RegionFaceIndex.BLOCKS,
                        com.wdylyh.config.RegionFaceIndex.ENTITIES,
                        com.wdylyh.config.RegionFaceIndex.PARTICLES,
                        com.wdylyh.config.RegionFaceIndex.ITEMS })
                {
                    com.wdylyh.config.RegionFacePacks.removeTextures(
                            com.wdylyh.config.RegionFaceIndex.removeId(cat, id));
                }
            }
        }
    }

    /** The category resolution of one attached id (same registries as the engine). */
    static com.wdylyh.config.RegionFaceEngine.Id parseRegionId(String id)
    {
        Identifier iid = Identifier.tryParse(id.contains(":") ? id.trim().toLowerCase(java.util.Locale.ROOT)
                : "minecraft:" + id.trim().toLowerCase(java.util.Locale.ROOT));

        if (iid == null)
        {
            return null;
        }

        boolean block = Registries.BLOCK.containsId(iid);
        boolean entity = Registries.ENTITY_TYPE.containsId(iid);
        boolean particle = Registries.PARTICLE_TYPE.containsId(iid);
        boolean item = Registries.ITEM.containsId(iid);

        if (!block && !entity && !particle && !item)
        {
            return null;
        }

        return new com.wdylyh.config.RegionFaceEngine.Id(iid.toString(), block, entity, particle, item);
    }

    /** The region face kind of one attached id (first matching category). */
    static FaceModListScreen.FaceKind kindOf(com.wdylyh.config.RegionFaceEngine.Id id)
    {
        if (id.block())
        {
            return FaceModListScreen.FaceKind.R_BLOCKS;
        }

        if (id.entity())
        {
            return FaceModListScreen.FaceKind.R_ENTITIES;
        }

        if (id.particle())
        {
            return FaceModListScreen.FaceKind.R_PARTICLES;
        }

        return FaceModListScreen.FaceKind.R_ITEMS;
    }

    private class Row extends WidgetBase
    {
        private final int line;
        private final int part;
        private final String id;

        Row(int index, int line, int part, String id, int x, int y)
        {
            super(x, y, RegionFaceListScreen.this.width - 40, ReplaceListScreen.LH);
            this.line = line;
            this.part = part;
            this.id = id;
        }

        @Override
        public void render(GuiContext ctx, int mx, int my, boolean selected)
        {
            super.render(ctx, mx, my, selected);

            boolean hov = this.isMouseOver(mx, my);
            com.wdylyh.config.RegionFaceEngine.Id parsed = this.id != null ? parseRegionId(this.id) : null;
            boolean idRow = parsed != null;
            int tbx = this.x + this.width - (idRow ? ReplaceListScreen.DWB * 2 : ReplaceListScreen.DWB);
            int dbx = this.x + this.width - ReplaceListScreen.DWB;
            boolean toh = idRow && hov && mx >= tbx && mx < dbx;
            boolean doh = idRow && hov && mx >= dbx;
            boolean mod = idRow && com.wdylyh.config.RegionFaceIndex.hasOverrides(
                    kindOf(parsed).indexKey(), parsed.id());

            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x20202020);

            if (hov)
            {
                RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x0FFFFFFF);
            }

            this.drawString(ctx, this.x + 4, this.y + 6, 0xFFFFFFFF,
                    this.id != null ? this.id : StringUtils.translate("reignrender.gui.regionface.all"));

            if (mod)
            {
                this.drawString(ctx, tbx - 10, this.y + 6, 0xFF00E000, "*");
            }

            if (idRow)
            {
                RenderUtils.drawRect(ctx, tbx, this.y, ReplaceListScreen.DWB, this.height, toh ? 0x2630FF30 : 0x20308030);
                this.drawCenteredString(ctx, tbx + ReplaceListScreen.DWB / 2, this.y + 6, 0xFFFFFFFF,
                        StringUtils.translate("reignrender.gui.face.textures"));
            }

            if (idRow)
            {
                RenderUtils.drawRect(ctx, dbx, this.y, ReplaceListScreen.DWB, this.height, doh ? 0x66FF3030 : 0x26803030);
                this.drawCenteredString(ctx, dbx + ReplaceListScreen.DWB / 2, this.y + 6, 0xFFFFFFFF, "X");
            }
        }

        @Override
        protected boolean onMouseClickedImpl(Click c, boolean dc)
        {
            if (c.getKeycode() != 0 || this.id == null)
            {
                return false;
            }

            int tbx = this.x + this.width - ReplaceListScreen.DWB * 2;
            int dbx = this.x + this.width - ReplaceListScreen.DWB;

            if ((int) c.x() >= dbx)
            {
                removeIdPart(this.line, this.part);
                RegionFaceListScreen.this.rebuild();
                return true;
            }

            if ((int) c.x() >= tbx)
            {
                com.wdylyh.config.RegionFaceEngine.Id parsed = parseRegionId(this.id);

                if (parsed != null)
                {
                    GuiBase.openGui(new FaceModTextureScreen(kindOf(parsed), parsed.id()));
                }

                return true;
            }

            return false;
        }
    }
}
