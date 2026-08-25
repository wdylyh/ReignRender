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
public class FPickBtn extends ConfigButtonStringList
{
    private final GPicker.FKind k;

    public FPickBtn(int x, int y, int width, int height, IConfigStringList config,
                    IConfigGui configGui, IDialogHandler dialogHandler)
    {
        super(x, y, width, height, config, configGui, dialogHandler);
        this.k = resolveKind(config);
    }

    private static GPicker.FKind resolveKind(IConfigStringList config)
    {
        for (GPicker.FKind k : GPicker.FKind.values())
        {
            if (config == k.getConfig())
            {
                return k;
            }
        }

        return GPicker.FKind.FOGS;
    }

    @Override
    protected boolean onMouseClickedImpl(Click c, boolean dc)
    {
        if (c.getKeycode() == 0)
        {
            GuiBase.openGui(new GPicker(this.k));
            return true;
        }

        return false;
    }
}