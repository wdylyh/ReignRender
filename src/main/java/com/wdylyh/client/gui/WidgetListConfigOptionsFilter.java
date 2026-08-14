package com.wdylyh.client.gui;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;

/**
 * Config options list that builds {@link WidgetConfigOptionFilter} rows, which
 * swap the filter list buttons to the icon grid picker.
 */
public class WidgetListConfigOptionsFilter extends WidgetListConfigOptions
{
    public WidgetListConfigOptionsFilter(int x, int y, int width, int height, int configWidth, float zLevel,
                                         boolean useKeybindSearch, GuiConfigsBase parent)
    {
        super(x, y, width, height, configWidth, zLevel, useKeybindSearch, parent);
    }

    @Override
    protected WidgetConfigOption createListEntryWidget(int x, int y, int listIndex, boolean isOdd, ConfigOptionWrapper wrapper)
    {
        return new WidgetConfigOptionFilter(x, y, this.browserEntryWidth, this.browserEntryHeight,
                this.maxLabelWidth, this.configWidth, wrapper, listIndex, this.parent, this);
    }
}
