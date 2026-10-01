package com.wdylyh.client.gui;

import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;
import net.minecraft.client.gui.Click;

/**
 * Config button for the unified condition entry list on the conditions tab.
 *
 * A click opens the entry editor ({@link ConditionEntryEditor}) right away so a
 * new entry can be added without passing through the full screen list first;
 * the list screen ({@link ConditionListScreen}) is the back/landing screen
 * where the finished entry shows up and existing rows can be edited or deleted.
 * The entries contain semicolon separated {@code key=value} fields themselves,
 * so they cannot be represented in the inline semicolon-joined text field.
 */
public class ConditionListButton extends ConfigButtonStringList
{
    public ConditionListButton(int x, int y, int width, int height, IConfigStringList config,
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
            GuiBase.openGui(new ConditionEntryEditor(null, new ConditionListScreen()));
            return true;
        }

        return false;
    }
}
