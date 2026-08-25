package com.wdylyh;

import com.wdylyh.client.gui.GConfigs;
import com.wdylyh.command.Cmd;
import com.wdylyh.config.Cb;
import com.wdylyh.config.Cfg;
import com.wdylyh.config.Keys;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import net.minecraft.client.MinecraftClient;

public class Init implements IInitializationHandler {

    @Override
    public void registerModHandlers() {
        ConfigManager.getInstance().registerConfigHandler(Ref.MOD_ID, new Cfg());
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new ModInfo(Ref.MOD_ID, Ref.MOD_NAME, GConfigs::new)
        );

        InputEventHandler.getKeybindManager().registerKeybindProvider(Keys.getInstance());

        Cmd.register();

        Cb.init(MinecraftClient.getInstance());
    }
}