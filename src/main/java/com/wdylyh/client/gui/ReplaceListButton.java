package com.wdylyh.client.gui;

import net.minecraft.client.gui.Click;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;

/**
 * Config button for the replacement string lists in view selection mode.
 *
 * A click opens the two-step add flow directly (pick the source id, then the
 * target id) without passing through the full screen list editor first; the
 * list screen ({@link ReplaceListScreen}) remains reachable via Esc and shows
 * the finished entry, where rows can be deleted. In manual mode the rules are
 * typed into the inline "source=target" field instead.
 */
public class ReplaceListButton extends ConfigButtonStringList
{
    private final ReplaceListScreen.ReplaceKind replaceKind;

    public ReplaceListButton(int x, int y, int width, int height, IConfigStringList config,
                  IConfigGui configGui, IDialogHandler dialogHandler)
    {
        super(x, y, width, height, config, configGui, dialogHandler);
        this.replaceKind = ReplaceListScreen.ReplaceKind.of((ConfigStringList) config);
    }

    @Override
    protected boolean onMouseClickedImpl(Click c, boolean dc)
    {
        if (c.getKeycode() == 0)
        {
            // Straight into the add flow; the list screen is the back/landing screen.
            ReplaceListScreen rls = new ReplaceListScreen(this.replaceKind);
            rls.startAdd();
            return true;
        }

        return false;
    }
}