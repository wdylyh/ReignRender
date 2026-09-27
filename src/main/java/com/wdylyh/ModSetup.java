package com.wdylyh;

import com.wdylyh.client.gui.ConfigScreen;
import com.wdylyh.command.RenderCommand;
import com.wdylyh.config.ConfigCallbacks;
import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.HotkeyRegistry;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import net.minecraft.client.MinecraftClient;

public class ModSetup implements IInitializationHandler {

    @Override
    public void registerModHandlers() {
        ConfigManager.getInstance().registerConfigHandler(ModReference.MOD_ID, new RenderConfig());
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new ModInfo(ModReference.MOD_ID, ModReference.MOD_NAME, ConfigScreen::new)
        );

        InputEventHandler.getKeybindManager().registerKeybindProvider(HotkeyRegistry.getInstance());

        RenderCommand.register();

        ConfigCallbacks.init(MinecraftClient.getInstance());
    }
}