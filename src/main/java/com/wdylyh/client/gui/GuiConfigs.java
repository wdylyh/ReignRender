package com.wdylyh.client.gui;

import java.util.List;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import com.wdylyh.Reference;
import com.wdylyh.config.Configs;

public class GuiConfigs extends GuiConfigsBase {

    public GuiConfigs() {
        super(10, 50, Reference.MOD_ID, null, "reignrender.gui.title.configs", String.format("%s", Reference.MOD_VERSION));
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return ConfigOptionWrapper.createFor(Configs.Disable.OPTIONS);
    }
}