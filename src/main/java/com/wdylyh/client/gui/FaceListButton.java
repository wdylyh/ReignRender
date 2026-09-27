package com.wdylyh.client.gui;

import net.minecraft.client.gui.Click;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;

/**
 * Config button for the face-mod category lists. A click opens the full screen
 * id list editor for the category, where ids are added (visualized picker or
 * manual input depending on the input mode) and each id leads to its texture
 * list and the pixel editor.
 */
public class FaceListButton extends ConfigButtonStringList
{
    private final FaceModListScreen.FaceKind kind;

    public FaceListButton(int x, int y, int width, int height, IConfigStringList config,
                          IConfigGui configGui, IDialogHandler dialogHandler)
    {
        super(x, y, width, height, config, configGui, dialogHandler);
        this.kind = FaceModListScreen.FaceKind.of((ConfigStringList) config);
    }

    @Override
    protected boolean onMouseClickedImpl(Click c, boolean dc)
    {
        if (c.getKeycode() == 0 && this.kind != null)
        {
            GuiBase.openGui(new FaceModListScreen(this.kind));
            return true;
        }

        return false;
    }
}
