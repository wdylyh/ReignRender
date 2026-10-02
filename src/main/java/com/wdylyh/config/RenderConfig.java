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
import java.util.List;
import java.util.Set;

public class RenderConfig implements IConfigHandler {

    private static final String CFG_FILE = "reignrender.json";
    private static final String OFF_KEY = "reignrender.config.disable";
    private static final String G_KEY = "reignrender.config.generic";
    private static final String HK_KEY = "reignrender.config.hotkeys";
    private static final String F_KEY = "reignrender.config.filter";
    private static final String FACE_KEY = "reignrender.config.face";
    private static final String C_KEY = "reignrender.config.conditions";

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
                Filters.HUD_MODE,
                // 坐标选取
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
         * Master switch for the coordinate filter. When on, the hide/keep
         * actions of the condition entries in
         * {@link RenderConfig.Conditions#CONDITION_ENTRIES} hide objects at the
         * listed regions (hide without ids hides the whole region, with ids
         * only the listed ones; keep only leaves the listed ids visible).
         * Independent of the per-category filters: the coordinates apply even
         * when e.g. the entity filter is off.
         */
        public static final ConfigBooleanHotkeyed TOGGLE_COORD_FILTER = new ConfigBooleanHotkeyed("toggleCoordFilter", false, "").apply(HK_KEY);

        /**
         * Master switch for the coordinate aware replacement system. The
         * replace actions of the condition entries in
         * {@link RenderConfig.Conditions#CONDITION_ENTRIES} apply only while this
         * switch is on AND the global replacement master switch
         * {@link RenderConfig.General#REPLACE_ENABLED} is OFF — the regional
         * mechanism is mutually exclusive with the global one and only takes
         * over when the global replacement is turned off.
         */
        public static final ConfigBooleanHotkeyed TOGGLE_COORD_REPLACE = new ConfigBooleanHotkeyed("toggleCoordReplace", false, "").apply(HK_KEY);

        /**
         * Master switch for the coordinate face-mod ("指定坐标面修改"). The
         * face actions of the condition entries in
         * {@link RenderConfig.Conditions#CONDITION_ENTRIES} apply only while this
         * switch is on AND the global face modification
         * {@link RenderConfig.General#ENABLE_FACE_MOD} is OFF — the coordinate
         * mechanism is mutually exclusive with the global one and only takes
         * over when the global face mod is turned off.
         */
        public static final ConfigBooleanHotkeyed TOGGLE_REGION_FACE = new ConfigBooleanHotkeyed("toggleRegionFace", false, "").apply(HK_KEY);

        /**
         * Press while looking at a block to pick its position into the
         * condition entry list. With {@link RenderConfig.General#COORD_PICK_MODE} set to
         * "single" the looked-at position is stored directly; with "box" press
         * the hotkey once for corner 1 and again for corner 2 to store a
         * region.
         */
        public static final ConfigHotkey PICK_COORD_HOTKEY = new ConfigHotkey("pickCoordHotkey", "", KeybindSettings.PRESS_ALLOWEXTRA).apply(HK_KEY);

        /**
         * Master switch for the per-id render count limits. When on, the
         * count entries in {@link RenderConfig.Conditions#CONDITION_ENTRIES} cap
         * how many objects of each id are rendered per frame (entities,
         * particles, block entities) / baked per section (blocks).
         * Independent of the per-category filters: the caps apply even when
         * e.g. the entity filter is off.
         */
        public static final ConfigBooleanHotkeyed TOGGLE_COUNT_LIMITS = new ConfigBooleanHotkeyed("toggleCountLimits", false, "").apply(HK_KEY);

        /**
         * Master switch for the per-id render distance limits. When on, the
         * dist entries in {@link RenderConfig.Conditions#CONDITION_ENTRIES} hide
         * objects farther than their per-id cap (in blocks) from the camera.
         * Independent of the per-category filters.
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

        /**
         * HUD 元素过滤模式。黑名单：隐藏列表中的元素；白名单：仅显示列表中
         * 的元素；关闭：隐藏全部 HUD 元素。主开关
         * {@link RenderConfig.Toggles#DISABLE_HUD_ELEMENTS} 仍是总开关。
         */
        public static final ConfigOptionValues<BaseOptionListConfigValue> HUD_MODE = new ConfigOptionValues<>(
                "hudMode", MODE_BLACKLIST, ImmutableList.of(MODE_OFF, MODE_BLACKLIST, MODE_WHITELIST)).apply(F_KEY);

        public static final ConfigStringList FILTERED_ENTITIES = new ConfigStringList("filteredEntities", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList FILTERED_BLOCKS = new ConfigStringList("filteredBlocks", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList FILTERED_FLUIDS = new ConfigStringList("filteredFluids", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList FILTERED_BLOCK_ENTITIES = new ConfigStringList("filteredBlockEntities", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList FILTERED_PARTICLES = new ConfigStringList("filteredParticles", ImmutableList.of()).apply(F_KEY);
        public static final ConfigStringList FILTERED_ARMOR = new ConfigStringList("filteredArmor", ImmutableList.of()).apply(F_KEY);
        /**
         * Fog identities hidden or kept by the fog filter. The list starts
         * empty (so the reset button clears it, like every other filter
         * list); ids are added through the picker, the pick hotkey or manual
         * input. Supported identities: camera submersion fog types (water,
         * lava, powder_snow, atmospheric), biome ids, dimension ids and the
         * status-effect fog identities ({@link FilterEngine#FOG_EFFECT_BLINDNESS}
         * etc.). With the fog filter in BLACKLIST mode every listed id is
         * hidden.
         */
        public static final ConfigStringList FILTERED_FOGS = new ConfigStringList("filteredFogs", ImmutableList.of()).apply(F_KEY);

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

    /**
     * Unified condition system ("条件系统"). Each entry is one semicolon
     * separated {@code key=value} line combining conditions (region / dist /
     * count / ids) with actions (hide / keep / replace:src=dst / face); the
     * parsed details and the queries live in {@link ConditionEngine}. The
     * actions reuse the existing engines: hide/keep feed the coordinate
     * filter ({@link RenderConfig.Hotkeys#TOGGLE_COORD_FILTER}), replace feeds
     * the coordinate replacement ({@link RenderConfig.Hotkeys#TOGGLE_COORD_REPLACE}),
     * face feeds the region face-mod ({@link RenderConfig.Hotkeys#TOGGLE_REGION_FACE})
     * and dist/count are gated by {@link RenderConfig.Hotkeys#TOGGLE_DISTANCE_LIMITS} /
     * {@link RenderConfig.Hotkeys#TOGGLE_COUNT_LIMITS}.
     */
    public static class Conditions {
        public static final ConfigStringList CONDITION_ENTRIES = new ConfigStringList(
                "conditionEntries", ImmutableList.of()).apply(C_KEY);

        public static final ImmutableList<@NotNull IConfigBase> OPTIONS = ImmutableList.of(
                CONDITION_ENTRIES
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
                migrateConditions(root);
                ConfigUtils.readConfigBase(root, "Disable", Toggles.OPTIONS);
                ConfigUtils.readConfigBase(root, "Generic", General.OPTIONS);
                ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.OPTIONS);
                ConfigUtils.readConfigBase(root, "Filter", Filters.OPTIONS);
                ConfigUtils.readConfigBase(root, "Face", Face.OPTIONS);
                ConfigUtils.readConfigBase(root, "Conditions", Conditions.OPTIONS);
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
     * Migrates the old scattered coordinate/limit lists into the unified
     * condition system:
     * <ul>
     *   <li>Filter.coordEntries → region entries with the action chosen by the
     *       old coordinate mode (OFF = whole-region hide, BLACKLIST = hide with
     *       ids, WHITELIST = keep with ids)</li>
     *   <li>Filter.coordReplaceEntries → replace actions</li>
     *   <li>Filter.regionFaceEntries → face actions</li>
     *   <li>Filter.countLimits / Filter.distanceLimits ("id=N") → count / dist
     *       entries with ids</li>
     * </ul>
     * The converted lines are written into Conditions.conditionEntries (only
     * when that key is not already present, so a re-migration never overwrites
     * newer edits) and the old keys are removed from the json.
     */
    private static void migrateConditions(JsonObject root) {
        JsonObject filter = root.getAsJsonObject("Filter");

        if (filter == null) {
            return;
        }

        // The old coordinate mode decides the action of the coord entries.
        String coordMode = "off";
        JsonObject generic = root.getAsJsonObject("Generic");
        if (generic != null && generic.has("coordMode") && generic.get("coordMode").isJsonPrimitive()) {
            coordMode = generic.get("coordMode").getAsString();
        }

        List<String> out = new ArrayList<>();

        if (filter.has("coordEntries") && filter.get("coordEntries").isJsonArray()) {
            for (JsonElement el : filter.getAsJsonArray("coordEntries")) {
                if (!el.isJsonPrimitive()) {
                    continue;
                }
                String[] parts = el.getAsString().split(";", -1);
                String box = parts[0].trim();
                StringBuilder ids = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    String id = parts[i].trim();
                    if (!id.isEmpty()) {
                        if (ids.length() > 0) {
                            ids.append(',');
                        }
                        ids.append(id);
                    }
                }
                switch (coordMode) {
                    case "whitelist" -> out.add("region=" + box
                            + (ids.length() > 0 ? ";ids=" + ids : "") + ";acts=keep");
                    case "blacklist" -> out.add("region=" + box
                            + (ids.length() > 0 ? ";ids=" + ids : "") + ";acts=blacklist");
                    // OFF mode hid the whole region regardless of any ids.
                    default -> out.add("region=" + box + ";acts=hide");
                }
            }
        }

        if (filter.has("coordReplaceEntries") && filter.get("coordReplaceEntries").isJsonArray()) {
            for (JsonElement el : filter.getAsJsonArray("coordReplaceEntries")) {
                if (!el.isJsonPrimitive()) {
                    continue;
                }
                String[] parts = el.getAsString().split(";", -1);
                if (parts.length < 2) {
                    continue;
                }
                StringBuilder acts = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    String rule = parts[i].trim();
                    if (!rule.isEmpty()) {
                        if (acts.length() > 0) {
                            acts.append(',');
                        }
                        acts.append("replace:").append(rule);
                    }
                }
                if (acts.length() > 0) {
                    out.add("region=" + parts[0].trim() + ";acts=" + acts);
                }
            }
        }

        if (filter.has("regionFaceEntries") && filter.get("regionFaceEntries").isJsonArray()) {
            for (JsonElement el : filter.getAsJsonArray("regionFaceEntries")) {
                if (!el.isJsonPrimitive()) {
                    continue;
                }
                String[] parts = el.getAsString().split(";", -1);
                StringBuilder ids = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    String id = parts[i].trim();
                    if (!id.isEmpty()) {
                        if (ids.length() > 0) {
                            ids.append(',');
                        }
                        ids.append(id);
                    }
                }
                out.add("region=" + parts[0].trim()
                        + (ids.length() > 0 ? ";ids=" + ids : "") + ";acts=face");
            }
        }

        for (String[] limits : new String[][] {{"countLimits", "count"}, {"distanceLimits", "dist"}}) {
            if (filter.has(limits[0]) && filter.get(limits[0]).isJsonArray()) {
                for (JsonElement el : filter.getAsJsonArray(limits[0])) {
                    if (!el.isJsonPrimitive()) {
                        continue;
                    }
                    String[] pair = el.getAsString().split("=", -1);
                    if (pair.length == 2 && !pair[0].trim().isEmpty() && !pair[1].trim().isEmpty()) {
                        out.add(limits[1] + "=" + pair[1].trim() + ";ids=" + pair[0].trim());
                    }
                }
            }
        }

        // Remove the migrated keys so the migration runs only once.
        for (String key : new String[] {"coordEntries", "coordReplaceEntries",
                "regionFaceEntries", "countLimits", "distanceLimits"}) {
            filter.remove(key);
        }
        if (generic != null) {
            generic.remove("coordMode");
        }

        if (!out.isEmpty()) {
            JsonObject conds = root.getAsJsonObject("Conditions");
            if (conds == null) {
                conds = new JsonObject();
                root.add("Conditions", conds);
            }
            // Never overwrite entries a newer version already saved.
            if (!conds.has("conditionEntries")) {
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (String line : out) {
                    arr.add(line);
                }
                conds.add("conditionEntries", arr);
            }
        }
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
        ConfigUtils.writeConfigBase(root, "Conditions", Conditions.OPTIONS);

        JsonUtils.writeJsonToFile(root, new File(configDir, CFG_FILE));
    }

    private static File cfgFile() {
        File configDir = FileUtils.getConfigDirectory().toFile();
        return new File(configDir, CFG_FILE);
    }
}