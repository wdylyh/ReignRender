package com.wdylyh.client.gui;

import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;
import net.minecraft.client.gui.Click;

/**
 * Config button for the coordinate replacement entry list, mirroring
 * {@link CoordEntryButton}: a click opens the entry editor
 * ({@link CoordReplaceEditor}) right away so a new entry can be added without
 * passing through the full screen list first; the list screen
 * ({@link CoordReplaceListScreen}) is the back/landing screen. Used in both view
 * and manual input mode, because the entries contain a semicolon separator
 * themselves ("x,y,z;src=dst;...") and cannot be represented unambiguously in
 * the inline semicolon-joined text field.
 */
public class CoordReplaceButton extends ConfigButtonStringList
{
    public CoordReplaceButton(int x, int y, int width, int height, IConfigStringList config,
                       IConfigGui configGui, IDialogHandler dialogHandler)
    {
        super(x, y, width, height, config, configGui, dialogHandler);
    }

    @Override
    protected boolean onMouseClickedImpl(Click c, boolean dc)
    {
        if (c.getKeycode() == 0)
        {
            // Straight into the add form; the list screen is the back/landing screen.
            GuiBase.openGui(new CoordReplaceEditor(null, new CoordReplaceListScreen()));
            return true;
        }

        return false;
    }
}