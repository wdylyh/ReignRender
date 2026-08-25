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
 * Opens the full screen replacement list editor ({@link RplScreen}) instead
 * of the default malilib text editor, so entries are managed visually
 * (add via a two-step picker flow, delete per row).
 */
public class RplBtn extends ConfigButtonStringList
{
    private final RplScreen.RKind rk;

    public RplBtn(int x, int y, int width, int height, IConfigStringList config,
                  IConfigGui configGui, IDialogHandler dialogHandler)
    {
        super(x, y, width, height, config, configGui, dialogHandler);
        this.rk = RplScreen.RKind.of((ConfigStringList) config);
    }

    @Override
    protected boolean onMouseClickedImpl(Click c, boolean dc)
    {
        if (c.getKeycode() == 0)
        {
            GuiBase.openGui(new RplScreen(this.rk));
            return true;
        }

        return false;
    }
}