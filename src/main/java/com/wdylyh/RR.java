package com.wdylyh;

import fi.dy.masa.malilib.event.InitializationHandler;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RR implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(Ref.MOD_ID);

	@Override
	public void onInitialize() {
		InitializationHandler.getInstance().registerInitializationHandler(new Init());

		LOGGER.info("ReignRender initialized!");
	}

	public static Identifier id(String path) {
		return Identifier.of(Ref.MOD_ID, path);
	}
}