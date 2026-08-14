package com.wdylyh.client.gui;

import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigResettable;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.interfaces.IConfigInfoProvider;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;

/**
 * Config option row for the filter lists.
 *
 * Only the STRING_LIST rows are customized: the config button opens the icon
 * grid picker instead of the plain text editor. Everything else (mode dropdowns
 * etc.) is delegated to the default malilib rendering.
 */
public class WidgetConfigOptionFilter extends WidgetConfigOption
{
    public WidgetConfigOptionFilter(int x, int y, int width, int height, int labelWidth, int configWidth,
            ConfigOptionWrapper wrapper, int listIndex, IKeybindConfigGui host,
            WidgetListConfigOptionsBase<?, ?> parent)
    {
        super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
    }

    @Override
    protected void addConfigOption(int x, int y, int labelWidth, int configWidth, IConfigBase config)
    {
        if (config.getType() != ConfigType.STRING_LIST)
        {
            super.addConfigOption(x, y, labelWidth, configWidth, config);
            return;
        }

        y += 1;

        String configName = config.getConfigGuiDisplayName();
        this.addLabel(x, y + 7, labelWidth, 8, 0xFFFFFFFF, configName);

        String comment = null;
        IConfigInfoProvider infoProvider = this.host.getHoverInfoProvider();

        if (infoProvider != null)
        {
            comment = infoProvider.getHoverInfo(config);
        }
        else
        {
            comment = config.getComment();
        }

        if (comment != null)
        {
            this.addConfigComment(x, y + 5, labelWidth, 12, comment);
        }

        x += labelWidth + 10;

        FilterPickerButton optionButton = new FilterPickerButton(x, y, configWidth, 20,
                (IConfigStringList) config, this.host, this.host.getDialogHandler());
        this.addConfigButtonEntry(x + configWidth + 2, y, (IConfigResettable) config, optionButton);
    }
}
