package com.wdylyh.client.gui;

import net.minecraft.client.gui.Click;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;

/**
 * Config button for the per-type filter string lists.
 *
 * Replaces the default malilib behavior (opening the plain {@code GuiStringListEdit}
 * text editor) with the icon grid picker, so the configured registry ids are
 * chosen visually instead of typed by hand.
 */
public class FilterPickerButton extends ConfigButtonStringList
{
    private final GuiFilterListPicker.FilterKind kind;

    public FilterPickerButton(int x, int y, int width, int height, IConfigStringList config,
                              IConfigGui configGui, IDialogHandler dialogHandler)
    {
        super(x, y, width, height, config, configGui, dialogHandler);
        this.kind = resolveKind(config);
    }

    private static GuiFilterListPicker.FilterKind resolveKind(IConfigStringList config)
    {
        for (GuiFilterListPicker.FilterKind kind : GuiFilterListPicker.FilterKind.values())
        {
            if (config == kind.getConfig())
            {
                return kind;
            }
        }

        return GuiFilterListPicker.FilterKind.FOGS;
    }

    @Override
    protected boolean onMouseClickedImpl(Click click, boolean doubleClick)
    {
        if (click.getKeycode() == 0)
        {
            GuiBase.openGui(new GuiFilterListPicker(this.kind));
            return true;
        }

        return false;
    }
}
