package com.wdylyh.config;

import fi.dy.masa.malilib.config.IHotkeyTogglable;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.IKeyboardInputHandler;
import fi.dy.masa.malilib.hotkeys.IMouseInputHandler;
import com.wdylyh.Ref;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;

public class Keys implements IKeybindProvider, IKeyboardInputHandler, IMouseInputHandler {

    private static final Keys INSTANCE = new Keys();

    private Keys() {}

    public static Keys getInstance() {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkeyTogglable toggle : Cfg.Off.OPTIONS) {
            manager.addKeybindToMap(toggle.getKeybind());
        }
        manager.addKeybindToMap(Cfg.HK.OPEN_CONFIG_GUI.getKeybind());
        manager.addKeybindToMap(Cfg.HK.REVEAL_HOTKEY.getKeybind());
        manager.addKeybindToMap(Cfg.HK.PICK_ENTITY_HOTKEY.getKeybind());
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(Ref.MOD_NAME, "reignrender.hotkeys.category.disable", Cfg.Off.OPTIONS);
        manager.addHotkeysForCategory(Ref.MOD_NAME, "reignrender.hotkeys.category.generic", Cfg.HK.OPTIONS);
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