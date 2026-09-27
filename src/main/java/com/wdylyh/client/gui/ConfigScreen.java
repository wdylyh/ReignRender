package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.wdylyh.ModReference;
import com.wdylyh.config.RenderConfig;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IConfigGuiAllTab;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.util.StringUtils;

public class ConfigScreen extends GuiConfigsBase implements IConfigGuiAllTab {

    private static GTab tab = GTab.HOTKEYS;

    public ConfigScreen() {
        super(10, 50, ModReference.MOD_ID, null, "reignrender.gui.title.configs", ModReference.MOD_VERSION);
    }

    @Override
    public void initGui() {
        super.initGui();

        this.clearOptions();

        int x = 10;
        int y = 26;

        for (GTab tab : GTab.values()) {
            x += this.createButton(x, y, -1, tab) + 2;
        }
    }

    @Override
    protected WidgetListConfigOptions createListWidget(int listX, int listY)
    {
        return new ConfigOptionListWidget(listX, listY,
                this.getBrowserWidth(), this.getBrowserHeight(), this.getConfigWidth(), 0.f, this.useKeybindSearch(), this);
    }

    @Override
    public boolean useAllTab() {
        return true;
    }

    @Override
    protected boolean useKeybindSearch() {
        return ConfigScreen.tab == GTab.ALL ||
               ConfigScreen.tab == GTab.HOTKEYS;
    }

    @Override
    public List<ConfigOptionWrapper> getAllConfigs() {
        List<ConfigOptionWrapper> configs = new ArrayList<>(RenderConfig.Toggles.OPTIONS.size() + RenderConfig.General.OPTIONS.size() + RenderConfig.Hotkeys.OPTIONS.size() + RenderConfig.Filters.OPTIONS.size() + RenderConfig.Face.OPTIONS.size());
        configs.addAll(ConfigOptionWrapper.createFor(RenderConfig.Toggles.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(RenderConfig.General.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(RenderConfig.Hotkeys.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(RenderConfig.Filters.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(RenderConfig.Face.OPTIONS));
        return configs;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        GTab tab = ConfigScreen.tab;

        if (tab == GTab.ALL) {
            return this.getAllConfigs();
        }
        else if (tab == GTab.GENERIC) {
            return ConfigOptionWrapper.createFor(RenderConfig.General.OPTIONS);
        }
        else if (tab == GTab.HOTKEYS) {
            // Hotkeys tab includes both disable toggles and hotkey bindings
            List<ConfigOptionWrapper> configs = new ArrayList<>(RenderConfig.Toggles.OPTIONS.size() + RenderConfig.Hotkeys.OPTIONS.size());
            configs.addAll(ConfigOptionWrapper.createFor(RenderConfig.Toggles.OPTIONS));
            configs.addAll(ConfigOptionWrapper.createFor(RenderConfig.Hotkeys.OPTIONS));
            return configs;
        }
        else if (tab == GTab.FILTER) {
            return ConfigOptionWrapper.createFor(RenderConfig.Filters.OPTIONS);
        }
        else if (tab == GTab.FACE) {
            return ConfigOptionWrapper.createFor(RenderConfig.Face.OPTIONS);
        }

        return Collections.emptyList();
    }

    private int createButton(int x, int y, int width, GTab tab) {
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, tab.getDisplayName());
        button.setEnabled(ConfigScreen.tab != tab);
        this.addButton(button, new ButtonListener(tab, this));
        return button.getWidth();
    }

    private record ButtonListener(GTab tab, ConfigScreen parent) implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            ConfigScreen.tab = this.tab;
            this.parent.reCreateListWidget();
            if (this.parent.getListWidget() != null) {
                this.parent.getListWidget().resetScrollbarPosition();
            }
            this.parent.initGui();
        }
    }

    public enum GTab {
        ALL     (IConfigGuiAllTab.getTranslationKey()),
        GENERIC ("reignrender.gui.title.generic"),
        HOTKEYS ("reignrender.gui.title.hotkeys"),
        FILTER  ("reignrender.gui.title.config"),
        FACE    ("reignrender.gui.title.face");

        private final String translationKey;

        GTab(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return StringUtils.translate(this.translationKey);
        }
    }

    /** Lets the face-mod list screens land the config GUI back on the face tab. */
    public static void setTab(GTab t) {
        tab = t;
    }
}