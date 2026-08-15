package com.wdylyh.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.IHotkeyTogglable;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigOptionValues;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.config.value.BaseOptionListConfigValue;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class Configs implements IConfigHandler {

    private static final String CONFIG_FILE_NAME = "reignrender.json";
    private static final String DISABLE_KEY = "reignrender.config.disable";
    private static final String GENERIC_KEY = "reignrender.config.generic";
    private static final String HOTKEY_KEY = "reignrender.config.hotkeys";
    private static final String FILTER_KEY = "reignrender.config.filter";

    private static final List<IConfigBase> FILTER_MODES = List.of(
            Filter.ENTITY_MODE, Filter.BLOCK_MODE, Filter.FLUID_MODE,
            Filter.BLOCK_ENTITY_MODE, Filter.PARTICLE_MODE,
            Filter.ARMOR_MODE, Filter.FOG_MODE);

    public static class Disable {
        public static final ConfigBooleanHotkeyed DISABLE_PARTICLES = new ConfigBooleanHotkeyed("disableParticles", false, "").apply(DISABLE_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_ENTITIES = new ConfigBooleanHotkeyed("disableEntities", false, "").apply(DISABLE_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_BLOCKS = new ConfigBooleanHotkeyed("disableBlocks", false, "").apply(DISABLE_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_FLUIDS = new ConfigBooleanHotkeyed("disableFluids", false, "").apply(DISABLE_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_BLOCK_ENTITIES = new ConfigBooleanHotkeyed("disableBlockEntities", false, "").apply(DISABLE_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_FALLING_BLOCKS = new ConfigBooleanHotkeyed("disableFallingBlocks", false, "").apply(DISABLE_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_ARMOR = new ConfigBooleanHotkeyed("disableArmor", false, "").apply(DISABLE_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_FOG = new ConfigBooleanHotkeyed("disableFog", false, "").apply(DISABLE_KEY);

        public static final ImmutableList<@NotNull IHotkeyTogglable> OPTIONS = ImmutableList.of(
                DISABLE_PARTICLES,
                DISABLE_ENTITIES,
                DISABLE_BLOCKS,
                DISABLE_FLUIDS,
                DISABLE_BLOCK_ENTITIES,
                DISABLE_FALLING_BLOCKS,
                DISABLE_ARMOR,
                DISABLE_FOG
        );
    }

    public static class Generic {
        public static final ConfigBoolean KEEP_SIGN_TEXT = new ConfigBoolean("keepSignText", true, "").apply(GENERIC_KEY);

        /**
         * Per-type filter modes (OFF / BLACKLIST / WHITELIST) for every render
         * category. They are displayed on the Generic tab, while the actual
         * filter lists stay on the Filter tab.
         */
        public static final ImmutableList<@NotNull IConfigBase> OPTIONS = ImmutableList.of(
                KEEP_SIGN_TEXT,
                Filter.ENTITY_MODE,
                Filter.BLOCK_MODE,
                Filter.FLUID_MODE,
                Filter.BLOCK_ENTITY_MODE,
                Filter.PARTICLE_MODE,
                Filter.ARMOR_MODE,
                Filter.FOG_MODE
        );
    }

    public static class Hotkeys {
        public static final ConfigHotkey OPEN_CONFIG_GUI = new ConfigHotkey("openConfigGui", "X,W").apply(HOTKEY_KEY);

        public static final ImmutableList<@NotNull IHotkey> OPTIONS = ImmutableList.of(
                OPEN_CONFIG_GUI
        );
    }

    /**
     * Per-type blacklist/whitelist render filtering.
     *
     * Each render category (entities, blocks, fluids, block entities, particles)
     * has its own OFF / BLACKLIST / WHITELIST mode switch plus a registry id list.
     * BLACKLIST hides the listed ids, WHITELIST only shows the listed ids.
     * The string lists store registry ids such as "minecraft:creeper" or
     * "minecraft:chest".
     */
    public static class Filter {
        public static final BaseOptionListConfigValue MODE_OFF = new BaseOptionListConfigValue("off", FILTER_KEY + ".mode.off");
        public static final BaseOptionListConfigValue MODE_BLACKLIST = new BaseOptionListConfigValue("blacklist", FILTER_KEY + ".mode.blacklist");
        public static final BaseOptionListConfigValue MODE_WHITELIST = new BaseOptionListConfigValue("whitelist", FILTER_KEY + ".mode.whitelist");

        public static final ConfigOptionValues<BaseOptionListConfigValue> ENTITY_MODE = new ConfigOptionValues<>(
                "entityMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(FILTER_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> BLOCK_MODE = new ConfigOptionValues<>(
                "blockMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(FILTER_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> FLUID_MODE = new ConfigOptionValues<>(
                "fluidMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(FILTER_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> BLOCK_ENTITY_MODE = new ConfigOptionValues<>(
                "blockEntityMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(FILTER_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> PARTICLE_MODE = new ConfigOptionValues<>(
                "particleMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(FILTER_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> ARMOR_MODE = new ConfigOptionValues<>(
                "armorMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(FILTER_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> FOG_MODE = new ConfigOptionValues<>(
                "fogMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(FILTER_KEY);

        public static final ConfigStringList FILTERED_ENTITIES = new ConfigStringList("filteredEntities", ImmutableList.of()).apply(FILTER_KEY);
        public static final ConfigStringList FILTERED_BLOCKS = new ConfigStringList("filteredBlocks", ImmutableList.of()).apply(FILTER_KEY);
        public static final ConfigStringList FILTERED_FLUIDS = new ConfigStringList("filteredFluids", ImmutableList.of()).apply(FILTER_KEY);
        public static final ConfigStringList FILTERED_BLOCK_ENTITIES = new ConfigStringList("filteredBlockEntities", ImmutableList.of()).apply(FILTER_KEY);
        public static final ConfigStringList FILTERED_PARTICLES = new ConfigStringList("filteredParticles", ImmutableList.of()).apply(FILTER_KEY);
        public static final ConfigStringList FILTERED_ARMOR = new ConfigStringList("filteredArmor", ImmutableList.of()).apply(FILTER_KEY);
        public static final ConfigStringList FILTERED_FOGS = new ConfigStringList("filteredFogs", ImmutableList.of()).apply(FILTER_KEY);

        /**
         * Block entity registry ids that support independent render disabling.
         * These ids also exist as blocks in the block registry, so they are
         * excluded from the block filter list to avoid duplicate management.
         */
        public static final Set<String> BLOCK_ENTITY_TYPE_IDS = Set.of(
                "minecraft:banner",
                "minecraft:bell",
                "minecraft:chest",
                "minecraft:copper_chest",
                "minecraft:copper_golem_statue",
                "minecraft:decorated_pot",
                "minecraft:ender_chest",
                "minecraft:hanging_sign",
                "minecraft:shulker_box",
                "minecraft:sign",
                "minecraft:skull");

        /**
         * The filter lists stay on the Filter tab; the mode switches moved to
         * the Generic tab (see {@link Configs.Generic}).
         */
        public static final ImmutableList<@NotNull IConfigBase> OPTIONS = ImmutableList.of(
                FILTERED_ENTITIES,
                FILTERED_BLOCKS,
                FILTERED_FLUIDS,
                FILTERED_BLOCK_ENTITIES,
                FILTERED_PARTICLES,
                FILTERED_ARMOR,
                FILTERED_FOGS
        );
    }

    @Override
    public void load() {
        File configFile = getConfigFile();

        if (configFile.exists() && configFile.isFile()) {
            JsonElement jsonElement = JsonUtils.parseJsonFile(configFile);

            if (jsonElement != null && jsonElement.isJsonObject()) {
                JsonObject root = jsonElement.getAsJsonObject();
                migrateFilterModes(root);
                ConfigUtils.readConfigBase(root, "Disable", Disable.OPTIONS);
                ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
                ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.OPTIONS);
                ConfigUtils.readConfigBase(root, "Filter", Filter.OPTIONS);
            }
        }

        cleanUpBlockFilterList();
        expandBlockEntityFilterList();
    }

    /**
     * The filter mode switches moved from the "Filter" config section to the
     * "Generic" section. Existing configs still store the mode values under
     * "Filter", so they are copied over before the sections are read back.
     */
    private static void migrateFilterModes(JsonObject root) {
        JsonObject generic = root.getAsJsonObject("Generic");
        JsonObject filter = root.getAsJsonObject("Filter");

        if (filter == null) {
            return;
        }

        if (generic == null) {
            generic = new JsonObject();
            root.add("Generic", generic);
        }

        for (IConfigBase mode : FILTER_MODES) {
            if (filter.has(mode.getName()) && !generic.has(mode.getName())) {
                generic.add(mode.getName(), filter.get(mode.getName()));
            }
        }
    }

    /**
     * The block filter list must not contain blocks backed by the block entity
     * types managed by the block entity filter list (copper chests, signs,
     * shulker boxes, ...), as those are rendered via their block entity.
     * Removes any leftovers from older configs or accidental picks.
     */
    private static void cleanUpBlockFilterList() {
        FilterRules.cleanUpBlockFilter();
    }

    /**
     * Migrates legacy type-level block entity ids in the config to concrete
     * variant block ids (per-variant filtering).
     */
    private static void expandBlockEntityFilterList() {
        FilterRules.expandBlockEntityFilter();
    }

    @Override
    public void save() {
        Path configDirPath = FileUtils.getConfigDirectory();
        File configDir = configDirPath.toFile();

        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        JsonObject root = new JsonObject();

        ConfigUtils.writeConfigBase(root, "Disable", Disable.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Hotkeys", Hotkeys.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Filter", Filter.OPTIONS);

        JsonUtils.writeJsonToFile(root, new File(configDir, CONFIG_FILE_NAME));
    }

    private static File getConfigFile() {
        File configDir = FileUtils.getConfigDirectory().toFile();
        return new File(configDir, CONFIG_FILE_NAME);
    }
}