package com.wdylyh.config;

import fi.dy.masa.malilib.config.IHotkeyTogglable;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.IKeyboardInputHandler;
import fi.dy.masa.malilib.hotkeys.IMouseInputHandler;
import com.wdylyh.Reference;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;

public class InputHandler implements IKeybindProvider, IKeyboardInputHandler, IMouseInputHandler {

    private static final InputHandler INSTANCE = new InputHandler();

    private InputHandler() {}

    public static InputHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkeyTogglable toggle : Configs.Disable.OPTIONS) {
            manager.addKeybindToMap(toggle.getKeybind());
        }
        manager.addKeybindToMap(Configs.Hotkeys.OPEN_CONFIG_GUI.getKeybind());
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(Reference.MOD_NAME, "reignrender.hotkeys.category.disable", Configs.Disable.OPTIONS);
        manager.addHotkeysForCategory(Reference.MOD_NAME, "reignrender.hotkeys.category.generic", Configs.Hotkeys.OPTIONS);
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