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
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionValues;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.config.value.BaseOptionListConfigValue;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Cfg implements IConfigHandler {

    private static final String CFG_FILE = "reignrender.json";
    private static final String OFF_KEY = "reignrender.config.disable";
    private static final String G_KEY = "reignrender.config.generic";
    private static final String HK_KEY = "reignrender.config.hotkeys";
    private static final String F_KEY = "reignrender.config.filter";

    /**
     * Set by {@link #migrateFogs} once the default fog identities have
     * been merged into an older config, then persisted into the "Filter"
     * section on the next save so the merge runs only once (a user who removes
     * an entry afterwards keeps it removed).
     */
    private static final String FOGS_MIG_KEY = "filteredFogsMigratedV2";
    private static boolean fogsMig = false;

    /**
     * Marks the second fog migration (adding the pumpkin overlay identity to
     * the default fog list). Separate from {@link #FOGS_MIG_KEY}
     * so an existing (already-V2) config still gets the new entry added once
     * without re-adding every removed default entry.
     */
    private static final String FOGS_PUMPKIN_MIG_KEY = "filteredFogsMigratedV3";
    private static boolean fogsPumpkinMig = false;

    private static final List<IConfigBase> FILTER_MODES = List.of(
            F.ENTITY_MODE, F.BLOCK_MODE, F.FLUID_MODE,
            F.BLOCK_ENTITY_MODE, F.PARTICLE_MODE,
            F.ARMOR_MODE, F.FOG_MODE,
            F.NAME_TAG_MODE, F.PLAYER_MODE);

    public static class Off {
        public static final ConfigBooleanHotkeyed DISABLE_PARTICLES = new ConfigBooleanHotkeyed("disableParticles", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_ENTITIES = new ConfigBooleanHotkeyed("disableEntities", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_BLOCKS = new ConfigBooleanHotkeyed("disableBlocks", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_FLUIDS = new ConfigBooleanHotkeyed("disableFluids", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_BLOCK_ENTITIES = new ConfigBooleanHotkeyed("disableBlockEntities", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_FALLING_BLOCKS = new ConfigBooleanHotkeyed("disableFallingBlocks", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_ARMOR = new ConfigBooleanHotkeyed("disableArmor", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_HELD_ITEMS = new ConfigBooleanHotkeyed("disableHeldItems", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_ELYTRA = new ConfigBooleanHotkeyed("disableElytra", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_FOG = new ConfigBooleanHotkeyed("disableFog", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed HIDE_SELF = new ConfigBooleanHotkeyed("hideSelf", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed HIDE_OTHER_PLAYERS = new ConfigBooleanHotkeyed("hideOtherPlayers", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_NAME_TAGS = new ConfigBooleanHotkeyed("disableNameTags", false, "").apply(OFF_KEY);

        // ==================== 环境控制 (Environment) ====================
        public static final ConfigBooleanHotkeyed DISABLE_SKY = new ConfigBooleanHotkeyed("disableSky", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_CLOUDS = new ConfigBooleanHotkeyed("disableClouds", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_WEATHER = new ConfigBooleanHotkeyed("disableWeather", false, "").apply(OFF_KEY);

        // ==================== 实体/特效控制 (Entity & effect) ====================
        public static final ConfigBooleanHotkeyed DISABLE_ITEM_ENTITIES = new ConfigBooleanHotkeyed("disableItemEntities", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_FIRE_ANIMATION = new ConfigBooleanHotkeyed("disableFireAnimation", false, "").apply(OFF_KEY);

        // ==================== HUD/渲染控制 (HUD & render) ====================
        public static final ConfigBooleanHotkeyed DISABLE_HUD_ELEMENTS = new ConfigBooleanHotkeyed("disableHudElements", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_BLOCK_OUTLINE = new ConfigBooleanHotkeyed("disableBlockOutline", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_GLINT = new ConfigBooleanHotkeyed("disableGlint", false, "").apply(OFF_KEY);

        public static final ImmutableList<@NotNull IHotkeyTogglable> OPTIONS = ImmutableList.of(
                DISABLE_PARTICLES,
                DISABLE_ENTITIES,
                DISABLE_BLOCKS,
                DISABLE_FLUIDS,
                DISABLE_BLOCK_ENTITIES,
                DISABLE_FALLING_BLOCKS,
                DISABLE_ARMOR,
                DISABLE_HELD_ITEMS,
                DISABLE_ELYTRA,
                DISABLE_FOG,
                HIDE_SELF,
                HIDE_OTHER_PLAYERS,
                DISABLE_NAME_TAGS,
                DISABLE_SKY,
                DISABLE_CLOUDS,
                DISABLE_WEATHER,
                DISABLE_ITEM_ENTITIES,
                DISABLE_FIRE_ANIMATION,
                DISABLE_HUD_ELEMENTS,
                DISABLE_BLOCK_OUTLINE,
                DISABLE_GLINT
        );
    }

    public static class G {
        public static final ConfigBoolean KEEP_SIGN_TEXT = new ConfigBoolean("keepSignText", true, "").apply(G_KEY);

        /**
         * What the reveal hotkey does while held.
         * Release ("解除"): holding the hotkey temporarily bypasses every
         * render filter (reveal mode). Enable ("开启"): holding the hotkey
         * temporarily hides everything, regardless of the individual filters
         * (force-hide mode).
         */
        public static final BaseOptionListConfigValue HOTKEY_MODE_RELEASE = new BaseOptionListConfigValue("release", G_KEY + ".hotkeyMode.release");
        public static final BaseOptionListConfigValue HOTKEY_MODE_ENABLE = new BaseOptionListConfigValue("enable", G_KEY + ".hotkeyMode.enable");

        public static final ConfigOptionValues<BaseOptionListConfigValue> REVEAL_HOTKEY_MODE = new ConfigOptionValues<>(
                "revealHotkeyMode", HOTKEY_MODE_RELEASE, ImmutableList.of(HOTKEY_MODE_RELEASE, HOTKEY_MODE_ENABLE)).apply(G_KEY);

        /**
         * Render categories the reveal hotkey can affect. Only the categories
         * present in this list are bypassed (release mode) or force-hidden
         * (enable mode) while the hotkey is held.
         */
        public static final ConfigStringList REVEAL_AFFECTED_TYPES = new ConfigStringList(
                "revealAffectedTypes", ImmutableList.copyOf(FR.ALL_TYPES)).apply(G_KEY);

        /**
         * When enabled, a small overlay in the top-left corner lists every
         * filter that is currently active (hidden while the F3 debug screen
         * is shown).
         */
        public static final ConfigBoolean SHOW_HUD_STATE = new ConfigBoolean("showHudState", false, "").apply(G_KEY);

        /**
         * Entities farther than this many blocks from the camera are not
         * rendered. -1 disables the distance limit.
         */
        public static final ConfigInteger ENTITY_RENDER_DISTANCE = new ConfigInteger("entityRenderDistance", -1, -1, 512, "").apply(G_KEY);

        /**
         * Maximum number of entities rendered per frame. -1 disables the limit.
         * Falling blocks are entities too, so they are covered by this cap.
         */
        public static final ConfigInteger MAX_ENTITIES = new ConfigInteger("maxEntities", -1, -1, 10000, "").apply(G_KEY);

        /**
         * Maximum number of particles allowed at once. -1 disables the limit.
         */
        public static final ConfigInteger MAX_PARTICLES = new ConfigInteger("maxParticles", -1, -1, 10000, "").apply(G_KEY);

        /**
         * Maximum number of blocks rendered per chunk section (16x16x16).
         * When a section holds more blocks than this, the excess blocks are
         * replaced with air in the mesh so they are not rendered. -1 disables
         * the limit.
         */
        public static final ConfigInteger MAX_BLOCKS_PER_CHUNK = new ConfigInteger("maxBlocksPerChunk", -1, -1, 4096, "").apply(G_KEY);

        /**
         * How far (in blocks) clouds are rendered around the camera. -1
         * disables the distance limit (renders every cloud).
         */
        public static final ConfigInteger CLOUD_RENDER_DISTANCE = new ConfigInteger("cloudRenderDistance", -1, -1, 512, "").apply(G_KEY);

        /**
         * Master switch for the universal render replacement system. When
         * enabled, every id in the per-category replacement lists swaps the
         * rendering of the source id for the target id.
         */
        public static final ConfigBoolean REPLACE_ENABLED = new ConfigBoolean("replaceEnabled", false, "").apply(G_KEY);

        /**
         * How the per-type filter lists are edited on the Filter tab.
         * View selection opens the icon grid picker; manual input opens a
         * text box where ids are typed separated by semicolons.
         */
        public static final BaseOptionListConfigValue INPUT_MODE_VIEW = new BaseOptionListConfigValue("view", G_KEY + ".inputMode.view");
        public static final BaseOptionListConfigValue INPUT_MODE_MANUAL = new BaseOptionListConfigValue("manual", G_KEY + ".inputMode.manual");

        public static final ConfigOptionValues<BaseOptionListConfigValue> FILTER_INPUT_MODE = new ConfigOptionValues<>(
                "filterInputMode", INPUT_MODE_VIEW, ImmutableList.of(INPUT_MODE_VIEW, INPUT_MODE_MANUAL)).apply(G_KEY);

        /**
         * Per-type filter modes (OFF / BLACKLIST / WHITELIST) for every render
         * category. They are displayed on the Generic tab, while the actual
         * filter lists stay on the Filter tab.
         */
        public static final ImmutableList<@NotNull IConfigBase> OPTIONS = ImmutableList.of(
                KEEP_SIGN_TEXT,
                REVEAL_HOTKEY_MODE,
                REVEAL_AFFECTED_TYPES,
                SHOW_HUD_STATE,
                ENTITY_RENDER_DISTANCE,
                MAX_ENTITIES,
                MAX_PARTICLES,
                MAX_BLOCKS_PER_CHUNK,
                CLOUD_RENDER_DISTANCE,
                REPLACE_ENABLED,
                F.ENTITY_MODE,
                F.BLOCK_MODE,
                F.FLUID_MODE,
                F.BLOCK_ENTITY_MODE,
                F.PARTICLE_MODE,
                F.ARMOR_MODE,
                F.FOG_MODE,
                F.NAME_TAG_MODE,
                F.PLAYER_MODE,
                FILTER_INPUT_MODE
        );
    }

    public static class HK {
        public static final ConfigHotkey OPEN_CONFIG_GUI = new ConfigHotkey("openConfigGui", "X,R").apply(HK_KEY);

        /**
         * While this key is held, the behavior set by REVEAL_HOTKEY_MODE is
         * active: reveal mode bypasses every render filter (including the
         * baked-in block/fluid filters, which are rebuilt on press/release),
         * force-hide mode hides everything. Defaults to H; an empty binding
         * never triggers. Registered with INGAME_BOTH so both the press and
         * the release fire the callback that rebuilds the chunk meshes.
         */
        public static final ConfigHotkey REVEAL_HOTKEY = new ConfigHotkey("revealHotkey", "H", KeybindSettings.INGAME_BOTH, "").apply(HK_KEY);

        /**
         * Press this key while looking at an entity to add (or remove) its
         * registry id to/from the entity filter list; sneak while pressing
         * picks the biome the player is standing in into the fog filter list
         * instead. The list is edited directly, so the change applies
         * instantly. Registered with PRESS_ALLOWEXTRA so the key still
         * triggers while the sneak key (shift) is held down.
         */
        public static final ConfigHotkey PICK_ENTITY_HOTKEY = new ConfigHotkey("pickEntityHotkey", "", KeybindSettings.PRESS_ALLOWEXTRA).apply(HK_KEY);

        public static final ImmutableList<@NotNull IHotkey> OPTIONS = ImmutableList.of(
                OPEN_CONFIG_GUI,
                REVEAL_HOTKEY,
                PICK_ENTITY_HOTKEY
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
    public static class F {
        public static final BaseOptionListConfigValue MODE_OFF = new BaseOptionListConfigValue("off", F_KEY + ".mode.off");
        public static final BaseOptionListConfigValue MODE_BLACKLIST = new BaseOptionListConfigValue("blacklist", F_KEY + ".mode.blacklist");
        public static final BaseOptionListConfigValue MODE_WHITELIST = new BaseOptionListConfigValue("whitelist", F_KEY + ".mode.whitelist");

        public static final ConfigOptionValues<BaseOptionListConfigValue> ENTITY_MODE = new ConfigOptionValues<>(
                "entityMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(F_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> BLOCK_MODE = new ConfigOptionValues<>(
                "blockMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(F_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> FLUID_MODE = new ConfigOptionValues<>(
                "fluidMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(F_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> BLOCK_ENTITY_MODE = new ConfigOptionValues<>(
                "blockEntityMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(F_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> PARTICLE_MODE = new ConfigOptionValues<>(
                "particleMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(F_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> ARMOR_MODE = new ConfigOptionValues<>(
                "armorMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(F_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> FOG_MODE = new ConfigOptionValues<>(
                "fogMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(F_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> NAME_TAG_MODE = new ConfigOptionValues<>(
                "nameTagMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(F_KEY);
        public static final ConfigOptionValues<BaseOptionListConfigValue> PLAYER_MODE = new ConfigOptionValues<>(
                "playerMode", MODE_OFF, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(F_KEY);

        public static final ConfigStringList FILTERED_ENTITIES = new ConfigStringList("filteredEntities", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList FILTERED_BLOCKS = new ConfigStringList("filteredBlocks", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList FILTERED_FLUIDS = new ConfigStringList("filteredFluids", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList FILTERED_BLOCK_ENTITIES = new ConfigStringList("filteredBlockEntities", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList FILTERED_PARTICLES = new ConfigStringList("filteredParticles", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList FILTERED_ARMOR = new ConfigStringList("filteredArmor", ImmutableList.of()).apply(F_KEY);
        /**
         * Every vanilla biome id of the target Minecraft version plus the four
         * status-effect fog identities ({@link FR#FOG_EFFECT_BLINDNESS}
         * etc.). With the fog filter in BLACKLIST mode this hides the fog of all
         * biomes and effect fogs by default; remove an id to stop filtering it.
         */
        public static final ConfigStringList FILTERED_FOGS = new ConfigStringList("filteredFogs", ImmutableList.of(
                "minecraft:badlands", "minecraft:bamboo_jungle", "minecraft:basalt_deltas", "minecraft:beach",
                "minecraft:birch_forest", "minecraft:cherry_grove", "minecraft:cold_ocean", "minecraft:crimson_forest",
                "minecraft:dark_forest", "minecraft:deep_cold_ocean", "minecraft:deep_dark", "minecraft:deep_frozen_ocean",
                "minecraft:deep_lukewarm_ocean", "minecraft:deep_ocean", "minecraft:desert", "minecraft:dripstone_caves",
                "minecraft:end_barrens", "minecraft:end_highlands", "minecraft:end_midlands", "minecraft:eroded_badlands",
                "minecraft:flower_forest", "minecraft:forest", "minecraft:frozen_ocean", "minecraft:frozen_peaks",
                "minecraft:frozen_river", "minecraft:grove", "minecraft:ice_spikes", "minecraft:jagged_peaks",
                "minecraft:jungle", "minecraft:lukewarm_ocean", "minecraft:lush_caves", "minecraft:mangrove_swamp",
                "minecraft:meadow", "minecraft:mushroom_fields", "minecraft:nether_wastes", "minecraft:ocean",
                "minecraft:old_growth_birch_forest", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
                "minecraft:pale_garden", "minecraft:plains", "minecraft:river", "minecraft:savanna",
                "minecraft:savanna_plateau", "minecraft:small_end_islands", "minecraft:snowy_beach",
                "minecraft:snowy_plains", "minecraft:snowy_slopes", "minecraft:snowy_taiga", "minecraft:soul_sand_valley",
                "minecraft:sparse_jungle", "minecraft:stony_peaks", "minecraft:stony_shore", "minecraft:sunflower_plains",
                "minecraft:swamp", "minecraft:taiga", "minecraft:the_end", "minecraft:the_void", "minecraft:warm_ocean",
                "minecraft:warped_forest", "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills",
                "minecraft:windswept_hills", "minecraft:windswept_savanna", "minecraft:wooded_badlands",
                FR.FOG_EFFECT_BLINDNESS, FR.FOG_EFFECT_DARKNESS,
                FR.FOG_EFFECT_WITHER, FR.FOG_EFFECT_NIGHT_VISION,
                FR.FOG_EFFECT_PUMPKIN
        )).apply(F_KEY);

        // Name based filters (semicolon separated names in a single text box).
        public static final ConfigString FILTERED_NAME_TAGS = new ConfigString("filteredNameTags", "").apply(F_KEY);
        public static final ConfigString FILTERED_PLAYERS = new ConfigString("filteredPlayers", "").apply(F_KEY);

        /**
         * HUD element ids hidden while the "Disable HUD Elements" toggle is on
         * (Bossbar, Hotbar, Chat, StatusEffects, Crosshair, ...). One id per
         * list entry; the toggle alone hides nothing, only the ids listed here.
         * Supports the same view/manual input modes as the filtered lists.
         */
        public static final ConfigStringList HIDDEN_HUD_ELEMENTS = new ConfigStringList(
                "hiddenHudElements", ImmutableList.of()).apply(F_KEY);

        /**
         * Universal render replacement lists. Each entry is "source=target"
         * (e.g. "minecraft:zombie=minecraft:skeleton"); while the
         * {@link Cfg.G.REPLACE_ENABLED} master switch is on, the
         * source id renders as the target id. One list per render category.
         */
        public static final ConfigStringList REPLACE_PARTICLES = new ConfigStringList("replaceParticles", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList REPLACE_BLOCKS = new ConfigStringList("replaceBlocks", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList REPLACE_ENTITIES = new ConfigStringList("replaceEntities", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList REPLACE_FOGS = new ConfigStringList("replaceFogs", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList REPLACE_ARMOR = new ConfigStringList("replaceArmor", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList REPLACE_NAME_TAGS = new ConfigStringList("replaceNameTags", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList REPLACE_PLAYER_NAMES = new ConfigStringList("replacePlayerNames", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList REPLACE_FLUIDS = new ConfigStringList("replaceFluids", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList REPLACE_BLOCK_ENTITIES = new ConfigStringList("replaceBlockEntities", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList REPLACE_FALLING_BLOCKS = new ConfigStringList("replaceFallingBlocks", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList REPLACE_ITEM_ENTITIES = new ConfigStringList("replaceItemEntities", ImmutableList.of()).apply(F_KEY);

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
         * the Generic tab (see {@link Cfg.G}).
         */
        public static final ImmutableList<@NotNull IConfigBase> OPTIONS = ImmutableList.of(
                FILTERED_ENTITIES,
                FILTERED_BLOCKS,
                FILTERED_FLUIDS,
                FILTERED_BLOCK_ENTITIES,
                FILTERED_PARTICLES,
                FILTERED_ARMOR,
                FILTERED_FOGS,
                FILTERED_NAME_TAGS,
                FILTERED_PLAYERS,
                HIDDEN_HUD_ELEMENTS,
                REPLACE_PARTICLES,
                REPLACE_BLOCKS,
                REPLACE_ENTITIES,
                REPLACE_FOGS,
                REPLACE_ARMOR,
                REPLACE_NAME_TAGS,
                REPLACE_PLAYER_NAMES,
                REPLACE_FLUIDS,
                REPLACE_BLOCK_ENTITIES,
                REPLACE_FALLING_BLOCKS,
                REPLACE_ITEM_ENTITIES
        );
    }

    @Override
    public void load() {
        File configFile = cfgFile();

        if (configFile.exists() && configFile.isFile()) {
            JsonElement jsonElement = JsonUtils.parseJsonFile(configFile);

            if (jsonElement != null && jsonElement.isJsonObject()) {
                JsonObject root = jsonElement.getAsJsonObject();
                migrateModes(root);
                migrateRevealMode(root);
                ConfigUtils.readConfigBase(root, "Disable", Off.OPTIONS);
                ConfigUtils.readConfigBase(root, "Generic", G.OPTIONS);
                ConfigUtils.readConfigBase(root, "Hotkeys", HK.OPTIONS);
                ConfigUtils.readConfigBase(root, "Filter", F.OPTIONS);
                migrateFogs(root);
                migrateFogsPumpkin(root);
            }
        }

        cleanBlockList();
        expandBlockEntityList();
    }

    /**
     * The filter mode switches moved from the "Filter" config section to the
     * "Generic" section. Existing configs still store the mode values under
     * "Filter", so they are copied over before the sections are read back.
     */
    private static void migrateModes(JsonObject root) {
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
     * The reveal hotkey mode used to be a boolean ("true" = reveal/release,
     * "false" = force-hide). It is now a named option list value. Old configs
     * still store a JSON boolean, which cannot be parsed by the option list
     * reader (it would silently fall back to the first value), so it is
     * converted to the new string form before the sections are read back.
     */
    private static void migrateRevealMode(JsonObject root) {
        JsonObject generic = root.getAsJsonObject("Generic");

        if (generic == null || !generic.has("revealHotkeyMode")) {
            return;
        }

        JsonElement element = generic.get("revealHotkeyMode");

        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            generic.addProperty("revealHotkeyMode", element.getAsBoolean() ? "release" : "enable");
        }
    }

    /**
     * The fog filter list grew from a small default to every vanilla biome plus
     * the status-effect fogs (blindness, darkness, wither, night vision). Older
     * configs still store the old (short) list, so the missing default entries
     * are merged into the loaded list while the user's own entries are kept.
     * Runs only once per config: the marker written on the next save (see
     * {@link #FOGS_MIG_KEY}) prevents re-adding entries the user
     * removed afterwards.
     */
    private static void migrateFogs(JsonObject root) {
        JsonObject filter = root.getAsJsonObject("Filter");

        if (filter == null || !filter.has("filteredFogs")) {
            // No stored list: the default list is already active.
            fogsMig = true;
            return;
        }

        if (filter.has(FOGS_MIG_KEY)) {
            // Migration is already done. Remember the fact even on this path:
            // save() only writes the JSON marker back when this static flag is
            // set, and without it the marker would be dropped on the next save,
            // causing a re-migration (and re-adding every default entry the
            // player removed) on the next load.
            fogsMig = true;
            return;
        }

        List<String> merged = new ArrayList<>(Cfg.F.FILTERED_FOGS.getStrings());
        Set<String> seen = new HashSet<>(merged);

        for (String id : Cfg.F.FILTERED_FOGS.getDefaultStrings()) {
            if (seen.add(id)) {
                merged.add(id);
            }
        }

        Cfg.F.FILTERED_FOGS.setStrings(merged);
        fogsMig = true;
    }

    /**
     * Adds the pumpkin overlay fog identity ({@link FR#FOG_EFFECT_PUMPKIN})
     * to the default fog list of an existing config that predates that entry.
     * Unlike the V2 migration this only ever inserts the single new identity
     * (never re-adding the full default list), so it is safe to run on an
     * already-V2 config. The marker is persisted on the next save so the entry
     * is only re-added if the player removes it and the list is genuinely
     * re-migrated.
     */
    private static void migrateFogsPumpkin(JsonObject root) {
        JsonObject filter = root.getAsJsonObject("Filter");

        if (filter == null || !filter.has("filteredFogs")) {
            // No stored list: the default list (already including pumpkin) is active.
            fogsPumpkinMig = true;
            return;
        }

        if (filter.has(FOGS_PUMPKIN_MIG_KEY)) {
            fogsPumpkinMig = true;
            return;
        }

        List<String> merged = new ArrayList<>(Cfg.F.FILTERED_FOGS.getStrings());
        if (!merged.contains(FR.FOG_EFFECT_PUMPKIN)) {
            merged.add(FR.FOG_EFFECT_PUMPKIN);
            Cfg.F.FILTERED_FOGS.setStrings(merged);
        }

        fogsPumpkinMig = true;
    }

    /**
     * The block filter list must not contain blocks backed by the block entity
     * types managed by the block entity filter list (copper chests, signs,
     * shulker boxes, ...), as those are rendered via their block entity.
     * Removes any leftovers from older configs or accidental picks.
     */
    private static void cleanBlockList() {
        FR.cleanUpBlockFilter();
    }

    /**
     * Migrates legacy type-level block entity ids in the config to concrete
     * variant block ids (per-variant filtering).
     */
    private static void expandBlockEntityList() {
        FR.expandBlockEntityFilter();
    }

    @Override
    public void save() {
        Path configDirPath = FileUtils.getConfigDirectory();
        File configDir = configDirPath.toFile();

        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        JsonObject root = new JsonObject();

        ConfigUtils.writeConfigBase(root, "Disable", Off.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Generic", G.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Hotkeys", HK.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Filter", F.OPTIONS);

        if (fogsMig) {
            JsonObject filter = JsonUtils.getNestedObject(root, "Filter", true);
            if (filter != null) {
                filter.addProperty(FOGS_MIG_KEY, true);
            }
        }

        if (fogsPumpkinMig) {
            JsonObject filter = JsonUtils.getNestedObject(root, "Filter", true);
            if (filter != null) {
                filter.addProperty(FOGS_PUMPKIN_MIG_KEY, true);
            }
        }

        JsonUtils.writeJsonToFile(root, new File(configDir, CFG_FILE));
    }

    private static File cfgFile() {
        File configDir = FileUtils.getConfigDirectory().toFile();
        return new File(configDir, CFG_FILE);
    }
}