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

public class RenderConfig implements IConfigHandler {

    private static final String CFG_FILE = "reignrender.json";
    private static final String OFF_KEY = "reignrender.config.disable";
    private static final String G_KEY = "reignrender.config.generic";
    private static final String HK_KEY = "reignrender.config.hotkeys";
    private static final String F_KEY = "reignrender.config.filter";
    private static final String FACE_KEY = "reignrender.config.face";

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
            Filters.ENTITY_MODE, Filters.BLOCK_MODE, Filters.FLUID_MODE,
            Filters.BLOCK_ENTITY_MODE, Filters.PARTICLE_MODE,
            Filters.ARMOR_MODE, Filters.FOG_MODE,
            Filters.NAME_TAG_MODE, Filters.PLAYER_MODE);

    public static class Toggles {
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

        // ==================== HUD/渲染控制 (HUD & render) ====================
        public static final ConfigBooleanHotkeyed DISABLE_HUD_ELEMENTS = new ConfigBooleanHotkeyed("disableHudElements", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_BLOCK_OUTLINE = new ConfigBooleanHotkeyed("disableBlockOutline", false, "").apply(OFF_KEY);
        public static final ConfigBooleanHotkeyed DISABLE_GLINT = new ConfigBooleanHotkeyed("disableGlint", false, "").apply(OFF_KEY);

        /**
         * Master switch for the face-mod ("贴图修改") feature. When on, the
         * generated ReignRender_FaceMod resource pack is enabled and every
         * edited texture applies; when off, the pack is disabled again and
         * everything renders with the original look.
         */
        public static final ImmutableList<@NotNull IHotkeyTogglable> OPTIONS = ImmutableList.of(
                // 实体与玩家
                DISABLE_PARTICLES,
                DISABLE_ENTITIES,
                DISABLE_FALLING_BLOCKS,
                HIDE_SELF,
                HIDE_OTHER_PLAYERS,
                DISABLE_NAME_TAGS,
                // 方块与物品
                DISABLE_BLOCKS,
                DISABLE_BLOCK_ENTITIES,
                DISABLE_FLUIDS,
                DISABLE_ITEM_ENTITIES,
                DISABLE_HELD_ITEMS,
                DISABLE_ARMOR,
                DISABLE_ELYTRA,
                // 环境
                DISABLE_FOG,
                DISABLE_SKY,
                DISABLE_CLOUDS,
                DISABLE_WEATHER,
                // 界面与效果
                DISABLE_HUD_ELEMENTS,
                DISABLE_BLOCK_OUTLINE,
                DISABLE_GLINT
        );
    }

    public static class General {
        public static final ConfigBoolean KEEP_SIGN_TEXT = new ConfigBoolean("keepSignText", true, "").apply(G_KEY);

        /**
         * Master switch of the face-mod feature. When on, the generated
         * ReignRender_FaceMod resource pack is enabled and every edited
         * texture applies; when off, the pack is disabled again and everything
         * renders with the original look.
         */
        public static final ConfigBooleanHotkeyed ENABLE_FACE_MOD = new ConfigBooleanHotkeyed("enableFaceMod", false, "").apply(G_KEY);

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
                "revealAffectedTypes", ImmutableList.copyOf(FilterEngine.ALL_TYPES)).apply(G_KEY);

        /**
         * Master switch for the universal render replacement system. When
         * enabled, every id in the per-category replacement lists swaps the
         * rendering of the source id for the target id.
         */
        public static final ConfigBooleanHotkeyed REPLACE_ENABLED = new ConfigBooleanHotkeyed("replaceEnabled", false, "").apply(G_KEY);

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
         * Mode of the coordinate filter (OFF / BLACKLIST / WHITELIST). The
         * coordinate entries live on the Filter tab. OFF ("关闭"): the whole
         * region of every entry is hidden, ignoring any attached ids.
         * WHITELIST ("白名单"): only the objects whose id is attached to the
         * entry are kept visible, everything else at that coordinate is hidden
         * (an entry without attached ids therefore hides everything there).
         * BLACKLIST ("黑名单"): only the objects whose id is attached to the
         * entry are hidden.
         */
        public static final ConfigOptionValues<BaseOptionListConfigValue> COORD_MODE = new ConfigOptionValues<>(
                "coordMode", Filters.MODE_OFF, ImmutableList.of(Filters.MODE_OFF, Filters.MODE_BLACKLIST, Filters.MODE_WHITELIST)).apply(G_KEY);

        /**
         * How the coordinate pick hotkey selects positions. Single ("单方块"):
         * the looked-at block position is stored. Box ("多方块"): the hotkey
         * stores corner 1 on the first press and corner 2 on the second press.
         */
        public static final BaseOptionListConfigValue COORD_PICK_SINGLE = new BaseOptionListConfigValue("single", G_KEY + ".coordPickMode.single");
        public static final BaseOptionListConfigValue COORD_PICK_BOX = new BaseOptionListConfigValue("box", G_KEY + ".coordPickMode.box");

        public static final ConfigOptionValues<BaseOptionListConfigValue> COORD_PICK_MODE = new ConfigOptionValues<>(
                "coordPickMode", COORD_PICK_SINGLE, ImmutableList.of(COORD_PICK_SINGLE, COORD_PICK_BOX)).apply(G_KEY);

        /**
         * Per-type filter modes (OFF / BLACKLIST / WHITELIST) for every render
         * category. They are displayed on the Generic tab, while the actual
         * filter lists stay on the Filter tab.
         */
        public static final ImmutableList<@NotNull IConfigBase> OPTIONS = ImmutableList.of(
                // 通用
                KEEP_SIGN_TEXT,
                FILTER_INPUT_MODE,
                // 面修改
                ENABLE_FACE_MOD,
                // 替换
                REPLACE_ENABLED,
                // 过滤模式
                Filters.ENTITY_MODE,
                Filters.BLOCK_MODE,
                Filters.FLUID_MODE,
                Filters.BLOCK_ENTITY_MODE,
                Filters.PARTICLE_MODE,
                Filters.ARMOR_MODE,
                Filters.FOG_MODE,
                Filters.NAME_TAG_MODE,
                Filters.PLAYER_MODE,
                // 坐标
                COORD_MODE,
                COORD_PICK_MODE,
                // 解除热键
                REVEAL_HOTKEY_MODE,
                REVEAL_AFFECTED_TYPES
        );
    }

    public static class Hotkeys {
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

        /**
         * Master switch for the coordinate filter. When on, the entries in
         * {@link RenderConfig.Filters#COORD_ENTRIES} hide everything at the listed
         * coordinates according to {@link RenderConfig.General#COORD_MODE} (OFF hides the
         * whole region, BLACKLIST/WHITELIST additionally match the optional
         * ids attached to each entry). Independent of the per-category
         * filters: the coordinates apply even when e.g. the entity filter is
         * off.
         */
        public static final ConfigBooleanHotkeyed TOGGLE_COORD_FILTER = new ConfigBooleanHotkeyed("toggleCoordFilter", false, "").apply(HK_KEY);

        /**
         * Master switch for the coordinate aware replacement system. The
         * region rules in {@link RenderConfig.Filters#COORD_REPLACE_ENTRIES} apply only
         * while this switch is on AND the global replacement master switch
         * {@link RenderConfig.General#REPLACE_ENABLED} is OFF — the regional
         * mechanism is mutually exclusive with the global one and only takes
         * over when the global replacement is turned off.
         */
        public static final ConfigBooleanHotkeyed TOGGLE_COORD_REPLACE = new ConfigBooleanHotkeyed("toggleCoordReplace", false, "").apply(HK_KEY);

        /**
         * Master switch for the region face-mod ("区域面修改"). The region
         * texture edits in {@link RenderConfig.Filters#REGION_FACE_ENTRIES} apply only
         * while this switch is on AND the global face modification
         * {@link RenderConfig.General#ENABLE_FACE_MOD} is OFF — the regional
         * mechanism is mutually exclusive with the global one and only takes
         * over when the global face mod is turned off.
         */
        public static final ConfigBooleanHotkeyed TOGGLE_REGION_FACE = new ConfigBooleanHotkeyed("toggleRegionFace", false, "").apply(HK_KEY);

        /**
         * Press while looking at a block to pick its position into the
         * coordinate filter list. With {@link RenderConfig.General#COORD_PICK_MODE} set to
         * "single" the looked-at position is stored directly; with "box" press
         * the hotkey once for corner 1 and again for corner 2 to store a
         * region.
         */
        public static final ConfigHotkey PICK_COORD_HOTKEY = new ConfigHotkey("pickCoordHotkey", "", KeybindSettings.PRESS_ALLOWEXTRA).apply(HK_KEY);

        /**
         * Master switch for the per-id render count limits. When on, the
         * entries in {@link RenderConfig.Filters#COUNT_LIMITS} cap how many
         * objects of each id are rendered per frame (entities) / spawned per
         * frame (particles). Independent of the per-category filters: the
         * caps apply even when e.g. the entity filter is off.
         */
        public static final ConfigBooleanHotkeyed TOGGLE_COUNT_LIMITS = new ConfigBooleanHotkeyed("toggleCountLimits", false, "").apply(HK_KEY);

        /**
         * Master switch for the per-id render distance limits. When on, the
         * entries in {@link RenderConfig.Filters#DISTANCE_LIMITS} hide
         * entities / particles whose distance to the camera exceeds their
         * per-id cap (in blocks). Independent of the per-category filters.
         */
        public static final ConfigBooleanHotkeyed TOGGLE_DISTANCE_LIMITS = new ConfigBooleanHotkeyed("toggleDistanceLimits", false, "").apply(HK_KEY);

        public static final ImmutableList<@NotNull IHotkey> OPTIONS = ImmutableList.of(
                // 界面
                OPEN_CONFIG_GUI,
                // 解除 / 选取
                REVEAL_HOTKEY,
                PICK_ENTITY_HOTKEY,
                PICK_COORD_HOTKEY,
                // 坐标
                TOGGLE_COORD_FILTER,
                TOGGLE_COORD_REPLACE,
                TOGGLE_REGION_FACE,
                // 限制
                TOGGLE_COUNT_LIMITS,
                TOGGLE_DISTANCE_LIMITS
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
    public static class Filters {
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
         * status-effect fog identities ({@link FilterEngine#FOG_EFFECT_BLINDNESS}
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
                FilterEngine.FOG_EFFECT_BLINDNESS, FilterEngine.FOG_EFFECT_DARKNESS,
                FilterEngine.FOG_EFFECT_WITHER, FilterEngine.FOG_EFFECT_NIGHT_VISION,
                FilterEngine.FOG_EFFECT_PUMPKIN
        )).apply(F_KEY);

        // Name based filters (semicolon separated names in a single text box).
        public static final ConfigString FILTERED_NAME_TAGS = new ConfigString("filteredNameTags", "").apply(F_KEY);
        public static final ConfigString FILTERED_PLAYERS = new ConfigString("filteredPlayers", "").apply(F_KEY);

        /**
         * Coordinate filter entries. Each entry is a coordinate target plus
         * optional additional ids (separated by semicolons):
         *   "x,y,z"                  a single block position
         *   "x1,y1,z1~x2,y2,z2"      the region between two corners
         *   "x,y,z;id;id"            coordinate target with attached ids
         * The ids are matched by registry id (blocks, entities, particles,
         * block entities, fluids, name tags/player names as text). Coordinates
         * are matched in every dimension. See {@link RenderConfig.General#COORD_MODE} for
         * how the mode uses the attached ids.
         */
        public static final ConfigStringList COORD_ENTRIES = new ConfigStringList("coordEntries", ImmutableList.of()).apply(F_KEY);

        /**
         * Coordinate aware render replacement entries. Like the coordinate
         * filter entries, each line starts with a coordinate target; instead
         * of attached ids it carries one or more "source=target" replacement
         * rules (the same syntax as the per-category replacement lists):
         *   "x,y,z;minecraft:stone=minecraft:glass"
         *   "x1,y1,z1~x2,y2,z2;minecraft:stone=minecraft:glass;minecraft:dirt=minecraft:sand"
         * A rule's source id decides its category the same way the attached
         * ids of the coordinate filter do (blocks, entities, particles,
         * fluids; ids that resolve to no registry match name tags / player
         * names as text). While the {@link RenderConfig.General.REPLACE_ENABLED} master
         * switch is on, a replacement found inside a matched region takes
         * precedence over the global per-category replacement lists.
         */
        public static final ConfigStringList COORD_REPLACE_ENTRIES = new ConfigStringList("coordReplaceEntries", ImmutableList.of()).apply(F_KEY);

        /**
         * Region face-mod ("区域面修改") entries. Each line starts with a
         * coordinate target plus optional attached ids (the same syntax as the
         * coordinate filter entries):
         *   "x,y,z"                  a single block position
         *   "x1,y1,z1~x2,y2,z2"      the region between two corners
         *   "x,y,z;id;id"            coordinate target with attached ids
         * While the {@link RenderConfig.Hotkeys#TOGGLE_REGION_FACE} master switch is on
         * (and the global face mod is off), the objects whose id is attached to
         * a matching entry — or every object, when the entry carries no ids —
         * render with the textures edited for them in the region face GUI
         * ({@link RegionFaceIndex} / {@link RegionFacePacks}). Covered
         * categories: blocks (incl. falling blocks), entities, particles and
         * items (dropped / held).
         */
        public static final ConfigStringList REGION_FACE_ENTRIES = new ConfigStringList("regionFaceEntries", ImmutableList.of()).apply(F_KEY);

        /**
         * HUD element ids hidden while the "Disable HUD Elements" toggle is on
         * (Bossbar, Hotbar, Chat, StatusEffects, Crosshair, ...). One id per
         * list entry; the toggle alone hides nothing, only the ids listed here.
         * Supports the same view/manual input modes as the filtered lists.
         */
        public static final ConfigStringList HIDDEN_HUD_ELEMENTS = new ConfigStringList(
                "hiddenHudElements", ImmutableList.of()).apply(F_KEY);

        /**
         * Per-id render count limits. Each entry is "id=number" (e.g.
         * "minecraft:creeper=20"); while the {@link RenderConfig.Hotkeys#TOGGLE_COUNT_LIMITS}
         * master switch is on, at most {@code number} objects of that id are
         * rendered per frame (entities) / spawned per frame (particles).
         * -1 disables the limit for that id. Independent of the per-category
         * filters: the caps apply even when e.g. the entity filter is off.
         */
        public static final ConfigStringList COUNT_LIMITS = new ConfigStringList("countLimits", ImmutableList.of()).apply(F_KEY);

        /**
         * Per-id render distance limits. Each entry is "id=blocks" (e.g.
         * "minecraft:creeper=32"); while the {@link RenderConfig.Hotkeys#TOGGLE_DISTANCE_LIMITS}
         * master switch is on, objects of that id farther than {@code blocks}
         * from the camera are hidden (entities in the entity render pass,
         * particles at spawn time). -1 disables the limit for that id.
         * Independent of the per-category filters.
         */
        public static final ConfigStringList DISTANCE_LIMITS = new ConfigStringList("distanceLimits", ImmutableList.of()).apply(F_KEY);

        /**
         * Universal render replacement lists. Each entry is "source=target"
         * (e.g. "minecraft:zombie=minecraft:skeleton"); while the
         * {@link RenderConfig.General.REPLACE_ENABLED} master switch is on, the
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
        public static final ConfigStringList REPLACE_HELD_ITEMS = new ConfigStringList("replaceHeldItems", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList REPLACE_HUD_ELEMENTS = new ConfigStringList("replaceHudElements", ImmutableList.of()).apply(F_KEY);

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
         * the Generic tab (see {@link RenderConfig.General}).
         */
        public static final ImmutableList<@NotNull IConfigBase> OPTIONS = ImmutableList.of(
                // 过滤名单
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
                // 坐标
                COORD_ENTRIES,
                COORD_REPLACE_ENTRIES,
                REGION_FACE_ENTRIES,
                // 限制
                COUNT_LIMITS,
                DISTANCE_LIMITS,
                // 替换列表
                REPLACE_PARTICLES,
                REPLACE_BLOCKS,
                REPLACE_FLUIDS,
                REPLACE_BLOCK_ENTITIES,
                REPLACE_ENTITIES,
                REPLACE_FALLING_BLOCKS,
                REPLACE_ITEM_ENTITIES,
                REPLACE_HELD_ITEMS,
                REPLACE_ARMOR,
                REPLACE_FOGS,
                REPLACE_NAME_TAGS,
                REPLACE_PLAYER_NAMES,
                REPLACE_HUD_ELEMENTS
        );
    }

    /**
     * Per-id texture modification lists ("面修改", the Face tab). Each list
     * holds the registry ids of one render category whose textures can be
     * edited in the pixel editor. The edits themselves are written as texture
     * files into the generated ReignRender_FaceMod resource pack (see
     * {@link FaceModPacks}); a list entry only records that the id has been
     * added to the face-mod feature, together with the index of its edited
     * texture paths ({@link FaceModIndex}).
     */
    public static class Face {
        public static final ConfigStringList FACE_PARTICLES = new ConfigStringList("faceParticles", ImmutableList.of()).apply(FACE_KEY);
        public static final ConfigStringList FACE_ENTITIES = new ConfigStringList("faceEntities", ImmutableList.of()).apply(FACE_KEY);
        public static final ConfigStringList FACE_BLOCKS = new ConfigStringList("faceBlocks", ImmutableList.of()).apply(FACE_KEY);
        public static final ConfigStringList FACE_FLUIDS = new ConfigStringList("faceFluids", ImmutableList.of()).apply(FACE_KEY);
        public static final ConfigStringList FACE_BLOCK_ENTITIES = new ConfigStringList("faceBlockEntities", ImmutableList.of()).apply(FACE_KEY);
        public static final ConfigStringList FACE_FALLING_BLOCKS = new ConfigStringList("faceFallingBlocks", ImmutableList.of()).apply(FACE_KEY);
        public static final ConfigStringList FACE_ARMOR = new ConfigStringList("faceArmor", ImmutableList.of()).apply(FACE_KEY);
        public static final ConfigStringList FACE_HELD_ITEMS = new ConfigStringList("faceHeldItems", ImmutableList.of()).apply(FACE_KEY);
        public static final ConfigStringList FACE_ITEM_ENTITIES = new ConfigStringList("faceItemEntities", ImmutableList.of()).apply(FACE_KEY);
        public static final ConfigStringList FACE_ELYTRA = new ConfigStringList("faceElytra", ImmutableList.of()).apply(FACE_KEY);
        public static final ConfigStringList FACE_SKY = new ConfigStringList("faceSky", ImmutableList.of()).apply(FACE_KEY);
        public static final ConfigStringList FACE_HUD_ELEMENTS = new ConfigStringList("faceHudElements", ImmutableList.of()).apply(FACE_KEY);

        public static final ImmutableList<@NotNull IConfigBase> OPTIONS = ImmutableList.of(
                FACE_PARTICLES,
                FACE_ENTITIES,
                FACE_BLOCKS,
                FACE_FLUIDS,
                FACE_BLOCK_ENTITIES,
                FACE_FALLING_BLOCKS,
                FACE_ARMOR,
                FACE_HELD_ITEMS,
                FACE_ITEM_ENTITIES,
                FACE_ELYTRA,
                FACE_SKY,
                FACE_HUD_ELEMENTS
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
                migrateFaceToggle(root);
                ConfigUtils.readConfigBase(root, "Disable", Toggles.OPTIONS);
                ConfigUtils.readConfigBase(root, "Generic", General.OPTIONS);
                ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.OPTIONS);
                ConfigUtils.readConfigBase(root, "Filter", Filters.OPTIONS);
                ConfigUtils.readConfigBase(root, "Face", Face.OPTIONS);
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
     * The face-mod master switch moved from the "Disable" config section to
     * the "Generic" section. Old configs still store it under "Disable", so
     * the value is copied over before the sections are read back.
     */
    private static void migrateFaceToggle(JsonObject root) {
        JsonObject disable = root.getAsJsonObject("Disable");
        JsonObject generic = root.getAsJsonObject("Generic");

        if (disable == null || !disable.has("enableFaceMod")) {
            return;
        }

        if (generic == null) {
            generic = new JsonObject();
            root.add("Generic", generic);
        }

        if (!generic.has("enableFaceMod")) {
            generic.add("enableFaceMod", disable.get("enableFaceMod"));
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

        List<String> merged = new ArrayList<>(RenderConfig.Filters.FILTERED_FOGS.getStrings());
        Set<String> seen = new HashSet<>(merged);

        for (String id : RenderConfig.Filters.FILTERED_FOGS.getDefaultStrings()) {
            if (seen.add(id)) {
                merged.add(id);
            }
        }

        RenderConfig.Filters.FILTERED_FOGS.setStrings(merged);
        fogsMig = true;
    }

    /**
     * Adds the pumpkin overlay fog identity ({@link FilterEngine#FOG_EFFECT_PUMPKIN})
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

        List<String> merged = new ArrayList<>(RenderConfig.Filters.FILTERED_FOGS.getStrings());
        if (!merged.contains(FilterEngine.FOG_EFFECT_PUMPKIN)) {
            merged.add(FilterEngine.FOG_EFFECT_PUMPKIN);
            RenderConfig.Filters.FILTERED_FOGS.setStrings(merged);
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
        FilterEngine.cleanUpBlockFilter();
    }

    /**
     * Migrates legacy type-level block entity ids in the config to concrete
     * variant block ids (per-variant filtering).
     */
    private static void expandBlockEntityList() {
        FilterEngine.expandBlockEntityFilter();
    }

    @Override
    public void save() {
        Path configDirPath = FileUtils.getConfigDirectory();
        File configDir = configDirPath.toFile();

        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        JsonObject root = new JsonObject();

        ConfigUtils.writeConfigBase(root, "Disable", Toggles.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Generic", General.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Hotkeys", Hotkeys.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Filter", Filters.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Face", Face.OPTIONS);

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