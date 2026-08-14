package com.wdylyh.config;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigOptionValues;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.config.value.BaseOptionListConfigValue;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import com.wdylyh.client.gui.GuiConfigs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Callbacks {

    private static final Logger LOGGER = LoggerFactory.getLogger(Callbacks.class);

    public static void init(MinecraftClient mc) {
        Configs.Hotkeys.OPEN_CONFIG_GUI.getKeybind().setCallback(new KeyCallbackOpenConfigGui());

        // Block/fluid rendering data is cached per chunk. When these toggles change,
        // force the world renderer to rebuild so the change takes effect immediately.
        Configs.Disable.DISABLE_BLOCKS.setValueChangeCallback(Callbacks::onRenderConfigChanged);
        Configs.Disable.DISABLE_FLUIDS.setValueChangeCallback(Callbacks::onRenderConfigChanged);

        // Per-type filters. Blocks/fluids are baked into chunk meshes, so their
        // changes must trigger a rebuild; the cache invalidation covers the rest.
        Configs.Filter.ENTITY_MODE.setValueChangeCallback(Callbacks::onFilterConfigChanged);
        Configs.Filter.BLOCK_MODE.setValueChangeCallback(Callbacks::onFilterConfigChanged);
        Configs.Filter.FLUID_MODE.setValueChangeCallback(Callbacks::onFilterConfigChanged);
        Configs.Filter.BLOCK_ENTITY_MODE.setValueChangeCallback(Callbacks::onFilterConfigChanged);
        Configs.Filter.PARTICLE_MODE.setValueChangeCallback(Callbacks::onFilterConfigChanged);
        Configs.Filter.FILTERED_ENTITIES.setValueChangeCallback(Callbacks::onFilterConfigChanged);
        Configs.Filter.FILTERED_BLOCKS.setValueChangeCallback(Callbacks::onFilterConfigChanged);
        Configs.Filter.FILTERED_FLUIDS.setValueChangeCallback(Callbacks::onFilterConfigChanged);
        Configs.Filter.FILTERED_BLOCK_ENTITIES.setValueChangeCallback(Callbacks::onFilterConfigChanged);
        Configs.Filter.FILTERED_PARTICLES.setValueChangeCallback(Callbacks::onFilterConfigChanged);
    }

    private static void onFilterConfigChanged(IConfigBase config) {
        FilterRules.invalidateCaches();
        LOGGER.info("[ReignRender] filter config '{}' changed to: {}", config.getName(), describeConfigValue(config));
        onRenderConfigChanged(config);
    }

    private static void onRenderConfigChanged(IConfigBase config) {
        LOGGER.info("[ReignRender] render config '{}' changed to: {}", config.getName(), describeConfigValue(config));

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.worldRenderer == null || mc.world == null || mc.player == null) {
            return;
        }

        // Reschedule all chunks within the render distance for a mesh rebuild.
        // Unlike reload() (which clears the built chunk storage and causes a
        // visible flicker), scheduleChunkRenders(...) only re-bakes the loaded
        // chunk meshes, so the fluid toggle takes effect without flashing.
        int distance = mc.options.getViewDistance().getValue();
        ChunkPos center = mc.player.getChunkPos();
        int minX = center.x - distance;
        int maxX = center.x + distance;
        int minZ = center.z - distance;
        int maxZ = center.z + distance;
        int minY = ChunkSectionPos.getSectionCoord(mc.world.getDimension().minY());
        int maxY = ChunkSectionPos.getSectionCoord(mc.world.getDimension().minY() + mc.world.getDimension().height() - 1);

        mc.worldRenderer.scheduleChunkRenders(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Renders the current value of a config option into a readable string,
     * so config changes can be logged for debugging. String lists show all
     * entries, mode options show the active mode name.
     */
    private static String describeConfigValue(IConfigBase config) {
        if (config instanceof ConfigStringList) {
            return String.join(", ", ((ConfigStringList) config).getStrings());
        }

        if (config instanceof ConfigOptionValues) {
            Object value = ((ConfigOptionValues<?>) config).getOptionValue();

            if (value instanceof BaseOptionListConfigValue) {
                return ((BaseOptionListConfigValue) value).getName();
            }

            return String.valueOf(value);
        }

        return String.valueOf(config.getAsJsonElement());
    }

    private static class KeyCallbackOpenConfigGui implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            GuiBase.openGui(new GuiConfigs());
            return true;
        }
    }
}
