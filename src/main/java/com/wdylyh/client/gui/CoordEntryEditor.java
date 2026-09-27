package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.wdylyh.config.CoordinateFilter;
import com.wdylyh.config.RenderConfig;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Coordinate entry editor: a coordinate target plus optional attached ids,
 * used by {@link CoordEntryListScreen} when adding a new entry (empty fields) and when
 * editing an existing one (pre-filled from the current entry line).
 *
 * The coordinate part accepts "x,y,z" (a single block position) or
 * "x1,y1,z1~x2,y2,z2" (the region between two corners); the optional ids part
 * takes multiple ids separated by semicolons. Confirming appends
 * "coord;id;id" to (or replaces the edited index in) the coordinate entry
 * list and returns to {@link CoordEntryListScreen}.
 */
public class CoordEntryEditor extends GuiBase
{
    /** Index of the entry to edit, or null to append a new entry. */
    private final Integer index;
    private final Screen back;

    private GuiTextFieldGeneric coordField;
    private GuiTextFieldGeneric idField;

    public CoordEntryEditor(Integer index, Screen back)
    {
        this.index = index;
        this.back = back;
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.setParent(this.back);
        this.setTitle(StringUtils.translate("reignrender.gui.coord.edit.title"));

        int cx = this.width / 2;
        int y = this.height / 2 - 34;

        String coord = "";
        String ids = "";

        if (this.index != null)
        {
            List<String> list = RenderConfig.Filters.COORD_ENTRIES.getStrings();

            if (this.index >= 0 && this.index < list.size())
            {
                String[] parts = list.get(this.index).split(";", -1);
                coord = parts[0];

                if (parts.length > 1)
                {
                    StringBuilder sb = new StringBuilder(parts[1]);

                    for (int i = 2; i < parts.length; i++)
                    {
                        sb.append(';').append(parts[i]);
                    }

                    ids = sb.toString();
                }
            }
        }

        this.coordField = new GuiTextFieldGeneric(cx - 130, y, 260, 16, this.textRenderer);
        this.coordField.setValueWrapper(coord);
        this.coordField.setPlaceholder(Text.translatable("reignrender.gui.coord.placeholder.coord"));
        this.addTextField(this.coordField, f -> false);

        this.idField = new GuiTextFieldGeneric(cx - 130, y + 26, 260, 16, this.textRenderer);
        this.idField.setValueWrapper(ids);
        this.idField.setPlaceholder(Text.translatable("reignrender.gui.coord.placeholder.ids"));
        this.addTextField(this.idField, f -> false);

        this.addLabel(cx - 130, y - 10, 260, 10,
                0x80FFFFFF, StringUtils.translate("reignrender.gui.coord.hint"));

        this.addButton(new ButtonGeneric(cx + 6, y + 50, 124, false,
                        this.index == null ? "reignrender.gui.coord.add" : "reignrender.gui.replace.finish"),
                       (b, m) -> this.confirm());
        this.addButton(new ButtonGeneric(cx - 10, y + 50, 124, true, "reignrender.gui.replace.cancel"),
                       (b, m) -> GuiBase.openGui(this.back));
    }

    private void confirm()
    {
        String coord = this.coordField.getValueWrapper().trim();

        if (coord.isEmpty())
        {
            return;
        }

        // Reject coordinates the engine's parser would silently drop, so an
        // invalid entry can never be written into the config.
        if (CoordinateFilter.parseBox(coord) == null)
        {
            ToastRenderer.show(Text.translatable("reignrender.message.coordRepInvalidCoord"),
                    ToastRenderer.TOAST_COLOR_ERROR);
            return;
        }

        List<String> ids = new ArrayList<>();

        for (String p : this.idField.getValueWrapper().split(";"))
        {
            String id = p.trim();

            if (!id.isEmpty())
            {
                ids.add(id);
            }
        }

        String line = coord + (ids.isEmpty() ? "" : ";" + String.join(";", ids));
        List<String> cur = new ArrayList<>(RenderConfig.Filters.COORD_ENTRIES.getStrings());

        if (this.index == null)
        {
            cur.add(line);
        }
        else if (this.index >= 0 && this.index < cur.size())
        {
            cur.set(this.index, line);
        }
        else
        {
            return;
        }

        RenderConfig.Filters.COORD_ENTRIES.setStrings(cur);
        GuiBase.openGui(new CoordEntryListScreen());
    }
}