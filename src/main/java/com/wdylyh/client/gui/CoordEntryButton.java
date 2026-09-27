package com.wdylyh.client.gui;

import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;
import net.minecraft.client.gui.Click;

/**
 * Config button for the coordinate entry list in view selection mode.
 *
 * A click opens the entry editor ({@link CoordEntryEditor}) right away so a new
 * entry can be added without passing through the full screen list first; the
 * list screen ({@link CoordEntryListScreen}) is the back/landing screen where the
 * finished entry shows up and existing rows can be edited or deleted. Used in
 * both input modes: the entries contain a semicolon separator themselves, so
 * they cannot be represented unambiguously in the inline semicolon-joined text
 * field.
 */
public class CoordEntryButton extends ConfigButtonStringList
{
    public CoordEntryButton(int x, int y, int width, int height, IConfigStringList config,
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
            GuiBase.openGui(new CoordEntryEditor(null, new CoordEntryListScreen()));
            return true;
        }

        return false;
    }
}