package com.wdylyh.client.gui;

import com.wdylyh.config.RenderConfig;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;

/**
 * Config options list that builds {@link ConfigOptionRowWidget} rows, which
 * swap the filter list buttons to the icon grid picker.
 */
public class ConfigOptionListWidget extends WidgetListConfigOptions
{
    /** Rows that draw an inline field plus the error hint line under it. */
    private static final int FIELD_ROW_HEIGHT = 30;

    public ConfigOptionListWidget(int x, int y, int width, int height, int cw, float zLevel,
                 boolean useKbSearch, GuiConfigsBase parent)
    {
        super(x, y, width, height, cw, zLevel, useKbSearch, parent);
    }

    @Override
    protected int getBrowserEntryHeightFor(ConfigOptionWrapper w)
    {
        // Only the inline-field rows (manual input mode) need the extra room
        // for the hint line; the multi-select rows keep the default height.
        if (RenderConfig.General.FILTER_INPUT_MODE.getOptionValue() == RenderConfig.General.INPUT_MODE_MANUAL)
        {
            Object config = w.getConfig();

            if (config instanceof IConfigStringList cfg
                    && cfg != RenderConfig.General.REVEAL_AFFECTED_TYPES
                    && cfg != RenderConfig.Conditions.CONDITION_ENTRIES)
            {
                return FIELD_ROW_HEIGHT;
            }
        }

        return super.getBrowserEntryHeightFor(w);
    }

    @Override
    protected WidgetConfigOption createListEntryWidget(int x, int y, int idx, boolean isOdd, ConfigOptionWrapper w)
    {
        return new ConfigOptionRowWidget(x, y, this.browserEntryWidth, this.getBrowserEntryHeightFor(w),
                this.maxLabelWidth, this.configWidth, w, idx, this.parent, this);
    }
}