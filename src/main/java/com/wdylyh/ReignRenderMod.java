package com.wdylyh;

import fi.dy.masa.malilib.event.InitializationHandler;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReignRenderMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(ModReference.MOD_ID);

	static {
		// Minecraft 原版会把 JVM 设成 headless，导致 AWT 的 FileDialog 抛
		// HeadlessException。这里必须在任何 AWT 类被加载之前改回 false，
		// 静态块是能做到这一点的最早时机。
		System.setProperty("java.awt.headless", "false");
	}

	@Override
	public void onInitialize() {
		ShadowBlocks.register();
		RegionFaceBlocks.register();

		InitializationHandler.getInstance().registerInitializationHandler(new ModSetup());

		LOGGER.info("ReignRender initialized!");
	}

	public static Identifier id(String path) {
		return Identifier.of(ModReference.MOD_ID, path);
	}
}