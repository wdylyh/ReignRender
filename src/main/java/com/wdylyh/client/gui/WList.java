package com.wdylyh.client.gui;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;

/**
 * Config options list that builds {@link WOpt} rows, which
 * swap the filter list buttons to the icon grid picker.
 */
public class WList extends WidgetListConfigOptions
{
    public WList(int x, int y, int width, int height, int cw, float zLevel,
                 boolean useKbSearch, GuiConfigsBase parent)
    {
        super(x, y, width, height, cw, zLevel, useKbSearch, parent);
    }

    @Override
    protected WidgetConfigOption createListEntryWidget(int x, int y, int idx, boolean isOdd, ConfigOptionWrapper w)
    {
        return new WOpt(x, y, this.browserEntryWidth, this.browserEntryHeight,
                this.maxLabelWidth, this.configWidth, w, idx, this.parent, this);
    }
}