package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.wdylyh.Reference;
import com.wdylyh.config.Configs;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IConfigGuiAllTab;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiConfigs extends GuiConfigsBase implements IConfigGuiAllTab {

    private static ConfigGuiTab tab = ConfigGuiTab.HOTKEYS;

    public GuiConfigs() {
        super(10, 50, Reference.MOD_ID, null, "reignrender.gui.title.configs", Reference.MOD_VERSION);
    }

    @Override
    public void initGui() {
        super.initGui();

        this.clearOptions();

        int x = 10;
        int y = 26;

        for (ConfigGuiTab tab : ConfigGuiTab.values()) {
            x += this.createButton(x, y, -1, tab) + 2;
        }
    }

    @Override
    protected WidgetListConfigOptions createListWidget(int listX, int listY)
    {
        return new WidgetListConfigOptionsFilter(listX, listY,
                this.getBrowserWidth(), this.getBrowserHeight(), this.getConfigWidth(), 0.f, this.useKeybindSearch(), this);
    }

    @Override
    public boolean useAllTab() {
        return true;
    }

    @Override
    protected boolean useKeybindSearch() {
        return GuiConfigs.tab == ConfigGuiTab.ALL ||
               GuiConfigs.tab == ConfigGuiTab.HOTKEYS;
    }

    @Override
    public List<ConfigOptionWrapper> getAllConfigs() {
        List<ConfigOptionWrapper> configs = new ArrayList<>();
        configs.addAll(ConfigOptionWrapper.createFor(Configs.Disable.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(Configs.Generic.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(Configs.Hotkeys.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(Configs.Filter.OPTIONS));
        return configs;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        ConfigGuiTab tab = GuiConfigs.tab;

        if (tab == ConfigGuiTab.ALL) {
            return this.getAllConfigs();
        }
        else if (tab == ConfigGuiTab.GENERIC) {
            return ConfigOptionWrapper.createFor(Configs.Generic.OPTIONS);
        }
        else if (tab == ConfigGuiTab.HOTKEYS) {
            // Hotkeys tab includes both disable toggles and hotkey bindings
            List<ConfigOptionWrapper> configs = new ArrayList<>();
            configs.addAll(ConfigOptionWrapper.createFor(Configs.Disable.OPTIONS));
            configs.addAll(ConfigOptionWrapper.createFor(Configs.Hotkeys.OPTIONS));
            return configs;
        }
        else if (tab == ConfigGuiTab.FILTER) {
            return ConfigOptionWrapper.createFor(Configs.Filter.OPTIONS);
        }

        return Collections.emptyList();
    }

    private int createButton(int x, int y, int width, ConfigGuiTab tab) {
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, tab.getDisplayName());
        button.setEnabled(GuiConfigs.tab != tab);
        this.addButton(button, new ButtonListener(tab, this));
        return button.getWidth();
    }

    private record ButtonListener(ConfigGuiTab tab, GuiConfigs parent) implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            GuiConfigs.tab = this.tab;
            this.parent.reCreateListWidget();
            if (this.parent.getListWidget() != null) {
                this.parent.getListWidget().resetScrollbarPosition();
            }
            this.parent.initGui();
        }
    }

    public enum ConfigGuiTab {
        ALL     (IConfigGuiAllTab.getTranslationKey()),
        GENERIC ("reignrender.gui.title.generic"),
        HOTKEYS ("reignrender.gui.title.hotkeys"),
        FILTER  ("reignrender.gui.title.config");

        private final String translationKey;

        ConfigGuiTab(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return StringUtils.translate(this.translationKey);
        }
    }
}
