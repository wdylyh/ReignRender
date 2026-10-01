package com.wdylyh.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import fi.dy.masa.malilib.config.value.BaseOptionListConfigValue;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeybindMulti;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized blacklist/whitelist matching for the per-type render filters.
 *
 * The current filter mode (OFF / BLACKLIST / WHITELIST) and the five registry
 * id lists are read from {@link RenderConfig.Filters}. All methods return true when
 * the given type should be hidden from rendering.
 */
public class FilterEngine {

    // Filter id sets, published as immutable snapshots. The query methods
    // (isBlockFiltered / isEntityFiltered / ...) run on both the ChunkBuilder
    // worker threads (SectionBuilder baking the meshes) and the render thread,
    // while a config change rebuilds the sets from whatever thread queries
    // first. Mutating a plain HashSet under that concurrency could leave an
    // individual chunk reading a half-cleared / half-filled set, baking the
    // wrong hide state into a small area that then never refreshes. Publishing
    // an immutable snapshot through a volatile reference makes every reader
    // see a fully built set.
    private static volatile Set<String> filtered_Entities = Set.of();
    private static volatile Set<String> filtered_Blocks = Set.of();
    private static volatile Set<String> filtered_Fluids = Set.of();
    private static volatile Set<String> filtered_Block_Entities = Set.of();
    private static volatile Set<String> filtered_Particles = Set.of();
    private static volatile Set<String> filtered_Armor = Set.of();
    private static volatile Set<String> filtered_Fogs = Set.of();
    private static volatile Set<String> filtered_Name_Tags = Set.of();
    private static volatile Set<String> filtered_Players = Set.of();

    // Pre-computed lowercase enum names so isFogFiltered does not allocate a
    // new String on every frame. CameraSubmersionType has only a handful of
    // constants, so this map is tiny.
    private static final EnumMap<CameraSubmersionType, String> FOG_NAMES = new EnumMap<>(CameraSubmersionType.class);
    static {
        for (CameraSubmersionType subType : CameraSubmersionType.values()) {
            FOG_NAMES.put(subType, subType.name().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Fog identities for the status-effect fogs, stored in the same
     * {@link RenderConfig.Filters.FILTERED_FOGS} list as the biome and submersion
     * type ids. Blindness and darkness darken the fog while the effect is
     * active, the wither boss thickens the fog while it is nearby.
     */
    public static final String FOG_EFFECT_BLINDNESS = "effect:blindness";
    public static final String FOG_EFFECT_DARKNESS = "effect:darkness";
    public static final String FOG_EFFECT_WITHER = "effect:wither";
    public static final String FOG_EFFECT_NIGHT_VISION = "effect:night_vision";

    /**
     * Fog identity for the pumpkin overlay (the full-screen pumpkin texture
     * drawn while wearing a carved pumpkin). It is added to the default fog
     * list so wearing a pumpkin gets its overlay hidden like the other fog
     * effects, without needing a dedicated toggle.
     */
    public static final String FOG_EFFECT_PUMPKIN = "effect:pumpkin";

    // ==================== 快捷键影响的渲染类型 (Hotkey affected types) ====================

    /**
     * Identifiers for every render category the reveal hotkey can affect.
     * The {@link RenderConfig.General.REVEAL_AFFECTED_TYPES} string list stores a
     * subset of these; only a category present in that list is affected by the
     * hotkey (bypassed in reveal mode, force-hidden in force-hide mode).
     */
    public static final String TYPE_ENTITIES = "entities";
    public static final String TYPE_FALLING_BLOCKS = "fallingBlocks";
    public static final String TYPE_PLAYERS = "players";
    public static final String TYPE_BLOCKS = "blocks";
    public static final String TYPE_FLUIDS = "fluids";
    public static final String TYPE_BLOCK_ENTITIES = "blockEntities";
    public static final String TYPE_PARTICLES = "particles";
    public static final String TYPE_ARMOR = "armor";
    public static final String TYPE_HELD_ITEMS = "heldItems";
    public static final String TYPE_ELYTRA = "elytra";
    public static final String TYPE_FOG = "fog";
    public static final String TYPE_NAME_TAGS = "nameTags";
    /** Item drop (ItemEntity) replacement, independent of the entity replacement. */
    public static final String TYPE_ITEM_ENTITIES = "itemEntities";
    /** Player name label replacement, independent of the general name tag replacement. */
    public static final String TYPE_PLAYER_NAMES = "playerNames";
    /** HUD element replacement (InGameHud element ids like "hotbar"). */
    public static final String TYPE_HUD_ELEMENTS = "hudElements";

    /** All category identifiers, used as the default for the affected types list. */
    public static final List<String> ALL_TYPES = List.of(
            TYPE_ENTITIES, TYPE_FALLING_BLOCKS, TYPE_PLAYERS, TYPE_BLOCKS, TYPE_FLUIDS,
            TYPE_BLOCK_ENTITIES, TYPE_PARTICLES, TYPE_ARMOR, TYPE_HELD_ITEMS,
            TYPE_ELYTRA, TYPE_FOG, TYPE_NAME_TAGS, TYPE_ITEM_ENTITIES, TYPE_PLAYER_NAMES,
            TYPE_HUD_ELEMENTS);

    private static volatile Set<String> reveal_Affected_Types = Set.of();

    /** Cached HUD element ids from HIDDEN_HUD_ELEMENTS (Set lookup, rebuilt on config change). */
    private static volatile Set<String> hidden_Hud_Elements = Set.of();

    private static volatile boolean dirty = true;

    private static final Logger LOGGER = LoggerFactory.getLogger(FilterEngine.class);

    // The filter methods below are called for every visible block/block entity
    // on every frame. To keep the log readable we record each (block id -> decision)
    // only once per config change instead of logging every single call. They are
    // only filled from the render thread except isBlockFiltered, which also runs
    // on the ChunkBuilder worker threads, so a concurrent set keeps the add()
    // safe regardless of which thread gets there first.
    private static final Set<String> logged_Block_Decisions = ConcurrentHashMap.newKeySet();
    private static final Set<String> logged_Block_Entity_Decisions = ConcurrentHashMap.newKeySet();

    // Lazy per-registry id caches. The registry id String of a key never
    // changes, so Registries.X.getId(key).toString() is computed once and
    // reused forever instead of allocating a new String on every frame / block.
    // Chunk building runs on the ChunkBuilder worker threads while the render
    // filters run on the render thread, so the caches are concurrent maps.
    // They are cleared together with the filter sets when a config change
    // rebuilds the caches (a datapack reload may have added new entries).
    private static final Map<EntityType<?>, String> ENTITY_ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<Block, String> BLOCK_ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<Fluid, String> FLUID_ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<ParticleType<?>, String> PARTICLE_ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<Item, String> ITEM_ID_CACHE = new ConcurrentHashMap<>();
    /** Biome/dimension registry-id string caches, used by the per-frame fog path. */
    private static final Map<RegistryKey<Biome>, String> BIOME_ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, String> DIMENSION_ID_CACHE = new ConcurrentHashMap<>();

    /**
     * Called whenever the filter mode or any of the filter lists change,
     * so the cached id sets are rebuilt on the next query.
     */
    public static void invalidateCaches() {
        dirty = true;
    }

    private static void rebuild() {
        if (dirty) {
            synchronized (FilterEngine.class) {
                if (dirty) {
                    // Build every set locally and publish the finished
                    // snapshots only after all of them are complete. Multiple
                    // threads (the ChunkBuilder workers and the render thread)
                    // can hit dirty=true at once; the double-checked lock makes
                    // exactly one rebuild run, so no reader ever sees a
                    // half-cleared / half-filled set.
                    Set<String> entities = new HashSet<>(RenderConfig.Filters.FILTERED_ENTITIES.getStrings());
                    Set<String> blocks = new HashSet<>(RenderConfig.Filters.FILTERED_BLOCKS.getStrings());
                    Set<String> fluids = new HashSet<>(RenderConfig.Filters.FILTERED_FLUIDS.getStrings());
                    Set<String> blockEntities = new HashSet<>(RenderConfig.Filters.FILTERED_BLOCK_ENTITIES.getStrings());
                    Set<String> particles = new HashSet<>(RenderConfig.Filters.FILTERED_PARTICLES.getStrings());
                    Set<String> armorSet = new HashSet<>(RenderConfig.Filters.FILTERED_ARMOR.getStrings());
                    Set<String> fogsSet = new HashSet<>(RenderConfig.Filters.FILTERED_FOGS.getStrings());
                    Set<String> nameTagsSet = new HashSet<>();
                    Set<String> playersSet = new HashSet<>();
                    Set<String> affectedTypes = new HashSet<>(RenderConfig.General.REVEAL_AFFECTED_TYPES.getStrings());
                    Set<String> hudElements = new HashSet<>(RenderConfig.Filters.HIDDEN_HUD_ELEMENTS.getStrings());

                    addNames(nameTagsSet, RenderConfig.Filters.FILTERED_NAME_TAGS.getStringValue());
                    addNames(playersSet, RenderConfig.Filters.FILTERED_PLAYERS.getStringValue());

                    filtered_Entities = Collections.unmodifiableSet(entities);
                    filtered_Blocks = Collections.unmodifiableSet(blocks);
                    filtered_Fluids = Collections.unmodifiableSet(fluids);
                    filtered_Block_Entities = Collections.unmodifiableSet(blockEntities);
                    filtered_Particles = Collections.unmodifiableSet(particles);
                    filtered_Armor = Collections.unmodifiableSet(armorSet);
                    filtered_Fogs = Collections.unmodifiableSet(fogsSet);
                    filtered_Name_Tags = Collections.unmodifiableSet(nameTagsSet);
                    filtered_Players = Collections.unmodifiableSet(playersSet);
                    reveal_Affected_Types = Collections.unmodifiableSet(affectedTypes);
                    hidden_Hud_Elements = Collections.unmodifiableSet(hudElements);

                    // A config change means the old logged decisions are stale, and the
                    // id caches are cleared too (a datapack reload may have added new
                    // entries to the registries).
                    logged_Block_Decisions.clear();
                    logged_Block_Entity_Decisions.clear();
                    ENTITY_ID_CACHE.clear();
                    BLOCK_ID_CACHE.clear();
                    FLUID_ID_CACHE.clear();
                    PARTICLE_ID_CACHE.clear();
                    ITEM_ID_CACHE.clear();
                    BIOME_ID_CACHE.clear();
                    DIMENSION_ID_CACHE.clear();

                    dirty = false;
                }
            }
        }
    }

    /**
     * Returns true when the given HUD element id should be hidden: the master
     * "Disable HUD Elements" toggle must be on AND the id must appear in the
     * HIDDEN_HUD_ELEMENTS list (an empty list hides nothing). The list is
     * cached as a Set so the per-frame lookup is O(1) instead of a linear scan.
     */
    public static boolean isHudElementHidden(String id) {
        // 主开关关闭时（最常见路径）直接短路，跳过缓存脏检查。
        if (!RenderConfig.Toggles.DISABLE_HUD_ELEMENTS.getBooleanValue()) {
            return false;
        }
        rebuild();
        return hidden_Hud_Elements.contains(id);
    }

    private static boolean isFiltered(BaseOptionListConfigValue mode, Set<String> list, String id) {
        if (mode == RenderConfig.Filters.MODE_OFF) {
            return false;
        }

        // BLACKLIST hides entries present in the list,
        // WHITELIST hides entries absent from the list.
        boolean inList = list.contains(id);
        return (mode == RenderConfig.Filters.MODE_WHITELIST) != inList;
    }

    /**
     * BLACKLIST/WHITELIST match for the name based filters (name tags, player
     * names) that avoids allocating a lowercased copy of {@code name} on every
     * call. Small lists (the common case) are scanned allocation-free with
     * {@code equalsIgnoreCase}; larger lists fall back to the cached lowercase
     * set lookup so the linear scan never dominates.
     */
    private static boolean icMatch(BaseOptionListConfigValue mode, Set<String> list, String name) {
        boolean inList;
        if (list.size() <= 16) {
            inList = false;
            for (String candidate : list) {
                if (candidate.equalsIgnoreCase(name)) {
                    inList = true;
                    break;
                }
            }
        } else {
            inList = list.contains(name.toLowerCase(Locale.ROOT));
        }
        return (mode == RenderConfig.Filters.MODE_WHITELIST) != inList;
    }

    /**
     * Splits a semicolon separated name list ("alice;bob") into the given set,
     * trimmed and lowercased so matching is case-insensitive. Empty entries are
     * skipped.
     */
    private static void addNames(Set<String> set, String raw) {
        if (raw == null) {
            return;
        }

        for (String part : raw.split(";")) {
            String name = part.trim().toLowerCase(Locale.ROOT);

            if (!name.isEmpty()) {
                set.add(name);
            }
        }
    }

    /**
     * Resolves and caches the registry id String of a key, or returns null when
     * the key is not present in the registry. The cache is populated on first
     * use (per-thread safe via ConcurrentHashMap) and cleared on config change,
     * so the per-frame toString() allocations happen only once per registry key.
     */
    private static <T> String registry_Id_Lookup(Map<T, String> cache, T key, Function<T, Identifier> resolver) {
        String id = cache.get(key);

        if (id == null) {
            Identifier identifier = resolver.apply(key);

            if (identifier != null) {
                id = identifier.toString();
                cache.put(key, id);
            }
        }

        return id;
    }

    /** Cached registry id String of an entity type, or null. */
    public static String getEntityId(EntityType<?> type) {
        return registry_Id_Lookup(ENTITY_ID_CACHE, type, Registries.ENTITY_TYPE::getId);
    }

    /** Cached registry id String of a block, or null. */
    public static String getBlockId(Block block) {
        return registry_Id_Lookup(BLOCK_ID_CACHE, block, Registries.BLOCK::getId);
    }

    /** Cached registry id String of a fluid, or null. */
    public static String getFluidId(Fluid fluid) {
        return registry_Id_Lookup(FLUID_ID_CACHE, fluid, Registries.FLUID::getId);
    }

    /** Cached registry id String of a particle type, or null. */
    public static String getParticleId(ParticleType<?> type) {
        return registry_Id_Lookup(PARTICLE_ID_CACHE, type, Registries.PARTICLE_TYPE::getId);
    }

    /** Cached registry id String of an item, or null. */
    public static String getItemId(Item item) {
        return registry_Id_Lookup(ITEM_ID_CACHE, item, Registries.ITEM::getId);
    }

    /**
     * Returns true while the reveal hotkey is held in reveal ("release") mode
     * AND the given render category is checked in the affected types list.
     * While true, the filters of that category are bypassed so it renders
     * normally. Blocks and fluids are covered too: their filter is baked into
     * the chunk meshes, so the hotkey callback (registered with INGAME_BOTH)
     * rebuilds every mesh on press/release, and the SectionBuilder /
     * BlockRenderManager mixins skip their replacement while this returns true.
     */
    public static boolean isRevealHeld(String type) {
        return isRevealHeld(type, revealDown());
    }

    /**
     * {@link #isRevealHeld(String)} with the reveal key state already queried.
     * Per-frame callers capture {@link #revealDown()} once and pass it
     * down, so the native GLFW keyboard query happens once per frame per caller
     * instead of once per filtered entity/block.
     */
    public static boolean isRevealHeld(String type, boolean keyDown) {
        return RenderConfig.General.REVEAL_HOTKEY_MODE.getOptionValue() == RenderConfig.General.HOTKEY_MODE_RELEASE
                && keyDown
                && isTypeAffected(type);
    }

    /**
     * Returns true while the reveal hotkey is held in force-hide ("enable")
     * mode AND the given render category is checked in the affected types
     * list AND the category's own "disable rendering" toggle is on. While
     * true, that category is forced hidden regardless of its own filter
     * mode, so it goes empty as long as the key is held. Categories whose
     * master toggle is off are left alone: the temporary hide only extends
     * the disabling the player has actually enabled.
     */
    public static boolean isFilterForced(String type) {
        return isFilterForced(type, revealDown());
    }

    /**
     * {@link #isFilterForced(String)} with the reveal key state already
     * queried (see {@link #isRevealHeld(String, boolean)}).
     */
    public static boolean isFilterForced(String type, boolean keyDown) {
        return RenderConfig.General.REVEAL_HOTKEY_MODE.getOptionValue() == RenderConfig.General.HOTKEY_MODE_ENABLE
                && keyDown
                && isTypeAffected(type)
                && typeOff(type);
    }

    /**
     * Returns true while the reveal hotkey is physically held, based on a
     * direct GLFW key state query instead of the keybind's event-driven
     * {@code pressed} flag. The flag can get stuck at true when a key-release
     * event is lost (for example the window losing focus while the key is
     * held), which would make the temporary reveal / force-hide stay active
     * forever. Querying the keyboard state every frame is immune to that.
     * Only the first bound key is checked; with no keys bound it returns
     * false.
     */
    public static boolean keyDown() {
        IKeybind keybind = RenderConfig.Hotkeys.REVEAL_HOTKEY.getKeybind();
        List<Integer> keys = keybind.getKeys();
        if (keys.isEmpty()) {
            return false;
        }
        return KeybindMulti.isKeyDown(keys.get(0));
    }

    /**
     * Returns the reveal key state that per-frame callers should query exactly
     * once and pass down to the keyDown-aware filter overloads. This is the raw
     * physical key state; the hotkey mode is applied inside each overload (the
     * RELEASE mode uses it to bypass filters while held, the ENABLE mode uses
     * it to force-hide while held), so in both modes the key state matters.
     * <p>
     * The polled key state can only change between frames, so instead of
     * issuing a GLFW keyboard query for every filtered entity / block / fluid /
     * particle of the frame, the state is sampled once per frame (in
     * {@link #frame()}, which runs exactly once per frame
     * before the entities are processed) and this method just reads the cached
     * value. The first call ever samples immediately, so the very first frame
     * is accurate too. Volatile because chunk-builder threads read it while the
     * render thread re-samples it each frame.
     */
    private static volatile boolean downCache = false;
    private static volatile boolean sampled = false;
    // The reveal key state the visible chunk meshes were last built with. The
    // block/fluid replacement and hiding are baked into the chunk meshes, so
    // whenever the physical key state differs from it the meshes must be
    // rebuilt with the new behavior. Keeping the previous build state here lets
    // frame() detect the change and schedule the rebuild on the same frame the
    // cached state is already updated, so worker threads never bake a stale
    // behavior (the keybind-triggered rebuild alone can race and read the
    // previous frame's state, leaving "uncontrolled" blocks behind).
    private static volatile boolean builtDown = false;

    public static boolean revealDown() {
        if (!sampled) {
            downCache = keyDown();
            sampled = true;
        }
        return downCache;
    }

    /**
     * 合并后的 reveal 热键行为判断。每个过滤方法只需调用一次，内部只读取
     * 一次热键模式，替代原先 filterAppliesNow / isRevealHeld / isFilterForced
     * 三个方法各自重复读取模式的写法。返回当前类别在本帧应采用的临时行为：
     * <ul>
     *   <li>{@link #HOTKEY_BEHAVIOR_NORMAL}：无临时行为，按正常 list 过滤。</li>
     *   <li>{@link #HOTKEY_BEHAVIOR_BYPASS}：release 模式按住且类别受影响，
     *       绕过过滤（该类别渲染正常）。</li>
     *   <li>{@link #HOTKEY_BEHAVIOR_FORCE}：enable 模式按住且类别受影响且
     *       该类别主开关开启，强制隐藏。</li>
     *   <li>{@link #HOTKEY_BEHAVIOR_INACTIVE}：enable 模式未按住，过滤不
     *       生效（恢复渲染），等价于原 filterAppliesNow 返回 false。</li>
     * </ul>
     * 公开给 SectionBuilder 等每区块/每帧调用方：它们只求值一次并缓存结果，
     * 避免对每个方块/粒子/实体重复读取热键模式。
     */
    public static int hotkey_Behavior(String type, boolean keyDown) {
        BaseOptionListConfigValue mode = RenderConfig.General.REVEAL_HOTKEY_MODE.getOptionValue();
        if (mode == RenderConfig.General.HOTKEY_MODE_ENABLE) {
            if (!keyDown) {
                return HOTKEY_BEHAVIOR_INACTIVE;
            }
            return isTypeAffected(type) && typeOff(type)
                    ? HOTKEY_BEHAVIOR_FORCE : HOTKEY_BEHAVIOR_NORMAL;
        }
        // Release 模式：按住且类别受影响 → 绕过过滤。
        return keyDown && isTypeAffected(type) ? HOTKEY_BEHAVIOR_BYPASS : HOTKEY_BEHAVIOR_NORMAL;
    }

    /** 行为码：无临时行为，正常过滤。 */
    public static final int HOTKEY_BEHAVIOR_NORMAL = 0;
    /** 行为码：release 模式按住 → 绕过过滤。 */
    public static final int HOTKEY_BEHAVIOR_BYPASS = 1;
    /** 行为码：enable 模式按住 → 强制隐藏。 */
    public static final int HOTKEY_BEHAVIOR_FORCE = 2;
    /** 行为码：enable 模式未按住 → 过滤不生效。 */
    public static final int HOTKEY_BEHAVIOR_INACTIVE = 3;

    /**
     * Returns true when the given render category has its master "disable
     * rendering" toggle enabled. Only these categories are affected by the
     * temporary hide of the reveal hotkey, so a category that is not being
     * disabled normally keeps rendering while the hotkey is held.
     */
    private static boolean typeOff(String type) {
        return switch (type) {
            case TYPE_ENTITIES -> RenderConfig.Toggles.DISABLE_ENTITIES.getBooleanValue();
            case TYPE_FALLING_BLOCKS -> RenderConfig.Toggles.DISABLE_FALLING_BLOCKS.getBooleanValue();
            case TYPE_PLAYERS -> RenderConfig.Toggles.HIDE_OTHER_PLAYERS.getBooleanValue()
                    || RenderConfig.Toggles.HIDE_SELF.getBooleanValue();
            case TYPE_BLOCKS -> RenderConfig.Toggles.DISABLE_BLOCKS.getBooleanValue();
            case TYPE_FLUIDS -> RenderConfig.Toggles.DISABLE_FLUIDS.getBooleanValue();
            case TYPE_BLOCK_ENTITIES -> RenderConfig.Toggles.DISABLE_BLOCK_ENTITIES.getBooleanValue();
            case TYPE_PARTICLES -> RenderConfig.Toggles.DISABLE_PARTICLES.getBooleanValue();
            case TYPE_ARMOR -> RenderConfig.Toggles.DISABLE_ARMOR.getBooleanValue();
            case TYPE_HELD_ITEMS -> RenderConfig.Toggles.DISABLE_HELD_ITEMS.getBooleanValue();
            case TYPE_ELYTRA -> RenderConfig.Toggles.DISABLE_ELYTRA.getBooleanValue();
            case TYPE_FOG -> RenderConfig.Toggles.DISABLE_FOG.getBooleanValue();
            case TYPE_NAME_TAGS -> RenderConfig.Toggles.DISABLE_NAME_TAGS.getBooleanValue();
            case TYPE_ITEM_ENTITIES -> RenderConfig.Toggles.DISABLE_ITEM_ENTITIES.getBooleanValue();
            case TYPE_PLAYER_NAMES -> RenderConfig.Toggles.DISABLE_NAME_TAGS.getBooleanValue();
            default -> false;
        };
    }

    /**
     * Returns true when the given render category is present in the affected
     * types list, i.e. the reveal hotkey can affect it.
     */
    public static boolean isTypeAffected(String type) {
        rebuild();
        return reveal_Affected_Types.contains(type);
    }

    /**
     * True when the replacement for the given category is currently suppressed.
     * The replacement is only active while the reveal behavior is
     * {@link #HOTKEY_BEHAVIOR_NORMAL} (the default state: release mode with the
     * hotkey not held). Every temporary reveal state blocks it so the original
     * content shows through:
     * <ul>
     *   <li>release ("解除") mode + hotkey held → {@code HOTKEY_BEHAVIOR_BYPASS}:
     *       the original content is shown again, so the replacement is skipped.</li>
     *   <li>enable ("开启") mode + hotkey held → {@code HOTKEY_BEHAVIOR_FORCE}:
     *       the category is force-hidden like its own disable logic, so the
     *       replacement is skipped too.</li>
     *   <li>enable ("开启") mode + hotkey not held → {@code HOTKEY_BEHAVIOR_INACTIVE}:
     *       the filters are inactive and the world renders as-is (original),
     *       so the replacement is skipped as well.</li>
     * </ul>
     */
    public static boolean isReplaceBlocked(String type) {
        return isReplaceBlocked(type, revealDown());
    }

    /**
     * {@link #isReplaceBlocked(String)} with the reveal key state already
     * queried (see {@link #isRevealHeld(String, boolean)}).
     */
    public static boolean isReplaceBlocked(String type, boolean keyDown) {
        return hotkey_Behavior(type, keyDown) != HOTKEY_BEHAVIOR_NORMAL;
    }

    public static boolean isEntityFiltered(EntityType<?> type) {
        return isEntityFiltered(type, revealDown());
    }

    /**
     * {@link #isEntityFiltered(EntityType)} with the reveal key state already
     * queried (see {@link #isRevealHeld(String, boolean)}).
     */
    public static boolean isEntityFiltered(EntityType<?> type, boolean keyDown) {
        return isEntityFiltered(type, keyDown, hotkey_Behavior(TYPE_ENTITIES, keyDown));
    }

    /**
     * {@link #isEntityFiltered(EntityType, boolean)} with the reveal behavior
     * already evaluated once by the caller (per-entity callers like
     * EntityRenderDispatcherMixin compute a single code for the whole filter
     * chain instead of one per filter method).
     */
    public static boolean isEntityFiltered(EntityType<?> type, boolean keyDown, int behavior) {
        if (type == null) {
            return false;
        }
        if (behavior != HOTKEY_BEHAVIOR_NORMAL) {
            // 绕过过滤 / 过滤不生效 → 渲染正常；强制隐藏 → 隐藏。
            return behavior == HOTKEY_BEHAVIOR_FORCE;
        }
        rebuild();
        BaseOptionListConfigValue mode = RenderConfig.Filters.ENTITY_MODE.getOptionValue();
        // With the master "disable entities" toggle on, an OFF mode means
        // every entity is hidden; otherwise the blacklist/whitelist applies.
        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }
        String id = getEntityId(type);
        return id != null && isFiltered(mode, filtered_Entities, id);
    }

    public static void frame() {
        // Sample the reveal key state once per frame here (this is called from
        // fillEntityRenderStates HEAD, exactly once per frame before the
        // entities are processed), so the per-entity/per-particle filter
        // queries of the frame reuse one GLFW keyboard query instead of each
        // issuing their own.
        downCache = keyDown();
        sampled = true;

        // The condition system's per-frame count budget and camera sample are
        // refreshed here too (the count limits reset once per frame, the
        // camera position feeds the block-baking distance checks).
        ConditionEngine.frame();

        // The block/fluid chunk meshes are baked with the reveal behavior that
        // was current when they were built. Rebuild them whenever the physical
        // key state changes, so the reveal / force-hide / replacement flip
        // takes effect reliably instead of only when the keybind rebroadcasts
        // the press (which can schedule the rebuild before this frame's sampled
        // state is current, leaving some blocks baked with the old behavior).
        if (downCache != builtDown) {
            builtDown = downCache;
            ConfigCallbacks.rebuildMeshes();
        }
    }

    public static boolean isBlockFiltered(BlockState state) {
        return isBlockFiltered(state, revealDown());
    }

    /**
     * {@link #isBlockFiltered(BlockState)} with the reveal key state already
     * queried.
     */
    public static boolean isBlockFiltered(BlockState state, boolean keyDown) {
        return isBlockFiltered(state, keyDown, hotkey_Behavior(TYPE_BLOCKS, keyDown));
    }

    /**
     * {@link #isBlockFiltered(BlockState, boolean)} with the reveal behavior
     * code for {@link #TYPE_BLOCKS} already evaluated. Per-block callers like
     * SectionBuilder evaluate it once per section and pass it down, so the
     * hotkey mode is not read again for every block.
     */
    public static boolean isBlockFiltered(BlockState state, boolean keyDown, int behavior) {
        if (state == null) {
            return false;
        }
        // Block 网格已按 reveal 状态重建（SectionBuilder 单独处理绕过/强制），
        // 这里只需在 enable 模式未按住时让过滤失效。
        if (behavior == HOTKEY_BEHAVIOR_INACTIVE) {
            return false;
        }
        rebuild();
        BaseOptionListConfigValue mode = RenderConfig.Filters.BLOCK_MODE.getOptionValue();
        // With the master "disable blocks" toggle on, an OFF mode means
        // every block is hidden; otherwise the blacklist/whitelist applies.
        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }
        String id = getBlockId(state.getBlock());
        boolean filtered = id != null && isFiltered(mode, filtered_Blocks, id);

        if (id != null && logged_Block_Decisions.add(id)) {
            LOGGER.info("[ReignRender] BLOCK filter: id={} mode={} inList={} -> filtered={}",
                    id, mode.getName(), filtered_Blocks.contains(id), filtered);
        }

        return filtered;
    }

    public static boolean isFluidFiltered(FluidState state) {
        return isFluidFiltered(state, revealDown());
    }

    /**
     * {@link #isFluidFiltered(FluidState)} with the reveal key state already
     * queried.
     */
    public static boolean isFluidFiltered(FluidState state, boolean keyDown) {
        return isFluidFiltered(state, keyDown, hotkey_Behavior(TYPE_FLUIDS, keyDown));
    }

    /**
     * {@link #isFluidFiltered(FluidState, boolean)} with the reveal behavior
     * code for {@link #TYPE_FLUIDS} already evaluated. Per-fluid callers like
     * BlockRenderManager evaluate it once per fluid and pass it down, so the
     * hotkey mode is not read again for every fluid.
     */
    public static boolean isFluidFiltered(FluidState state, boolean keyDown, int behavior) {
        if (state == null) {
            return false;
        }
        // 同 {@link #isBlockFiltered(BlockState, boolean)}：流体网格已按 reveal 状态重建。
        if (behavior == HOTKEY_BEHAVIOR_INACTIVE) {
            return false;
        }
        rebuild();
        BaseOptionListConfigValue mode = RenderConfig.Filters.FLUID_MODE.getOptionValue();
        // With the master "disable fluids" toggle on, an OFF mode means
        // every fluid is hidden; otherwise the blacklist/whitelist applies.
        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }
        String id = getFluidId(state.getFluid());
        return id != null && isFiltered(mode, filtered_Fluids, id);
    }

    /**
     * Returns true when the given block entity should be hidden from rendering.
     * The list stores concrete variant block ids (e.g. "minecraft:white_shulker_box"),
     * so each variant (shulker color, sign wood, ...) can be filtered independently.
     */
    public static boolean isBlockEntityFiltered(BlockEntity blockEntity) {
        return isBlockEntityFiltered(blockEntity, revealDown());
    }

    /**
     * {@link #isBlockEntityFiltered(BlockEntity)} with the reveal key state
     * already queried.
     */
    public static boolean isBlockEntityFiltered(BlockEntity blockEntity, boolean keyDown) {
        return isBlockEntityFiltered(blockEntity, keyDown, hotkey_Behavior(TYPE_BLOCK_ENTITIES, keyDown));
    }

    /**
     * {@link #isBlockEntityFiltered(BlockEntity, boolean)} with the reveal
     * behavior already evaluated once by the caller.
     */
    public static boolean isBlockEntityFiltered(BlockEntity blockEntity, boolean keyDown, int behavior) {
        if (blockEntity == null) {
            return false;
        }
        if (behavior != HOTKEY_BEHAVIOR_NORMAL) {
            return behavior == HOTKEY_BEHAVIOR_FORCE;
        }
        rebuild();
        BaseOptionListConfigValue mode = RenderConfig.Filters.BLOCK_ENTITY_MODE.getOptionValue();
        // With the master "disable block entities" toggle on, an OFF mode means
        // every block entity is hidden; otherwise the blacklist/whitelist applies.
        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }
        String id = getBlockId(blockEntity.getCachedState().getBlock());
        boolean filtered = id != null && isFiltered(mode, filtered_Block_Entities, id);

        if (id != null && logged_Block_Entity_Decisions.add(id)) {
            LOGGER.info("[ReignRender] BLOCK_ENTITY filter: id={} mode={} inList={} -> filtered={}",
                    id, mode.getName(), filtered_Block_Entities.contains(id), filtered);
        }

        return filtered;
    }

    public static boolean isParticleFiltered(ParticleType<?> type) {
        return isParticleFiltered(type, revealDown());
    }

    /**
     * {@link #isParticleFiltered(ParticleType)} with the reveal key state
     * already queried.
     */
    public static boolean isParticleFiltered(ParticleType<?> type, boolean keyDown) {
        return isParticleFiltered(type, keyDown, hotkey_Behavior(TYPE_PARTICLES, keyDown));
    }

    /**
     * {@link #isParticleFiltered(ParticleType, boolean)} with the reveal
     * behavior already evaluated once by the caller. Per-particle callers like
     * ParticleManager evaluate a single code for the whole spawn chain (filter
     * + count cap) instead of one per method.
     */
    public static boolean isParticleFiltered(ParticleType<?> type, boolean keyDown, int behavior) {
        if (type == null) {
            return false;
        }
        if (behavior != HOTKEY_BEHAVIOR_NORMAL) {
            return behavior == HOTKEY_BEHAVIOR_FORCE;
        }
        rebuild();
        BaseOptionListConfigValue mode = RenderConfig.Filters.PARTICLE_MODE.getOptionValue();
        // With the master "disable particles" toggle on, an OFF mode means
        // every particle is hidden; otherwise the blacklist/whitelist applies.
        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }
        String id = getParticleId(type);
        return id != null && isFiltered(mode, filtered_Particles, id);
    }

    /**
     * Returns true when the given armor piece should be hidden. The list stores
     * item registry ids (e.g. "minecraft:diamond_helmet").
     */
    public static boolean isArmorFiltered(ItemStack stack) {
        return isArmorFiltered(stack, revealDown());
    }

    /**
     * {@link #isArmorFiltered(ItemStack)} with the reveal key state already
     * queried.
     */
    public static boolean isArmorFiltered(ItemStack stack, boolean keyDown) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        int behavior = hotkey_Behavior(TYPE_ARMOR, keyDown);
        if (behavior != HOTKEY_BEHAVIOR_NORMAL) {
            return behavior == HOTKEY_BEHAVIOR_FORCE;
        }
        rebuild();
        BaseOptionListConfigValue mode = RenderConfig.Filters.ARMOR_MODE.getOptionValue();
        // With the master "disable armor" toggle on, an OFF mode means
        // every armor piece is hidden; otherwise the blacklist/whitelist applies.
        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }
        String id = getItemId(stack.getItem());
        return id != null && isFiltered(mode, filtered_Armor, id);
    }

    /**
     * Returns true when the name tag with the given text should be hidden.
     * The list stores the displayed name text (semicolon separated, e.g.
     * "123;234"), matched case-insensitively. Only called while the master
     * "disable name tags" toggle is on, so an OFF mode means every name tag
     * is hidden, otherwise the blacklist/whitelist applies.
     */
    public static boolean isNameTagHidden(String name) {
        return isNameTagHidden(name, revealDown());
    }

    /**
     * {@link #isNameTagHidden(String)} with the reveal key state already
     * queried.
     */
    public static boolean isNameTagHidden(String name, boolean keyDown) {
        if (name == null) {
            return false;
        }
        int behavior = hotkey_Behavior(TYPE_NAME_TAGS, keyDown);
        if (behavior != HOTKEY_BEHAVIOR_NORMAL) {
            return behavior == HOTKEY_BEHAVIOR_FORCE;
        }
        rebuild();
        BaseOptionListConfigValue mode = RenderConfig.Filters.NAME_TAG_MODE.getOptionValue();

        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }

        return icMatch(mode, filtered_Name_Tags, name);
    }

    /**
     * Returns true when the player with the given game name should be hidden.
     * The list stores player names (semicolon separated), matched
     * case-insensitively. Only called while the master "hide other players"
     * toggle is on, so an OFF mode means every other player is hidden,
     * otherwise the blacklist/whitelist applies.
     */
    public static boolean isPlayerHidden(String name) {
        return isPlayerHidden(name, revealDown());
    }

    /**
     * {@link #isPlayerHidden(String)} with the reveal key state already
     * queried.
     */
    public static boolean isPlayerHidden(String name, boolean keyDown) {
        return isPlayerHidden(name, keyDown, hotkey_Behavior(TYPE_PLAYERS, keyDown));
    }

    /**
     * {@link #isPlayerHidden(String, boolean)} with the reveal behavior already
     * evaluated once by the caller (per-entity callers like
     * EntityRenderDispatcherMixin evaluate the behavior code once and pass it
     * down, so the hotkey mode is not read again per player entity).
     */
    public static boolean isPlayerHidden(String name, boolean keyDown, int behavior) {
        if (name == null) {
            return false;
        }
        if (behavior != HOTKEY_BEHAVIOR_NORMAL) {
            return behavior == HOTKEY_BEHAVIOR_FORCE;
        }
        rebuild();
        BaseOptionListConfigValue mode = RenderConfig.Filters.PLAYER_MODE.getOptionValue();

        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }

        return icMatch(mode, filtered_Players, name);
    }

    // Reusable floor position for the per-frame biome lookups. BlockPos.ofFloored
    // allocates a new BlockPos on every frame, and the fog color / fog identity
    // paths query the biome once per frame each, so the position is floored into
    // this one instance instead. Fog rendering runs only on the render thread.
    private static final BlockPos.Mutable FOG_POS = new BlockPos.Mutable();

    private static RegistryEntry<Biome> getBiomeAt(ClientWorld world, Vec3d pos) {
        FOG_POS.set(MathHelper.floor(pos.x), MathHelper.floor(pos.y), MathHelper.floor(pos.z));
        return world.getBiome(FOG_POS);
    }

    /**
     * Returns true when the fog currently being drawn should be hidden.
     * <p>
     * Java edition has no fog registry, so a fog instance is identified by a set
     * of "identities":
     * <ul>
     *   <li>the camera submersion type ("water", "lava", "powder_snow", "atmospheric"),</li>
     *   <li>the biome id at the camera position (e.g. "minecraft:swamp"),</li>
     *   <li>the dimension id (e.g. "minecraft:overworld").</li>
     * </ul>
     * A blacklist hides the fog when any identity is listed, a whitelist only
     * shows the fog when at least one identity is listed.
     */
    public static boolean isFogFiltered(CameraSubmersionType type, ClientWorld world, Vec3d pos) {
        return isFogFiltered(type, world, pos, revealDown());
    }

    /**
     * {@link #isFogFiltered(CameraSubmersionType, ClientWorld, Vec3d)} with the
     * reveal key state already queried.
     */
    public static boolean isFogFiltered(CameraSubmersionType type, ClientWorld world, Vec3d pos, boolean keyDown) {
        return isFogFiltered(type, world, pos, keyDown, hotkey_Behavior(TYPE_FOG, keyDown));
    }

    /**
     * {@link #isFogFiltered(CameraSubmersionType, ClientWorld, Vec3d, boolean)}
     * with the reveal behavior already evaluated once by the caller (per-frame
     * callers like FogRendererMixin do a single {@link #hotkey_Behavior} call and
     * pass the code down, so the hotkey mode is not read twice per frame).
     */
    public static boolean isFogFiltered(CameraSubmersionType type, ClientWorld world, Vec3d pos,
                                        boolean keyDown, int behavior) {
        if (type == null) {
            return false;
        }
        if (behavior != HOTKEY_BEHAVIOR_NORMAL) {
            return behavior == HOTKEY_BEHAVIOR_FORCE;
        }
        rebuild();
        BaseOptionListConfigValue mode = RenderConfig.Filters.FOG_MODE.getOptionValue();
        // With the master "disable fog" toggle on, an OFF mode means
        // every fog type is hidden; otherwise the blacklist/whitelist applies.
        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }

        boolean anyInList = filtered_Fogs.contains(FOG_NAMES.get(type));

        if (!anyInList && world != null && pos != null) {
            RegistryEntry<Biome> biome = getBiomeAt(world, pos);
            if (biome != null) {
                anyInList = biome.getKey()
                        .map(biomeKey -> filtered_Fogs.contains(registry_Id_Lookup(BIOME_ID_CACHE, biomeKey, RegistryKey::getValue)))
                        .orElse(false);
            }
        }

        if (!anyInList && world != null) {
            anyInList = filtered_Fogs.contains(registry_Id_Lookup(DIMENSION_ID_CACHE, world.getRegistryKey().getValue(), id -> id));
        }

        // Status-effect fogs: blindness and darkness darken the fog while the
        // effect is active, night vision brightens it, so their fog identity
        // matches while the player has the corresponding status effect.
        if (!anyInList) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                anyInList = (filtered_Fogs.contains(FOG_EFFECT_BLINDNESS) && player.hasStatusEffect(StatusEffects.BLINDNESS))
                        || (filtered_Fogs.contains(FOG_EFFECT_DARKNESS) && player.hasStatusEffect(StatusEffects.DARKNESS))
                        || (filtered_Fogs.contains(FOG_EFFECT_NIGHT_VISION) && player.hasStatusEffect(StatusEffects.NIGHT_VISION));
            }
        }

        if (mode == RenderConfig.Filters.MODE_BLACKLIST) {
            return anyInList;
        }
        // Whitelist: hide the fog when none of its identities is listed.
        return !anyInList;
    }

    /**
     * Fills {@code out} with the candidate identities of the fog currently
     * being drawn, in the same priority order used by {@link #isFogFiltered}:
     * the camera submersion type name, the biome key at the camera position,
     * the dimension key, then the active status-effect fogs. The fog
     * replacement system iterates this list to find the first identity that
     * has a replacement rule, so a rule on any of the identities applies.
     * The caller clears the list before calling (and must not retain it), so
     * no per-frame list is allocated.
     */
    public static void getFogIdentities(CameraSubmersionType type, ClientWorld world, Vec3d pos, List<String> out) {
        if (type != null) {
            String name = FOG_NAMES.get(type);
            if (name != null) {
                out.add(name);
            }
        }
        if (world != null && pos != null) {
            RegistryEntry<Biome> biome = getBiomeAt(world, pos);
            if (biome != null) {
                biome.getKey().ifPresent(key ->
                        out.add(registry_Id_Lookup(BIOME_ID_CACHE, key, RegistryKey::getValue)));
            }
        }
        if (world != null) {
            out.add(registry_Id_Lookup(DIMENSION_ID_CACHE, world.getRegistryKey().getValue(), id -> id));
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            if (player.hasStatusEffect(StatusEffects.BLINDNESS)) {
                out.add(FOG_EFFECT_BLINDNESS);
            }
            if (player.hasStatusEffect(StatusEffects.DARKNESS)) {
                out.add(FOG_EFFECT_DARKNESS);
            }
            if (player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                out.add(FOG_EFFECT_NIGHT_VISION);
            }
        }
    }

    /**
     * Returns true when the fog-thickening applied while a wither boss is
     * nearby should be disabled. The wither effect fog is not part of the fog
     * color: it is a separate flag ({@code BossBarHud.shouldThickenFog}) that
     * shrinks the fog distance, so it is filtered independently of
     * {@link #isFogFiltered}.
     */
    public static boolean isWitherFogFiltered() {
        return isWitherFogFiltered(revealDown());
    }

    /**
     * {@link #isWitherFogFiltered()} with the reveal key state already queried.
     */
    public static boolean isWitherFogFiltered(boolean keyDown) {
        return isWitherFogFiltered(keyDown, hotkey_Behavior(TYPE_FOG, keyDown));
    }

    /**
     * {@link #isWitherFogFiltered(boolean)} with the reveal behavior already
     * evaluated once by the caller.
     */
    public static boolean isWitherFogFiltered(boolean keyDown, int behavior) {
        if (behavior != HOTKEY_BEHAVIOR_NORMAL) {
            return behavior == HOTKEY_BEHAVIOR_FORCE;
        }
        rebuild();
        BaseOptionListConfigValue mode = RenderConfig.Filters.FOG_MODE.getOptionValue();
        // With the master "disable fog" toggle on, an OFF mode means
        // every fog type is hidden; otherwise the blacklist/whitelist applies.
        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }
        boolean inList = filtered_Fogs.contains(FOG_EFFECT_WITHER);
        // BLACKLIST hides the effect while the entry is listed,
        // WHITELIST hides it while the entry is missing.
        return (mode == RenderConfig.Filters.MODE_BLACKLIST) == inList;
    }

    /**
     * Returns true when the night vision fog brightness should be disabled.
     * Night vision is not a fog color of its own: it lerps the fog color
     * towards white (via {@code GameRenderer.getNightVisionStrength}) and also
     * brightens the lightmap. Filtering it forces the strength to 0, which
     * disables both the fog brightening and the lightmap brightening at once.
     * The mode semantics match {@link #isWitherFogFiltered} exactly.
     */
    public static boolean isNightVisionFiltered() {
        return isNightVisionFiltered(revealDown());
    }

    /**
     * {@link #isNightVisionFiltered()} with the reveal key state already
     * queried.
     */
    public static boolean isNightVisionFiltered(boolean keyDown) {
        int behavior = hotkey_Behavior(TYPE_FOG, keyDown);
        if (behavior != HOTKEY_BEHAVIOR_NORMAL) {
            return behavior == HOTKEY_BEHAVIOR_FORCE;
        }
        rebuild();
        BaseOptionListConfigValue mode = RenderConfig.Filters.FOG_MODE.getOptionValue();
        // With the master "disable fog" toggle on, an OFF mode means
        // every fog type is hidden; otherwise the blacklist/whitelist applies.
        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }
        boolean inList = filtered_Fogs.contains(FOG_EFFECT_NIGHT_VISION);
        // BLACKLIST hides the effect while the entry is listed,
        // WHITELIST hides it while the entry is missing.
        return (mode == RenderConfig.Filters.MODE_BLACKLIST) == inList;
    }

    /**
     * Returns true when the pumpkin overlay (the full-screen pumpkin texture
     * drawn while wearing a carved pumpkin) should be hidden. The pumpkin
     * overlay belongs to the fog family: it has a single identity
     * ({@link #FOG_EFFECT_PUMPKIN}), so the fog mode/list semantics apply
     * directly. An OFF mode means every fog (and thus the pumpkin overlay) is
     * hidden, a BLACKLIST hides it while the entry is listed and a WHITELIST
     * hides it while the entry is missing.
     */
    public static boolean isPumpkinOverlayFiltered() {
        return isPumpkinOverlayFiltered(revealDown());
    }

    /**
     * {@link #isPumpkinOverlayFiltered()} with the reveal key state already
     * queried.
     */
    public static boolean isPumpkinOverlayFiltered(boolean keyDown) {
        int behavior = hotkey_Behavior(TYPE_FOG, keyDown);
        if (behavior != HOTKEY_BEHAVIOR_NORMAL) {
            return behavior == HOTKEY_BEHAVIOR_FORCE;
        }
        rebuild();
        BaseOptionListConfigValue mode = RenderConfig.Filters.FOG_MODE.getOptionValue();
        // With the master "disable fog" toggle on, an OFF mode means
        // every fog effect is hidden.
        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }
        boolean inList = filtered_Fogs.contains(FOG_EFFECT_PUMPKIN);
        // BLACKLIST hides the overlay while the entry is listed,
        // WHITELIST hides it while the entry is missing.
        return (mode == RenderConfig.Filters.MODE_BLACKLIST) == inList;
    }

    /**
     * Returns true when the given block is backed by a block entity type that is
     * managed by the block entity filter list (e.g. every shulker box color, sign
     * wood, banner color or copper chest oxidation stage).
     * <p>
     * The result is cached in a flat {@link Set}: the block entity type ids are a
     * static constant, so the registry lookup + {@code supports()} scan only ever
     * runs once. This method is called for every block of every rebuilt section
     * by ChunkMeshFilter (block-category exemption), so the uncached version with
     * its per-call {@link Identifier#tryParse} would be far too expensive there.
     */
    private static volatile Set<Block> managed_Block_Cache = null;

    public static boolean isBlockManagedByBlockEntityFilter(Block block) {
        if (block == null) {
            return false;
        }

        Set<Block> cache = managed_Block_Cache;
        if (cache == null) {
            cache = buildManagedBlockCache();
            managed_Block_Cache = cache;
        }

        return cache.contains(block);
    }

    // Concurrent rebuilds (chunk-builder threads hitting the lazy init at the
    // same time) just build equal sets and publish one of them; harmless.
    private static Set<Block> buildManagedBlockCache() {
        List<BlockEntityType<?>> types = new ArrayList<>();
        for (String id : RenderConfig.Filters.BLOCK_ENTITY_TYPE_IDS) {
            Identifier identifier = Identifier.tryParse(id);
            BlockEntityType<?> type = identifier != null ? Registries.BLOCK_ENTITY_TYPE.get(identifier) : null;

            if (type != null) {
                types.add(type);
            }
        }

        Set<Block> set = new HashSet<>();
        for (Block block : Registries.BLOCK) {
            BlockState state = block.getDefaultState();

            for (BlockEntityType<?> type : types) {
                if (type.supports(state)) {
                    set.add(block);
                    break;
                }
            }
        }

        return set;
    }

    /**
     * Removes block ids that are backed by a managed block entity type from the
     * block filter list (copper chests, signs, banners, ...). Called after
     * loading the config to clean up leftovers from older versions.
     */
    public static void cleanUpBlockFilter() {
        List<String> current = RenderConfig.Filters.FILTERED_BLOCKS.getStrings();
        List<String> blocks = new ArrayList<>();

        for (String rawId : current) {
            Identifier id = Identifier.tryParse(rawId);
            Block block = id != null ? Registries.BLOCK.get(id) : null;

            if (block == null || !isBlockManagedByBlockEntityFilter(block)) {
                blocks.add(rawId);
            }
        }

        if (blocks.size() != current.size()) {
            RenderConfig.Filters.FILTERED_BLOCKS.setStrings(blocks);
        }
    }

    /**
     * Migrates the block entity filter list from type-level ids to concrete
     * variant block ids. Older configs stored BlockEntityType registry ids
     * (e.g. "minecraft:shulker_box") because per-variant filtering was not
     * supported; each of those expands into every item-backed variant block
     * (each shulker color, sign wood, banner color, ...). Duplicate ids are
     * removed as well. Called after loading the config.
     */
    public static void expandBlockEntityFilter() {
        List<String> current = RenderConfig.Filters.FILTERED_BLOCK_ENTITIES.getStrings();
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String rawId : current) {
            Identifier id = Identifier.tryParse(rawId);

            if (id == null || !RenderConfig.Filters.BLOCK_ENTITY_TYPE_IDS.contains(id.toString())) {
                addUnique(result, seen, rawId);
                continue;
            }

            BlockEntityType<?> type = Registries.BLOCK_ENTITY_TYPE.get(id);

            if (type == null) {
                addUnique(result, seen, rawId);
                continue;
            }

            // Expand into every item-backed variant block. Wall variants have no
            // item and are not offered in the picker, so they are skipped too.
            boolean any = false;

            for (Identifier blockId : Registries.BLOCK.getIds()) {
                Block block = Registries.BLOCK.get(blockId);

                if (block == null || !type.supports(block.getDefaultState())) {
                    continue;
                }

                Item item = block.asItem();

                if (item == null || item == Items.AIR) {
                    continue;
                }

                addUnique(result, seen, blockId.toString());
                any = true;
            }

            // No item-backed variant: keep the raw type id as-is
            if (!any) {
                addUnique(result, seen, rawId);
            }
        }

        // Content comparison, not size: a type id that expands into exactly
        // one differently-named variant block keeps the list size at 1 but the
        // entries changed, and the migration must still be written back.
        if (!result.equals(current)) {
            RenderConfig.Filters.FILTERED_BLOCK_ENTITIES.setStrings(result);
        }
    }

    private static void addUnique(List<String> list, Set<String> seen, String id) {
        if (seen.add(id)) {
            list.add(id);
        }
    }
}