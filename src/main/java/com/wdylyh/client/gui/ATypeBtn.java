package com.wdylyh.client.gui;

import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;
import net.minecraft.client.gui.Click;

/**
 * Config button for the {@code REVEAL_AFFECTED_TYPES} string list.
 *
 * Shows the currently checked categories like the default string list button,
 * but clicking it opens the multi-select category picker
 * ({@link GTypePick}) instead of the plain text editor.
 */
public class ATypeBtn extends ConfigButtonStringList
{
    public ATypeBtn(int x, int y, int width, int height, IConfigStringList config,
                    IConfigGui configGui, IDialogHandler dialogHandler)
    {
        super(x, y, width, height, config, configGui, dialogHandler);
    }

    @Override
    protected boolean onMouseClickedImpl(Click c, boolean dc)
    {
        if (c.getKeycode() == 0)
        {
            GuiBase.openGui(new GTypePick());
            return true;
        }

        return false;
    }
}