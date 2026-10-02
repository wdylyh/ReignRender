package com.wdylyh.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import fi.dy.masa.malilib.config.options.ConfigStringList;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Centralized matching for the universal render replacement system.
 *
 * Every per-category replacement list in {@link RenderConfig.Filters} stores entries
 * of the form {@code "source=target"} (e.g. {@code "minecraft:zombie=minecraft:skeleton"}).
 * While the master switch {@link RenderConfig.General.REPLACE_ENABLED} is on, the
 * source id renders as the target id. Each category has its own query method
 * that returns the replacement for a given source id, or {@code null} when the
 * source is not replaced (or the system is disabled).
 */
public class ReplacementEngine {

    // Per-category replacement maps: source id -> target id. They are
    // published as immutable snapshots: the query methods (id_Replacement / text_Replacement) run on
    // both the ChunkBuilder worker threads (SectionBuilder baking the meshes)
    // and the render thread (fluid / armor / ... per-frame replacements), while
    // a config change rebuilds them from whatever thread queries first. A plain
    // HashMap mutated under that concurrency could leave an individual chunk
    // reading a half-cleared map, baking the wrong (or no) replacement into a
    // small area that then never refreshes. Publishing an immutable snapshot
    // through a volatile reference makes every reader see a fully built map.
    private static volatile Map<String, String> particle_Replace = Map.of();
    private static volatile Map<String, String> block_Replace = Map.of();
    private static volatile Map<String, String> entity_Replace = Map.of();
    private static volatile Map<String, String> fog_Replace = Map.of();
    private static volatile Map<String, String> armor_Replace = Map.of();
    private static volatile Map<String, String> nameTag_Replace = Map.of();
    private static volatile Map<String, String> playerName_Replace = Map.of();
    private static volatile Map<String, String> fluid_Replace = Map.of();
    private static volatile Map<String, String> blockEntity_Replace = Map.of();
    private static volatile Map<String, String> fallingBlock_Replace = Map.of();
    private static volatile Map<String, String> item_Replace = Map.of();
    private static volatile Map<String, String> heldItem_Replace = Map.of();
    private static volatile Map<String, String> hudElement_Replace = Map.of();

    // Coordinate aware replacement entries ("region;source=target;..."). Like
    // the per-category maps they are published through a volatile immutable
    // snapshot: the region matcher runs on the ChunkBuilder worker threads
    // (block / fluid / block entity baking) and the render thread (entities,
    // particles, fog, ...) alike.
    private static volatile CoordEntry[] coordinate_Entries = new CoordEntry[0];

    // Lazy per-target registry resolution caches. The old hot paths resolved
    // the replacement target id through Identifier.tryParse + containsId +
    // registry.get on every affected object (every block / particle / entity /
    // armor piece / fluid). The mapping target id -> registry entry is stable
    // until the config changes, so each id is resolved once and cached. Chunk
    // building runs on the ChunkBuilder worker threads while the render filters
    // run on the render thread, so the caches are concurrent maps. A negative
    // result is cached as INVALID_TARGET_MARKER so misconfigured ids do not get
    // re-parsed on every object. Cleared together with the id maps when a
    // config change rebuilds the caches.
    private static final Map<String, Object> block_Target_Cache = new ConcurrentHashMap<>();
    private static final Map<String, Object> fluid_Target_Cache = new ConcurrentHashMap<>();
    private static final Map<String, Object> item_Target_Cache = new ConcurrentHashMap<>();
    private static final Map<String, Object> entity_Target_Cache = new ConcurrentHashMap<>();
    private static final Map<String, Object> particle_Target_Cache = new ConcurrentHashMap<>();
    private static final Object INVALID_TARGET_MARKER = new Object();

    private static volatile boolean dirty = true;

    /**
     * Called whenever the replace master switch or any replacement list
     * changes, so the cached maps are rebuilt on the next query.
     */
    public static void invalidateCaches() {
        dirty = true;
    }

    /**
     * Returns true when the replacement system is currently active (the master
     * switch is on). When false, every {@code getReplacement*} method returns
     * null immediately.
     */
    public static boolean isReplaceEnabled() {
        return RenderConfig.General.REPLACE_ENABLED.getBooleanValue();
    }

    /**
     * Returns true when the coordinate aware replacement rules are active: the
     * coordinate replacement toggle must be on AND the global replacement
     * master switch must be OFF — the regional mechanism only applies while
     * the global one is off (they are mutually exclusive: with the global
     * replacement enabled the region rules step aside entirely).
     * <p>
     * Public so per-section callers (SectionBuilder baking) can evaluate it
     * once per section instead of once per block.
     */
    public static boolean isCoordReplaceActive() {
        return !isReplaceEnabled() && RenderConfig.Hotkeys.TOGGLE_COORD_REPLACE.getBooleanValue();
    }

    private static boolean coordReplaceActive() {
        return isCoordReplaceActive();
    }

    /**
     * True when ANY replacement mechanism is active (the global master switch
     * or the coordinate replacement toggle). Per-object callers (entity
     * dispatch, particle spawn, item drops, armor, ...) use this once per
     * object to skip the whole replacement chain — the {@code isReplaceBlocked}
     * check and the {@code getReplacement*At / getReplacement*} pair — when
     * no replacement can possibly match.
     */
    public static boolean anyReplaceActive() {
        return isReplaceEnabled() || isCoordReplaceActive();
    }

    private static void rebuild() {
        if (dirty) {
            synchronized (ReplacementEngine.class) {
                if (dirty) {
                    // Build every map locally and publish the finished
                    // snapshots only after all of them are complete. Multiple
                    // threads (the ChunkBuilder workers and the render thread)
                    // can hit dirty=true at once; the double-checked lock
                    // makes exactly one rebuild run, so no reader ever sees a
                    // half-cleared / half-filled map.
                    Map<String, String> particleMap = new HashMap<>();
                    Map<String, String> blockMap = new HashMap<>();
                    Map<String, String> entityMap = new HashMap<>();
                    Map<String, String> fogMap = new HashMap<>();
                    Map<String, String> armorMap = new HashMap<>();
                    Map<String, String> nameTagMap = new HashMap<>();
                    Map<String, String> playerNameMap = new HashMap<>();
                    Map<String, String> fluidMap = new HashMap<>();
                    Map<String, String> blockEntityMap = new HashMap<>();
                    Map<String, String> fallingBlockMap = new HashMap<>();
                    Map<String, String> itemMap = new HashMap<>();
                    Map<String, String> heldItemMap = new HashMap<>();
                    Map<String, String> hudElementMap = new HashMap<>();

                    parse(particleMap, RenderConfig.Filters.REPLACE_PARTICLES);
                    parse(blockMap, RenderConfig.Filters.REPLACE_BLOCKS);
                    parse(entityMap, RenderConfig.Filters.REPLACE_ENTITIES);
                    parse(fogMap, RenderConfig.Filters.REPLACE_FOGS);
                    parse(armorMap, RenderConfig.Filters.REPLACE_ARMOR);
                    parse(nameTagMap, RenderConfig.Filters.REPLACE_NAME_TAGS);
                    parse(playerNameMap, RenderConfig.Filters.REPLACE_PLAYER_NAMES);
                    parse(fluidMap, RenderConfig.Filters.REPLACE_FLUIDS);
                    parse(blockEntityMap, RenderConfig.Filters.REPLACE_BLOCK_ENTITIES);
                    parse(fallingBlockMap, RenderConfig.Filters.REPLACE_FALLING_BLOCKS);
                    parse(itemMap, RenderConfig.Filters.REPLACE_ITEM_ENTITIES);
                    parse(heldItemMap, RenderConfig.Filters.REPLACE_HELD_ITEMS);
                    parse(hudElementMap, RenderConfig.Filters.REPLACE_HUD_ELEMENTS);

                    // Coordinate aware entries: parse the coordinates + rules
                    // and publish the immutable snapshot together with the
                    // per-category maps above. The lines come from the
                    // condition system's replace actions.
                    List<CoordEntry> coordList = new ArrayList<>();
                    for (String line : ConditionEngine.coordReplaceLines()) {
                        CoordEntry coordEntry = parseCoord(line);
                        if (coordEntry != null) {
                            coordList.add(coordEntry);
                        }
                    }

                    particle_Replace = Collections.unmodifiableMap(particleMap);
                    block_Replace = Collections.unmodifiableMap(blockMap);
                    entity_Replace = Collections.unmodifiableMap(entityMap);
                    fog_Replace = Collections.unmodifiableMap(fogMap);
                    armor_Replace = Collections.unmodifiableMap(armorMap);
                    nameTag_Replace = Collections.unmodifiableMap(nameTagMap);
                    playerName_Replace = Collections.unmodifiableMap(playerNameMap);
                    fluid_Replace = Collections.unmodifiableMap(fluidMap);
                    blockEntity_Replace = Collections.unmodifiableMap(blockEntityMap);
                    fallingBlock_Replace = Collections.unmodifiableMap(fallingBlockMap);
                    item_Replace = Collections.unmodifiableMap(itemMap);
                    heldItem_Replace = Collections.unmodifiableMap(heldItemMap);
                    hudElement_Replace = Collections.unmodifiableMap(hudElementMap);
                    coordinate_Entries = coordList.toArray(new CoordEntry[0]);

                    block_Target_Cache.clear();
                    fluid_Target_Cache.clear();
                    item_Target_Cache.clear();
                    entity_Target_Cache.clear();
                    particle_Target_Cache.clear();

                    dirty = false;
                }
            }
        }
    }

    /**
     * Parses a string list of "source=target" entries into the given map.
     * Entries without an '=' or with an empty source (or empty target) are
     * skipped. The source is always lowercased so matching is
     * case-insensitive: registry ids are lowercase anyway, and the name-based
     * categories (name tags, player names) rely on it (see
     * {@link #text_Replacement}). The target keeps its original case so
     * replacement names are displayed as configured.
     */
    private static void parse(Map<String, String> map, ConfigStringList config) {
        for (String raw : config.getStrings()) {
            if (raw == null) {
                continue;
            }
            int eq = raw.indexOf('=');
            if (eq <= 0 || eq >= raw.length() - 1) {
                continue;
            }
            String source = raw.substring(0, eq).trim();
            String target = raw.substring(eq + 1).trim();
            if (!source.isEmpty() && !target.isEmpty()) {
                map.put(source.toLowerCase(Locale.ROOT), target);
            }
        }
    }

    /**
     * Parses one coordinate replacement line into a {@link CoordEntry}, or
     * returns null when the line is invalid. A line is a coordinate target
     * (the same "x,y,z" / "x1,y1,z1~x2,y2,z2" syntax as the coordinate filter
     * entries, reused verbatim through {@link CoordinateFilter#parseBox}) followed by
     * one or more "source=target" rules:
     * <pre>
     *   x,y,z;minecraft:stone=minecraft:glass
     *   x1,y1,z1~x2,y2,z2;minecraft:stone=minecraft:glass;minecraft:dirt=minecraft:sand
     * </pre>
     * A rule's source decides which render category it applies to the same way
     * the attached ids of the coordinate filter do; sources that resolve to no
     * registry match name tags / player names as text. The source is
     * lowercased exactly like the global lists, the target keeps its case.
     * Lines without a valid coordinate or without any valid rule are skipped.
     */
    private static CoordEntry parseCoord(String line) {
        if (line == null) {
            return null;
        }

        String[] parts = line.split(";", -1);
        int[] box = CoordinateFilter.parseBox(parts[0]);
        if (box == null) {
            return null;
        }

        Map<String, Map<String, String>> rules = new HashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String rule = parts[i];
            if (rule == null) {
                continue;
            }
            // Optional category prefix: "blocks:stone=glass" pins the rule to
            // one render category, "stone=glass" applies to every category.
            String cat = null;
            int colon = rule.indexOf(':');
            if (colon > 0) {
                String maybe = rule.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                if (COORD_REPLACE_CATEGORIES.contains(maybe)) {
                    cat = maybe;
                    rule = rule.substring(colon + 1);
                }
            }
            int eq = rule.indexOf('=');
            if (eq <= 0 || eq >= rule.length() - 1) {
                continue;
            }
            String source = rule.substring(0, eq).trim();
            String target = rule.substring(eq + 1).trim();
            if (!source.isEmpty() && !target.isEmpty()) {
                rules.computeIfAbsent(cat, k -> new HashMap<>())
                        .put(source.toLowerCase(Locale.ROOT), target);
            }
        }
        if (rules.isEmpty()) {
            return null;
        }
        return new CoordEntry(box[0], box[1], box[2], box[3], box[4], box[5], Collections.unmodifiableMap(rules));
    }

    /**
     * Resolves a replacement for a case-insensitive textual key (name tags /
     * player names). Returns the replacement text or null.
     */
    private static String text_Replacement(Map<String, String> map, String key) {
        if (!isReplaceEnabled() || key == null) {
            return null;
        }
        rebuild();
        // equalsIgnoreCase is allocation-free, unlike key.toLowerCase(...) which
        // would allocate a new String for every rendered name tag / player name
        // on every frame. The text maps only hold a handful of rules, so the
        // linear scan is cheap.
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Resolves a replacement for an exact id key. Returns the replacement id
     * or null.
     */
    private static String id_Replacement(Map<String, String> map, String key) {
        if (!isReplaceEnabled() || key == null) {
            return null;
        }
        rebuild();
        return hitIn(map, key);
    }

    /**
     * Looks up a rule by exact id within the query's category first, then in
     * the "no prefix" bucket (which applies to every category). The game
     * always queries with the fully namespaced id ("minecraft:grass_block"),
     * while players may write the bare id ("grass_block") in the lists, so an
     * exact miss retries with the "minecraft:" prefix stripped. Bare keys
     * never match ids of other namespaces, keeping custom mod ids unambiguous.
     */
    private static String hitRule(Map<String, Map<String, String>> rules, String category, String key) {
        if (key == null) {
            return null;
        }
        // Parsers store the category prefix lowercased (COORD_REPLACE_CATEGORIES
        // is all lowercase), while the mixins query with the camel-case
        // FilterEngine.TYPE_* constants: normalize before the map lookup.
        String cat = category == null ? null : category.toLowerCase(Locale.ROOT);
        String value = hitIn(rules.get(cat), key);
        if (value != null) {
            return value;
        }
        return hitIn(rules.get(null), key);
    }

    /** Single-bucket variant of {@link #hitRule}: exact match, then bare-id retry. */
    private static String hitIn(Map<String, String> bucket, String key) {
        if (bucket == null) {
            return null;
        }
        String value = bucket.get(key);
        if (value != null) {
            return value;
        }
        String bare = key.startsWith("minecraft:") ? key.substring("minecraft:".length()) : null;
        return (bare == null) ? null : bucket.get(bare);
    }

    // ==================== 分类查询方法 (Per-category queries) ====================

    /** Returns the replacement particle id for the given particle id, or null. */
    public static String getReplacementParticle(String sourceId) {
        return id_Replacement(particle_Replace, sourceId);
    }

    /** Number of configured particle replace rules (for diagnostics). */
    public static int particleRuleCount() {
        rebuild();
        return particle_Replace.size();
    }

    /** Number of configured block replace rules (for diagnostics). */
    public static int blockRuleCount() {
        rebuild();
        return block_Replace.size();
    }

    /** Number of parsed coordinate aware replace entries (for diagnostics). */
    public static int coordEntryCount() {
        rebuild();
        return coordinate_Entries.length;
    }

    /** Returns the replacement block id for the given block id, or null. */
    public static String getReplacementBlock(String sourceId) {
        return id_Replacement(block_Replace, sourceId);
    }

    /** Returns the replacement entity type id for the given entity id, or null. */
    public static String getReplacementEntity(String sourceId) {
        return id_Replacement(entity_Replace, sourceId);
    }

    /** Returns the replacement fog identity for the given fog identity, or null. */
    public static String getReplacementFog(String sourceId) {
        return id_Replacement(fog_Replace, sourceId);
    }

    /** Returns the replacement armor item id for the given item id, or null. */
    public static String getReplacementArmor(String sourceId) {
        return id_Replacement(armor_Replace, sourceId);
    }

    /** Returns the replacement name tag text for the given name, or null. */
    public static String getReplacementNameTag(String sourceName) {
        return text_Replacement(nameTag_Replace, sourceName);
    }

    /** Returns the replacement player name text for the given name, or null. */
    public static String getReplacementPlayerName(String sourceName) {
        return text_Replacement(playerName_Replace, sourceName);
    }

    /** Returns the replacement fluid id for the given fluid id, or null. */
    public static String getReplacementFluid(String sourceId) {
        return id_Replacement(fluid_Replace, sourceId);
    }

    /** Returns the replacement block entity id for the given id, or null. */
    public static String getReplacementBlockEntity(String sourceId) {
        return id_Replacement(blockEntity_Replace, sourceId);
    }

    /** Returns the replacement falling block id for the given source, or null. */
    public static String getReplacementFallingBlock(String sourceId) {
        return id_Replacement(fallingBlock_Replace, sourceId);
    }

    /** Returns the replacement item id for the given item id, or null. */
    public static String getReplacementItem(String sourceId) {
        return id_Replacement(item_Replace, sourceId);
    }

    /** Returns the replacement item id for the given held item id, or null. */
    public static String getReplacementHeldItem(String sourceId) {
        return id_Replacement(heldItem_Replace, sourceId);
    }

    /** Returns the replacement HUD element id for the given element id, or null. */
    public static String getReplacementHudElement(String sourceId) {
        return id_Replacement(hudElement_Replace, sourceId);
    }

    // ==================== 坐标替换查询 (Coordinate aware queries) ====================
    //
    // Every *At method resolves a replacement the same way its coordinate
    // free counterpart does, but checks the coordinate replacement entries
    // first. When the position falls inside a region whose entry carries a
    // matching rule the coordinate rule wins; otherwise (no containing region
    // at all, or the first containing region has no rule for the source) the
    // caller falls back to its global per-category list.

    /** Returns the coordinate replacement block id for the source at (x, y, z), or null. */
    // Each query passes its render category (FilterEngine.TYPE_*), so a rule
    // prefixed with a category only answers that category's queries.

    public static String getReplacementBlockAt(String sourceId, double x, double y, double z, String category) {
        return coordinate_Replace_Id(sourceId, x, y, z, category);
    }

    public static String getReplacementFluidAt(String sourceId, double x, double y, double z, String category) {
        return coordinate_Replace_Id(sourceId, x, y, z, category);
    }

    public static String getReplacementEntityAt(String sourceId, double x, double y, double z, String category) {
        return coordinate_Replace_Id(sourceId, x, y, z, category);
    }

    public static String getReplacementParticleAt(String sourceId, double x, double y, double z, String category) {
        return coordinate_Replace_Id(sourceId, x, y, z, category);
    }

    public static String getReplacementBlockEntityAt(String sourceId, double x, double y, double z, String category) {
        return coordinate_Replace_Id(sourceId, x, y, z, category);
    }

    public static String getReplacementFallingBlockAt(String sourceId, double x, double y, double z, String category) {
        return coordinate_Replace_Id(sourceId, x, y, z, category);
    }

    public static String getReplacementItemAt(String sourceId, double x, double y, double z, String category) {
        return coordinate_Replace_Id(sourceId, x, y, z, category);
    }

    public static String getReplacementHeldItemAt(String sourceId, double x, double y, double z, String category) {
        return coordinate_Replace_Id(sourceId, x, y, z, category);
    }

    public static String getReplacementFogAt(String sourceId, double x, double y, double z, String category) {
        return coordinate_Replace_Id(sourceId, x, y, z, category);
    }

    public static String getReplacementArmorAt(String sourceId, double x, double y, double z, String category) {
        return coordinate_Replace_Id(sourceId, x, y, z, category);
    }

    public static String getReplacementNameTagAt(String sourceName, double x, double y, double z, String category) {
        return coordinate_Replace_Text(sourceName, x, y, z, category);
    }

    public static String getReplacementPlayerNameAt(String sourceName, double x, double y, double z, String category) {
        return coordinate_Replace_Text(sourceName, x, y, z, category);
    }

    /**
     * Resolves an exact id rule inside the first region that contains
     * (x, y, z). Returns the rule target, or null when no region contains the
     * position or the first containing region has no rule for the key (the
     * caller then falls back to the global list).
     */
    private static String coordinate_Replace_Id(String key, double x, double y, double z, String category) {
        if (!coordReplaceActive() || key == null) {
            return null;
        }
        rebuild();
        if (coordinate_Entries.length == 0) {
            return null;
        }
        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y);
        int bz = MathHelper.floor(z);
        for (CoordEntry e : coordinate_Entries) {
            if (bx >= e.x1 && bx <= e.x2 && by >= e.y1 && by <= e.y2 && bz >= e.z1 && bz <= e.z2) {
                return hitRule(e.rules, category, key);
            }
        }
        return null;
    }

    /**
     * Resolves a case-insensitive text rule (name tags / player names) inside
     * the first region that contains (x, y, z). Same return contract as
     * {@link #coordinate_Replace_Id}
     */
    private static String coordinate_Replace_Text(String key, double x, double y, double z, String category) {
        if (!coordReplaceActive() || key == null) {
            return null;
        }
        // parseCoord stores the category keys lowercased ("nametags"), so the
        // camel-case TYPE_* constants ("nameTags") must be lowercased here too,
        // exactly like hitRule does for the id path.
        String cat = category != null ? category.toLowerCase(Locale.ROOT) : null;
        rebuild();
        if (coordinate_Entries.length == 0) {
            return null;
        }
        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y);
        int bz = MathHelper.floor(z);
        for (CoordEntry e : coordinate_Entries) {
            if (bx >= e.x1 && bx <= e.x2 && by >= e.y1 && by <= e.y2 && bz >= e.z1 && bz <= e.z2) {
                String value = hitText(e.rules.get(cat), key);
                return value != null ? value : hitText(e.rules.get(null), key);
            }
        }
        return null;
    }

    /** Case-insensitive text match within one category bucket, or null. */
    private static String hitText(Map<String, String> bucket, String key) {
        if (bucket == null) {
            return null;
        }
        // equalsIgnoreCase is allocation-free, unlike key.toLowerCase(...) which
        // would allocate a new String for every rendered name tag / player name
        // on every frame. The text buckets only hold a handful of rules, so the
        // linear scan is cheap.
        for (Map.Entry<String, String> rule : bucket.entrySet()) {
            if (rule.getKey().equalsIgnoreCase(key)) {
                return rule.getValue();
            }
        }
        return null;
    }

    // ==================== 替换目标注册表解析 (Replacement target resolution) ====================

    // Resolves a registry entry from a target id exactly once, then caches it.
    // Returns null for ids that are not valid registry entries (also cached).
    private static <T> T target(Map<String, Object> cache, String targetId, Function<String, T> resolver) {
        Object cached = cache.get(targetId);

        if (cached == null) {
            T resolved = resolver.apply(targetId);
            cache.put(targetId, resolved != null ? resolved : INVALID_TARGET_MARKER);
            return resolved;
        }

        return cached == INVALID_TARGET_MARKER ? null : (T) cached;
    }

    /**
     * Parses an id, allowing bare names. Registry ids are usually written
     * with the "minecraft:" namespace ("minecraft:stone"), but the lists also
     * accept the bare name ("stone"), which {@link Identifier#tryParse} alone
     * rejects. Like the id picker ({@code IconGridPicker}) a failed parse retries
     * with the "minecraft:" prefix; ids with an explicit namespace are used
     * verbatim so custom mod ids stay unambiguous.
     */
    private static Identifier tryParseId(String raw) {
        if (raw == null) {
            return null;
        }
        Identifier id = Identifier.tryParse(raw);
        if (id == null && raw.indexOf(':') < 0) {
            id = Identifier.tryParse("minecraft:" + raw);
        }
        return id;
    }

    private static Block blockOf(String targetId) {
        Identifier id = tryParseId(targetId);
        return id != null && Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id) : null;
    }

    private static Fluid fluidOf(String targetId) {
        Identifier id = tryParseId(targetId);
        return id != null && Registries.FLUID.containsId(id) ? Registries.FLUID.get(id) : null;
    }

    private static Item itemOf(String targetId) {
        Identifier id = tryParseId(targetId);
        return id != null && Registries.ITEM.containsId(id) ? Registries.ITEM.get(id) : null;
    }

    private static EntityType<?> etypeOf(String targetId) {
        Identifier id = tryParseId(targetId);
        return id != null && Registries.ENTITY_TYPE.containsId(id) ? Registries.ENTITY_TYPE.get(id) : null;
    }

    private static ParticleType<?> ptypeOf(String targetId) {
        Identifier id = tryParseId(targetId);
        return id != null && Registries.PARTICLE_TYPE.containsId(id) ? Registries.PARTICLE_TYPE.get(id) : null;
    }

    /** Cached Block for a replacement target id, or null when the id is invalid. */
    public static Block getBlockTarget(String targetId) {
        return targetId == null ? null : target(block_Target_Cache, targetId, ReplacementEngine::blockOf);
    }

    /** Cached Fluid for a replacement target id, or null when the id is invalid. */
    public static Fluid getFluidTarget(String targetId) {
        return targetId == null ? null : target(fluid_Target_Cache, targetId, ReplacementEngine::fluidOf);
    }

    /** Cached Item for a replacement target id, or null when the id is invalid. */
    public static Item getItemTarget(String targetId) {
        return targetId == null ? null : target(item_Target_Cache, targetId, ReplacementEngine::itemOf);
    }

    /** Cached EntityType for a replacement target id, or null when the id is invalid. */
    public static EntityType<?> getEntityTypeTarget(String targetId) {
        return targetId == null ? null : target(entity_Target_Cache, targetId, ReplacementEngine::etypeOf);
    }

    /** Cached ParticleType for a replacement target id, or null when the id is invalid. */
    public static ParticleType<?> getParticleTypeTarget(String targetId) {
        return targetId == null ? null : target(particle_Target_Cache, targetId, ReplacementEngine::ptypeOf);
    }

    // ==================== 数据 (Data) ====================

    /**
     * Registry ids are shared between render categories (a falling anvil, the
     * landed anvil block and the dropped anvil item all resolve to
     * "minecraft:anvil"), so a coordinate rule may pin itself to one category
     * with a prefix — {@code replace:fallingBlocks:anvil=gold} only affects
     * falling blocks. The category keys mirror {@link FilterEngine#TYPE_*}
     * lowercased: every parser compares the prefix case-insensitively via
     * {@code toLowerCase}, and {@link #hitRule} lowercases the queried
     * category, so the all-lowercase keys here are the one canonical form.
     * Rules without a prefix apply to every category.
     */
    public static final java.util.Set<String> COORD_REPLACE_CATEGORIES = java.util.Set.of(
            "blocks", "entities", "particles", "fluids", "blockentities", "fallingblocks",
            "itementities", "helditems", "armor", "fog", "nametags", "playernames");

    /** One parsed coordinate replacement region plus its "source=target" rules. */
    private static class CoordEntry {
        final int x1, y1, z1, x2, y2, z2;
        /** Rules keyed by category (null = the "no prefix" bucket, valid for every category). */
        final Map<String, Map<String, String>> rules;

        CoordEntry(int x1, int y1, int z1, int x2, int y2, int z2, Map<String, Map<String, String>> rules) {
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
            this.rules = rules;
        }
    }
}
