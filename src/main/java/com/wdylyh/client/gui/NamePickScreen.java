package com.wdylyh.client.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Text based single-select screen for the name replacement lists (name tags
 * and player names). The player types one name and confirms; the value is
 * handed to the callback, which opens the next screen (the target picker or
 * the replacement list itself).
 */
public class NamePickScreen extends GuiBase
{
    private final IconGridPicker.PickCb callback;
    private final String titleKey;
    private final String confirmKey;
    private final Screen back;

    private GuiTextFieldGeneric field;

    public NamePickScreen(IconGridPicker.PickCb callback, String titleKey, String confirmKey, Screen back)
    {
        this.callback = callback;
        this.titleKey = titleKey;
        this.confirmKey = confirmKey;
        this.back = back;
    }

    @Override
    public void initGui()
    {
        super.initGui();

        this.setParent(this.back);
        this.setTitle(StringUtils.translate(this.titleKey));

        int cx = this.width / 2;

        this.field = new GuiTextFieldGeneric(cx - 100, this.height / 2 - 24, 200, 16, this.textRenderer);
        this.field.setPlaceholder(Text.translatable("reignrender.gui.replace.text.placeholder"));
        this.addTextField(this.field, f -> false);

        this.addLabel(cx - 100, this.height / 2 - 2, 200, 10,
                0x80FFFFFF, StringUtils.translate("reignrender.gui.replace.text.hint"));

        this.addButton(new ButtonGeneric(cx + 4, this.height / 2 + 10, 96, false, this.confirmKey),
                       (b, m) -> this.confirm());
        this.addButton(new ButtonGeneric(cx - 4, this.height / 2 + 10, 96, true, "reignrender.gui.replace.cancel"),
                       (b, m) -> GuiBase.openGui(this.back));
    }

    private void confirm()
    {
        String s = this.field.getValueWrapper().trim();

        if (!s.isEmpty())
        {
            this.callback.onPicked(s);
        }
    }
}