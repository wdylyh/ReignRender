package com.wdylyh.config;

import java.util.Collections;
import java.util.HashMap;
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

/**
 * Centralized matching for the universal render replacement system.
 *
 * Every per-category replacement list in {@link Cfg.F} stores entries
 * of the form {@code "source=target"} (e.g. {@code "minecraft:zombie=minecraft:skeleton"}).
 * While the master switch {@link Cfg.G.REPLACE_ENABLED} is on, the
 * source id renders as the target id. Each category has its own query method
 * that returns the replacement for a given source id, or {@code null} when the
 * source is not replaced (or the system is disabled).
 */
public class Rpl {

    // Per-category replacement maps: source id -> target id. They are
    // published as immutable snapshots: the query methods (idR / txtR) run on
    // both the ChunkBuilder worker threads (SectionBuilder baking the meshes)
    // and the render thread (fluid / armor / ... per-frame replacements), while
    // a config change rebuilds them from whatever thread queries first. A plain
    // HashMap mutated under that concurrency could leave an individual chunk
    // reading a half-cleared map, baking the wrong (or no) replacement into a
    // small area that then never refreshes. Publishing an immutable snapshot
    // through a volatile reference makes every reader see a fully built map.
    private static volatile Map<String, String> pR = Map.of();
    private static volatile Map<String, String> bR = Map.of();
    private static volatile Map<String, String> eR = Map.of();
    private static volatile Map<String, String> fR = Map.of();
    private static volatile Map<String, String> aR = Map.of();
    private static volatile Map<String, String> ntR = Map.of();
    private static volatile Map<String, String> pnR = Map.of();
    private static volatile Map<String, String> flR = Map.of();
    private static volatile Map<String, String> beR = Map.of();
    private static volatile Map<String, String> fbR = Map.of();
    private static volatile Map<String, String> itR = Map.of();

    // Lazy per-target registry resolution caches. The old hot paths resolved
    // the replacement target id through Identifier.tryParse + containsId +
    // registry.get on every affected object (every block / particle / entity /
    // armor piece / fluid). The mapping target id -> registry entry is stable
    // until the config changes, so each id is resolved once and cached. Chunk
    // building runs on the ChunkBuilder worker threads while the render filters
    // run on the render thread, so the caches are concurrent maps. A negative
    // result is cached as BAD so misconfigured ids do not get
    // re-parsed on every object. Cleared together with the id maps when a
    // config change rebuilds the caches.
    private static final Map<String, Object> bTC = new ConcurrentHashMap<>();
    private static final Map<String, Object> flTC = new ConcurrentHashMap<>();
    private static final Map<String, Object> iTC = new ConcurrentHashMap<>();
    private static final Map<String, Object> eTC = new ConcurrentHashMap<>();
    private static final Map<String, Object> pTC = new ConcurrentHashMap<>();
    private static final Object BAD = new Object();

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
        return Cfg.G.REPLACE_ENABLED.getBooleanValue();
    }

    private static void rebuild() {
        if (dirty) {
            synchronized (Rpl.class) {
                if (dirty) {
                    // Build every map locally and publish the finished
                    // snapshots only after all of them are complete. Multiple
                    // threads (the ChunkBuilder workers and the render thread)
                    // can hit dirty=true at once; the double-checked lock
                    // makes exactly one rebuild run, so no reader ever sees a
                    // half-cleared / half-filled map.
                    Map<String, String> p = new HashMap<>();
                    Map<String, String> b = new HashMap<>();
                    Map<String, String> e = new HashMap<>();
                    Map<String, String> f = new HashMap<>();
                    Map<String, String> a = new HashMap<>();
                    Map<String, String> nt = new HashMap<>();
                    Map<String, String> pn = new HashMap<>();
                    Map<String, String> fl = new HashMap<>();
                    Map<String, String> be = new HashMap<>();
                    Map<String, String> fb = new HashMap<>();
                    Map<String, String> it = new HashMap<>();

                    parse(p, Cfg.F.REPLACE_PARTICLES);
                    parse(b, Cfg.F.REPLACE_BLOCKS);
                    parse(e, Cfg.F.REPLACE_ENTITIES);
                    parse(f, Cfg.F.REPLACE_FOGS);
                    parse(a, Cfg.F.REPLACE_ARMOR);
                    parse(nt, Cfg.F.REPLACE_NAME_TAGS);
                    parse(pn, Cfg.F.REPLACE_PLAYER_NAMES);
                    parse(fl, Cfg.F.REPLACE_FLUIDS);
                    parse(be, Cfg.F.REPLACE_BLOCK_ENTITIES);
                    parse(fb, Cfg.F.REPLACE_FALLING_BLOCKS);
                    parse(it, Cfg.F.REPLACE_ITEM_ENTITIES);

                    pR = Collections.unmodifiableMap(p);
                    bR = Collections.unmodifiableMap(b);
                    eR = Collections.unmodifiableMap(e);
                    fR = Collections.unmodifiableMap(f);
                    aR = Collections.unmodifiableMap(a);
                    ntR = Collections.unmodifiableMap(nt);
                    pnR = Collections.unmodifiableMap(pn);
                    flR = Collections.unmodifiableMap(fl);
                    beR = Collections.unmodifiableMap(be);
                    fbR = Collections.unmodifiableMap(fb);
                    itR = Collections.unmodifiableMap(it);

                    bTC.clear();
                    flTC.clear();
                    iTC.clear();
                    eTC.clear();
                    pTC.clear();

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
     * {@link #txtR}). The target keeps its original case so
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
     * Resolves a replacement for a case-insensitive textual key (name tags /
     * player names). Returns the replacement text or null.
     */
    private static String txtR(Map<String, String> map, String key) {
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
    private static String idR(Map<String, String> map, String key) {
        if (!isReplaceEnabled() || key == null) {
            return null;
        }
        rebuild();
        return map.get(key);
    }

    // ==================== 分类查询方法 (Per-category queries) ====================

    /** Returns the replacement particle id for the given particle id, or null. */
    public static String getReplacementParticle(String sourceId) {
        return idR(pR, sourceId);
    }

    /** Number of configured particle replace rules (for diagnostics). */
    public static int particleRuleCount() {
        rebuild();
        return pR.size();
    }

    /** Returns the replacement block id for the given block id, or null. */
    public static String getReplacementBlock(String sourceId) {
        return idR(bR, sourceId);
    }

    /** Returns the replacement entity type id for the given entity id, or null. */
    public static String getReplacementEntity(String sourceId) {
        return idR(eR, sourceId);
    }

    /** Returns the replacement fog identity for the given fog identity, or null. */
    public static String getReplacementFog(String sourceId) {
        return idR(fR, sourceId);
    }

    /** Returns the replacement armor item id for the given item id, or null. */
    public static String getReplacementArmor(String sourceId) {
        return idR(aR, sourceId);
    }

    /** Returns the replacement name tag text for the given name, or null. */
    public static String getReplacementNameTag(String sourceName) {
        return txtR(ntR, sourceName);
    }

    /** Returns the replacement player name text for the given name, or null. */
    public static String getReplacementPlayerName(String sourceName) {
        return txtR(pnR, sourceName);
    }

    /** Returns the replacement fluid id for the given fluid id, or null. */
    public static String getReplacementFluid(String sourceId) {
        return idR(flR, sourceId);
    }

    /** Returns the replacement block entity id for the given id, or null. */
    public static String getReplacementBlockEntity(String sourceId) {
        return idR(beR, sourceId);
    }

    /** Returns the replacement falling block id for the given source, or null. */
    public static String getReplacementFallingBlock(String sourceId) {
        return idR(fbR, sourceId);
    }

    /** Returns the replacement item id for the given item id, or null. */
    public static String getReplacementItem(String sourceId) {
        return idR(itR, sourceId);
    }

    // ==================== 替换目标注册表解析 (Replacement target resolution) ====================

    // Resolves a registry entry from a target id exactly once, then caches it.
    // Returns null for ids that are not valid registry entries (also cached).
    private static <T> T target(Map<String, Object> cache, String targetId, Function<String, T> resolver) {
        Object cached = cache.get(targetId);

        if (cached == null) {
            T resolved = resolver.apply(targetId);
            cache.put(targetId, resolved != null ? resolved : BAD);
            return resolved;
        }

        return cached == BAD ? null : (T) cached;
    }

    private static Block blockOf(String targetId) {
        Identifier id = Identifier.tryParse(targetId);
        return id != null && Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id) : null;
    }

    private static Fluid fluidOf(String targetId) {
        Identifier id = Identifier.tryParse(targetId);
        return id != null && Registries.FLUID.containsId(id) ? Registries.FLUID.get(id) : null;
    }

    private static Item itemOf(String targetId) {
        Identifier id = Identifier.tryParse(targetId);
        return id != null && Registries.ITEM.containsId(id) ? Registries.ITEM.get(id) : null;
    }

    private static EntityType<?> etypeOf(String targetId) {
        Identifier id = Identifier.tryParse(targetId);
        return id != null && Registries.ENTITY_TYPE.containsId(id) ? Registries.ENTITY_TYPE.get(id) : null;
    }

    private static ParticleType<?> ptypeOf(String targetId) {
        Identifier id = Identifier.tryParse(targetId);
        return id != null && Registries.PARTICLE_TYPE.containsId(id) ? Registries.PARTICLE_TYPE.get(id) : null;
    }

    /** Cached Block for a replacement target id, or null when the id is invalid. */
    public static Block getBlockTarget(String targetId) {
        return targetId == null ? null : target(bTC, targetId, Rpl::blockOf);
    }

    /** Cached Fluid for a replacement target id, or null when the id is invalid. */
    public static Fluid getFluidTarget(String targetId) {
        return targetId == null ? null : target(flTC, targetId, Rpl::fluidOf);
    }

    /** Cached Item for a replacement target id, or null when the id is invalid. */
    public static Item getItemTarget(String targetId) {
        return targetId == null ? null : target(iTC, targetId, Rpl::itemOf);
    }

    /** Cached EntityType for a replacement target id, or null when the id is invalid. */
    public static EntityType<?> getEntityTypeTarget(String targetId) {
        return targetId == null ? null : target(eTC, targetId, Rpl::etypeOf);
    }

    /** Cached ParticleType for a replacement target id, or null when the id is invalid. */
    public static ParticleType<?> getParticleTypeTarget(String targetId) {
        return targetId == null ? null : target(pTC, targetId, Rpl::ptypeOf);
    }
}