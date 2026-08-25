package com.wdylyh.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.wdylyh.Ref;
import com.wdylyh.config.Cfg;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IConfigGuiAllTab;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.util.StringUtils;

public class GConfigs extends GuiConfigsBase implements IConfigGuiAllTab {

    private static GTab tab = GTab.HOTKEYS;

    public GConfigs() {
        super(10, 50, Ref.MOD_ID, null, "reignrender.gui.title.configs", Ref.MOD_VERSION);
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
        return new WList(listX, listY,
                this.getBrowserWidth(), this.getBrowserHeight(), this.getConfigWidth(), 0.f, this.useKeybindSearch(), this);
    }

    @Override
    public boolean useAllTab() {
        return true;
    }

    @Override
    protected boolean useKeybindSearch() {
        return GConfigs.tab == GTab.ALL ||
               GConfigs.tab == GTab.HOTKEYS;
    }

    @Override
    public List<ConfigOptionWrapper> getAllConfigs() {
        List<ConfigOptionWrapper> configs = new ArrayList<>(Cfg.Off.OPTIONS.size() + Cfg.G.OPTIONS.size() + Cfg.HK.OPTIONS.size() + Cfg.F.OPTIONS.size());
        configs.addAll(ConfigOptionWrapper.createFor(Cfg.Off.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(Cfg.G.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(Cfg.HK.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(Cfg.F.OPTIONS));
        return configs;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        GTab tab = GConfigs.tab;

        if (tab == GTab.ALL) {
            return this.getAllConfigs();
        }
        else if (tab == GTab.GENERIC) {
            return ConfigOptionWrapper.createFor(Cfg.G.OPTIONS);
        }
        else if (tab == GTab.HOTKEYS) {
            // Hotkeys tab includes both disable toggles and hotkey bindings
            List<ConfigOptionWrapper> configs = new ArrayList<>(Cfg.Off.OPTIONS.size() + Cfg.HK.OPTIONS.size());
            configs.addAll(ConfigOptionWrapper.createFor(Cfg.Off.OPTIONS));
            configs.addAll(ConfigOptionWrapper.createFor(Cfg.HK.OPTIONS));
            return configs;
        }
        else if (tab == GTab.FILTER) {
            return ConfigOptionWrapper.createFor(Cfg.F.OPTIONS);
        }

        return Collections.emptyList();
    }

    private int createButton(int x, int y, int width, GTab tab) {
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, tab.getDisplayName());
        button.setEnabled(GConfigs.tab != tab);
        this.addButton(button, new ButtonListener(tab, this));
        return button.getWidth();
    }

    private record ButtonListener(GTab tab, GConfigs parent) implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            GConfigs.tab = this.tab;
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
        FILTER  ("reignrender.gui.title.config");

        private final String translationKey;

        GTab(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return StringUtils.translate(this.translationKey);
        }
    }
}