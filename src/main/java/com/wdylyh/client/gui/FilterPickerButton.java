package com.wdylyh.client.gui;

import net.minecraft.client.gui.Click;
import com.wdylyh.config.Configs;
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
        if (config == Configs.Filter.FILTERED_ENTITIES)
        {
            return GuiFilterListPicker.FilterKind.ENTITIES;
        }
        if (config == Configs.Filter.FILTERED_BLOCKS)
        {
            return GuiFilterListPicker.FilterKind.BLOCKS;
        }
        if (config == Configs.Filter.FILTERED_FLUIDS)
        {
            return GuiFilterListPicker.FilterKind.FLUIDS;
        }
        if (config == Configs.Filter.FILTERED_BLOCK_ENTITIES)
        {
            return GuiFilterListPicker.FilterKind.BLOCK_ENTITIES;
        }

        return GuiFilterListPicker.FilterKind.PARTICLES;
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
