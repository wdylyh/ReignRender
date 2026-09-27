package com.wdylyh.client.gui;

import net.minecraft.client.gui.Click;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;

/**
 * Config button for the per-id limit string lists in view selection mode.
 *
 * Opens the full screen limit editor ({@link LimitListScreen}) instead of the
 * default malilib text editor, so entries are managed visually (add via the
 * icon picker + number screen, delete per row).
 */
public class LimitListButton extends ConfigButtonStringList
{
    private final LimitListScreen.LimitKind limitKind;

    public LimitListButton(int x, int y, int width, int height, IConfigStringList config,
                  IConfigGui configGui, IDialogHandler dialogHandler)
    {
        super(x, y, width, height, config, configGui, dialogHandler);
        this.limitKind = LimitListScreen.LimitKind.of((ConfigStringList) config);
    }

    @Override
    protected boolean onMouseClickedImpl(Click c, boolean dc)
    {
        if (c.getKeycode() == 0)
        {
            GuiBase.openGui(new LimitListScreen(this.limitKind));
            return true;
        }

        return false;
    }
}