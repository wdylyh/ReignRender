package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Number input screen for the per-id limit lists, opened after an id was
 * picked in the icon grid. Accepts any integer of -1 or greater; -1 stores the
 * id without a limit shown.
 */
public class LimitValueScreen extends GuiBase
{
    private final LimitListScreen.LimitKind limitKind;
    private final String id;
    private final Screen back;

    private GuiTextFieldGeneric field;

    public LimitValueScreen(LimitListScreen.LimitKind limitKind, String id, Screen back)
    {
        this.limitKind = limitKind;
        this.id = id;
        this.back = back;
    }

    @Override
    public void initGui()
    {
        super.initGui();

        this.setParent(this.back);
        this.setTitle(StringUtils.translate(this.limitKind.getValueTitleKey()));

        int cx = this.width / 2;

        this.field = new GuiTextFieldGeneric(cx - 100, this.height / 2 - 24, 200, 16, this.textRenderer);
        this.field.setPlaceholder(Text.translatable("reignrender.gui.limit.value.placeholder"));
        this.addTextField(this.field, f -> false);

        this.addLabel(cx - 100, this.height / 2 - 2, 200, 10,
                0x80FFFFFF, StringUtils.translate("reignrender.gui.limit.value.hint"));

        this.addButton(new ButtonGeneric(cx + 4, this.height / 2 + 10, 96, false, "reignrender.gui.limit.confirm"),
                       (b, m) -> this.confirm());
        this.addButton(new ButtonGeneric(cx - 4, this.height / 2 + 10, 96, true, "reignrender.gui.replace.cancel"),
                       (b, m) -> GuiBase.openGui(this.back));
    }

    /**
     * Validates the number and stores "id=number" in the list. An existing
     * entry for the same id is replaced, so the list can never contain two
     * entries for one id (the engine would apply the last one, making an
     * older entry impossible to remove through this screen). The screen
     * stays open on invalid input (empty, non-integer or below -1) so it can
     * be corrected; on success the limit list is reopened showing the entry.
     */
    private void confirm()
    {
        String s = this.field.getValueWrapper().trim();

        if (s.isEmpty())
        {
            return;
        }

        try
        {
            int value = Integer.parseInt(s);

            if (value < -1)
            {
                return;
            }

            List<String> cur = new ArrayList<>(this.limitKind.getCfg().getStrings());
            cur.removeIf(e -> e.startsWith(this.id + "="));
            cur.add(this.id + "=" + value);
            this.limitKind.getCfg().setStrings(cur);
            GuiBase.openGui(new LimitListScreen(this.limitKind));
        }
        catch (NumberFormatException ignored) {}
    }
}