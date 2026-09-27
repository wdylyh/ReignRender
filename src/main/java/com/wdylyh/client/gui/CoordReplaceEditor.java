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
 * Coordinate replacement entry editor: a coordinate target plus one or more
 * "source=target" replacement rules, used by {@link CoordReplaceListScreen} when
 * adding a new entry (empty fields) and when editing an existing one
 * (pre-filled from the current entry line) — the coordinate replacement
 * counterpart of {@link CoordEntryEditor}.
 *
 * The coordinate part accepts "x,y,z" (a single block position) or
 * "x1,y1,z1~x2,y2,z2" (the region between two corners); the rules part takes
 * multiple "source=target" pairs separated by semicolons. Confirming appends
 * "coord;rule;rule" to (or replaces the edited index in) the coordinate
 * replacement list and returns to {@link CoordReplaceListScreen}. Rules that are not
 * "source=target" are rejected with an inline error so nothing invalid is
 * silently dropped.
 */
public class CoordReplaceEditor extends GuiBase
{
    /** Index of the entry to edit, or null to append a new entry. */
    private final Integer index;
    private final Screen back;

    // Draft text kept across the failure-initGui rebuild, so a rejected input
    // stays in the fields. The fields are only pre-filled from the existing
    // entry on the first init; a rebuild after a failed confirm keeps what the
    // user typed (including an intentionally cleared coordinate).
    private String coordVal = "";
    private String ruleVal = "";
    private boolean prefilled;

    /** Inline error line shown above the fields, or null. */
    private String err;

    private GuiTextFieldGeneric coordField;
    private GuiTextFieldGeneric ruleField;

    public CoordReplaceEditor(Integer index, Screen back)
    {
        this.index = index;
        this.back = back;
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.setParent(this.back);
        this.setTitle(StringUtils.translate("reignrender.gui.coordRep.edit.title"));

        String coord = "";
        String rules = "";

        if (!this.prefilled)
        {
            if (this.index != null)
            {
                List<String> list = RenderConfig.Filters.COORD_REPLACE_ENTRIES.getStrings();

                if (this.index >= 0 && this.index < list.size())
                {
                    String[] parts = list.get(this.index).split(";", -1);
                    coord = parts[0];

                    for (int i = 1; i < parts.length; i++)
                    {
                        rules = rules.isEmpty() ? parts[i] : rules + ";" + parts[i];
                    }
                }
            }

            this.coordVal = coord;
            this.ruleVal = rules;
            this.prefilled = true;
        }

        int cx = this.width / 2;
        int y = this.height / 2 - 34;

        if (this.err != null)
        {
            this.addLabel(cx - 130, y - 24, 260, 10, 0xFFE36B6B, this.err);
        }

        this.coordField = new GuiTextFieldGeneric(cx - 130, y, 260, 16, this.textRenderer);
        this.coordField.setValueWrapper(this.coordVal);
        this.coordField.setPlaceholder(Text.translatable("reignrender.gui.coordRep.placeholder.coord"));
        this.addTextField(this.coordField, f -> false);

        this.ruleField = new GuiTextFieldGeneric(cx - 130, y + 26, 260, 16, this.textRenderer);
        this.ruleField.setValueWrapper(this.ruleVal);
        this.ruleField.setPlaceholder(Text.translatable("reignrender.gui.coordRep.placeholder.rules"));
        this.addTextField(this.ruleField, f -> false);

        this.addLabel(cx - 130, y - 10, 260, 10,
                0x80FFFFFF, StringUtils.translate("reignrender.gui.coordRep.hint"));

        this.addButton(new ButtonGeneric(cx - 130, y + 50, 80, false, "reignrender.gui.coordRep.pick"),
                       (b, m) ->
                       {
                           // Keep whatever the player typed manually before opening the picker.
                           this.ruleVal = this.ruleField.getValueWrapper().trim();
                           this.err = null;
                           GuiBase.openGui(new ReplaceCategoryPicker(this));
                       });
        this.addButton(new ButtonGeneric(cx - 44, y + 50, 80, false,
                        this.index == null ? "reignrender.gui.coordRep.add" : "reignrender.gui.replace.finish"),
                       (b, m) -> this.confirm());
        this.addButton(new ButtonGeneric(cx + 130, y + 50, 80, true, "reignrender.gui.replace.cancel"),
                       (b, m) -> GuiBase.openGui(this.back));
    }

    /**
     * Appends a "source=target" rule picked in view selection mode and reopens
     * this editor, so the rule shows up in the existing field next to any
     * manually typed ones. Called at the end of the {@link ReplaceCategoryPicker} two-step
     * picker flow; the re-init keeps the draft, only the rule field gains one
     * more entry.
     */
    public void appendRule(String rule)
    {
        this.ruleVal = this.ruleVal.isEmpty() ? rule : this.ruleVal + ";" + rule;
        this.err = null;
        this.prefilled = true;
        GuiBase.openGui(this);
    }

    private void confirm()
    {
        // Refreshing the fields on failure keeps the draft; the values set
        // here must be picked up by the re-init before the fields are rebuilt.
        this.coordVal = this.coordField.getValueWrapper().trim();
        this.ruleVal = this.ruleField.getValueWrapper().trim();

        if (this.coordVal.isEmpty())
        {
            this.err = StringUtils.translate("reignrender.message.coordRepInvalidCoord");
            this.initGui();
            return;
        }

        // Reject coordinates the engine's parser would silently drop.
        if (CoordinateFilter.parseBox(this.coordVal) == null)
        {
            this.err = StringUtils.translate("reignrender.message.coordRepInvalidCoord");
            this.initGui();
            return;
        }

        List<String> rules = new ArrayList<>();
        List<String> bad = new ArrayList<>();

        for (String p : this.ruleVal.split(";"))
        {
            String rule = p.trim();

            if (rule.isEmpty())
            {
                continue;
            }

            int eq = rule.indexOf('=');

            if (eq <= 0 || eq >= rule.length() - 1)
            {
                bad.add(rule);
                continue;
            }

            String src = rule.substring(0, eq).trim();
            String tgt = rule.substring(eq + 1).trim();

            if (src.isEmpty() || tgt.isEmpty())
            {
                bad.add(rule);
            }
            else
            {
                rules.add(src + "=" + tgt);
            }
        }

        if (!bad.isEmpty())
        {
            this.err = Text.translatable("reignrender.message.coordRepInvalidRule",
                    String.join(";", bad)).getString();
            this.initGui();
            return;
        }

        // A region without rules is dropped entirely by ReplacementEngine.parseCoord,
        // so saving it would create a dead entry.
        if (rules.isEmpty())
        {
            this.err = StringUtils.translate("reignrender.message.coordRepNoRules");
            this.initGui();
            return;
        }

        String line = this.coordVal + ";" + String.join(";", rules);
        List<String> cur = new ArrayList<>(RenderConfig.Filters.COORD_REPLACE_ENTRIES.getStrings());

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

        RenderConfig.Filters.COORD_REPLACE_ENTRIES.setStrings(cur);
        GuiBase.openGui(new CoordReplaceListScreen());
    }
}