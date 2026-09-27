package com.wdylyh.config;

import fi.dy.masa.malilib.config.IHotkeyTogglable;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.IKeyboardInputHandler;
import fi.dy.masa.malilib.hotkeys.IMouseInputHandler;
import com.wdylyh.ModReference;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;

public class HotkeyRegistry implements IKeybindProvider, IKeyboardInputHandler, IMouseInputHandler {

    private static final HotkeyRegistry INSTANCE = new HotkeyRegistry();

    private HotkeyRegistry() {}

    public static HotkeyRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkeyTogglable toggle : RenderConfig.Toggles.OPTIONS) {
            manager.addKeybindToMap(toggle.getKeybind());
        }
        manager.addKeybindToMap(RenderConfig.Hotkeys.OPEN_CONFIG_GUI.getKeybind());
        manager.addKeybindToMap(RenderConfig.Hotkeys.REVEAL_HOTKEY.getKeybind());
        manager.addKeybindToMap(RenderConfig.Hotkeys.PICK_ENTITY_HOTKEY.getKeybind());
        manager.addKeybindToMap(RenderConfig.Hotkeys.TOGGLE_COORD_FILTER.getKeybind());
        manager.addKeybindToMap(RenderConfig.Hotkeys.TOGGLE_COORD_REPLACE.getKeybind());
        manager.addKeybindToMap(RenderConfig.Hotkeys.PICK_COORD_HOTKEY.getKeybind());
        manager.addKeybindToMap(RenderConfig.Hotkeys.TOGGLE_COUNT_LIMITS.getKeybind());
        manager.addKeybindToMap(RenderConfig.Hotkeys.TOGGLE_DISTANCE_LIMITS.getKeybind());
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(ModReference.MOD_NAME, "reignrender.hotkeys.category.disable", RenderConfig.Toggles.OPTIONS);
        manager.addHotkeysForCategory(ModReference.MOD_NAME, "reignrender.hotkeys.category.generic", RenderConfig.Hotkeys.OPTIONS);
    }

    @Override
    public boolean onKeyInput(KeyInput input, boolean eventKeyState) {
        return false;
    }

    @Override
    public boolean onMouseClick(Click click, boolean eventButtonState) {
        return false;
    }
}