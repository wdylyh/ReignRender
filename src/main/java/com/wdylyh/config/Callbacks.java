package com.wdylyh.config;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import com.wdylyh.client.gui.GuiConfigs;
import net.minecraft.client.MinecraftClient;

public class Callbacks {

    public static void init(MinecraftClient mc) {
        Configs.Hotkeys.OPEN_CONFIG_GUI.getKeybind().setCallback(new KeyCallbackOpenConfigGui());
    }

    private static class KeyCallbackOpenConfigGui implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            GuiBase.openGui(new GuiConfigs());
            return true;
        }
    }
}