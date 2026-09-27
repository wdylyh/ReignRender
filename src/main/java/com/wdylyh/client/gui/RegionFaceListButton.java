package com.wdylyh.client.gui;

import net.minecraft.client.gui.Click;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;

/**
 * Config button for the region face-mod entries. A click opens the entry list
 * screen where every attached id leads to its region texture list and the
 * pixel editor, and new "coordinate;id..." lines are added.
 */
public class RegionFaceListButton extends ConfigButtonStringList
{
    public RegionFaceListButton(int x, int y, int width, int height, IConfigStringList config,
                                IConfigGui configGui, IDialogHandler dialogHandler)
    {
        super(x, y, width, height, config, configGui, dialogHandler);
    }

    @Override
    protected boolean onMouseClickedImpl(Click c, boolean dc)
    {
        if (c.getKeycode() == 0)
        {
            GuiBase.openGui(new RegionFaceListScreen());
            return true;
        }

        return false;
    }
}
