package com.wdylyh.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified condition system ("条件系统").
 *
 * The entries live in {@link RenderConfig.Conditions#CONDITION_ENTRIES}, one per
 * line. Each line is a semicolon separated list of {@code key=value} fields:
 * <pre>
 *   region=x1,y1,z1~x2,y2,z2   the region (single point "x,y,z" works too)
 *   dist=32                    hide objects farther than 32 blocks from the camera
 *   count=5                    hide objects beyond the 5th of their id per frame
 *   ids=a,b,c                  the registry ids this entry matches
 *   acts=hide,replace:src=dst,face   the actions to apply
 * </pre>
 * A replace rule may pin one render category with a prefix —
 * {@code replace:fallingBlocks:anvil=gold} only replaces falling blocks,
 * {@code replace:blocks:anvil=gold} only the landed block — because an id
 * like "anvil" is shared between the block, the falling entity and the
 * dropped item. A rule without a prefix applies to every category.
 * All fields are optional; the actions act on the set of objects that satisfy
 * the conditions (AND combination: a region limits the position, dist hides
 * objects beyond the distance, count hides objects past the per-frame /
 * per-section cap):
 * <ul>
 *   <li>{@code hide} ("禁止渲染"): hide the matching objects inside the region.
 *       Without ids the whole region is hidden.</li>
 *   <li>{@code keep} ("白名单保留"): inside the region only the listed ids stay
 *       visible. Requires ids.</li>
 *   <li>{@code replace:src=dst} ("替换渲染"): inside the region the src id renders
 *       as dst. May appear multiple times. Requires a region.</li>
 *   <li>{@code face} ("面修改"): inside the region the matching objects render
 *       with the textures edited in the region face GUI.</li>
 * </ul>
 * The region actions (hide/keep/replace/face) require a region; dist/count
 * work with or without one and always require ids.
 *
 * <p>Implementation: the region actions are serialized back into the line
 * formats of the existing engines — {@link CoordinateFilter} (hide/keep, the
 * keep lines carry a {@code "+"} prefix marking the whitelist semantics),
 * {@link ReplacementEngine} (replace) and {@link RegionFaceEngine} (face) — so
 * all the battle-tested matching logic stays untouched. dist/count are
 * evaluated here directly: entity/particle/block-entity paths count per frame
 * ({@link #frame()} resets the budget), the block baking path counts per
 * section ({@link #beginSection()}).
 *
 * <p>Published through volatile immutable snapshots (like {@link FilterEngine}):
 * the block path runs on the ChunkBuilder worker threads while the entity /
 * particle paths run on the render thread.
 */
public class ConditionEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionEngine.class);

    // ==================== 解析数据 (Parsed data) ====================

    /** One parsed condition entry. */
    static final class Cond {
        /** Normalized [x1,y1,z1,x2,y2,z2] box, or null when the entry has no region. */
        final int[] box;
        /** Distance limit in blocks, -1 = none. */
        final int dist;
        /** Per-frame / per-section count limit, -1 = none. */
        final int count;
        /** Normalized object ids (registry ids stay canonical, unknown text stays raw). */
        final List<String> ids;
        final boolean hide;
        final boolean keep;
        final boolean face;
        /** Replacement rules as {category, src, dst} — category null = every category. */
        final List<String[]> replaces;

        Cond(int[] box, int dist, int count, List<String> ids,
             boolean hide, boolean keep, boolean face, List<String[]> replaces) {
            this.box = box;
            this.dist = dist;
            this.count = count;
            this.ids = ids;
            this.hide = hide;
            this.keep = keep;
            this.face = face;
            this.replaces = replaces;
        }
    }

    private static volatile boolean dirty = true;
    private static volatile Cond[] entries = new Cond[0];

    // Serialized view lines for the existing engines, rebuilt together with
    // the entries.
    private static volatile List<String> coordFilterView = List.of();
    private static volatile List<String> coordReplaceView = List.of();
    private static volatile List<String> regionFaceView = List.of();

    /**
     * Called whenever the condition list changes, so the parsed entries and
     * the engine view lines are rebuilt on the next query.
     */
    public static void invalidateCaches() {
        dirty = true;
    }

    private static void rebuild() {
        if (dirty) {
            synchronized (ConditionEngine.class) {
                if (dirty) {
                    List<Cond> parsed = new ArrayList<>();
                    List<String> cf = new ArrayList<>();
                    List<String> cr = new ArrayList<>();
                    List<String> rf = new ArrayList<>();

                    for (String line : RenderConfig.Conditions.CONDITION_ENTRIES.getStrings()) {
                        Cond e;
                        try {
                            e = parse(line);
                        } catch (Exception ex) {
                            // A malformed entry must never break the rebuild:
                            // an exception here would leave every dependent
                            // engine (coordinate filter, replacement, region
                            // face) with its previous / empty snapshot.
                            LOGGER.error("[ReignRender] condition entry failed to parse: {}", line, ex);
                            continue;
                        }
                        if (e == null) {
                            continue;
                        }
                        parsed.add(e);
                        // Pure dist/count entries have no region and never
                        // produce engine view lines; boxToString would NPE on
                        // the null box.
                        String box = e.box == null ? null : boxToString(e.box);

                        // hide/keep -> coordinate filter lines ("region;id;id",
                        // keep lines carry the "+" whitelist prefix).
                        if ((e.hide || e.keep) && box != null) {
                            StringBuilder sb = new StringBuilder();
                            if (e.keep) {
                                sb.append('+');
                            }
                            sb.append(box);
                            for (String id : e.ids) {
                                sb.append(';').append(id);
                            }
                            cf.add(sb.toString());
                        }
                        // replace -> coordinate replacement lines
                        // ("region;[cat:]src=dst;...").
                        if (!e.replaces.isEmpty() && box != null) {
                            StringBuilder sb = new StringBuilder(box);
                            for (String[] r : e.replaces) {
                                sb.append(';');
                                if (r[0] != null) {
                                    sb.append(r[0]).append(':');
                                }
                                sb.append(r[1]).append('=').append(r[2]);
                            }
                            cr.add(sb.toString());
                        }
                        // face -> region face lines ("region;id;id").
                        if (e.face && box != null) {
                            StringBuilder sb = new StringBuilder(box);
                            for (String id : e.ids) {
                                sb.append(';').append(id);
                            }
                            rf.add(sb.toString());
                        }
                    }

                    entries = parsed.toArray(new Cond[0]);
                    coordFilterView = List.copyOf(cf);
                    coordReplaceView = List.copyOf(cr);
                    regionFaceView = List.copyOf(rf);
                    frameCounts.clear();
                    dirty = false;
                }
            }
        }
    }

    // ==================== 引擎视图行 (Engine view lines) ====================

    /** Coordinate filter lines: hide entries verbatim, keep entries with a "+" prefix. */
    public static List<String> coordFilterLines() {
        rebuild();
        return coordFilterView;
    }

    /** Coordinate aware replacement lines ("region;src=dst;..."). */
    public static List<String> coordReplaceLines() {
        rebuild();
        return coordReplaceView;
    }

    /** Region face-mod lines ("region;id;id"). */
    public static List<String> regionFaceLines() {
        rebuild();
        return regionFaceView;
    }

    // ==================== 距离/数量查询 (Distance / count queries) ====================

    // Per-frame count budget for the entity / particle / block-entity render
    // paths, keyed by entry index. Reset in frame() once per frame.
    private static final ConcurrentHashMap<Integer, Integer> frameCounts = new ConcurrentHashMap<>();

    // Per-section count budget for the block baking path. SectionBuilder runs
    // one build per worker thread at a time, so a ThreadLocal map needs no
    // synchronization; beginSection() clears it at the start of every build.
    private static final ThreadLocal<HashMap<Integer, Integer>> sectionCounts =
            ThreadLocal.withInitial(HashMap::new);

    // Camera position sampled once per frame on the render thread (frame())
    // and read by the ChunkBuilder worker threads for the block distance
    // checks. Volatile makes the cross-thread read safe.
    private static volatile double camX, camY, camZ;

    /**
     * Resets the per-frame count budget and samples the camera position for
     * the block baking distance checks. Called from {@link FilterEngine#frame()},
     * exactly once per frame on the render thread.
     */
    public static void frame() {
        frameCounts.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.gameRenderer != null) {
            Camera cam = mc.gameRenderer.getCamera();
            if (cam != null) {
                Vec3d p = cam.getCameraPos();
                camX = p.x;
                camY = p.y;
                camZ = p.z;
            }
        }
    }

    /**
     * Clears the per-section count budget. Called from the SectionBuilder
     * mixin at the start of every chunk section build.
     */
    public static void beginSection() {
        sectionCounts.get().clear();
    }

    /**
     * True when the object with the given id at (x, y, z) is farther from the
     * camera (cx, cy, cz) than a matching distance entry allows. The first
     * matching entry (config order = priority) decides.
     */
    public static boolean isDistanceExceeded(String id, double x, double y, double z,
                                             double cx, double cy, double cz) {
        if (id == null || !RenderConfig.Hotkeys.TOGGLE_DISTANCE_LIMITS.getBooleanValue()) {
            return false;
        }
        rebuild();
        for (Cond e : entries) {
            if (e.dist < 0 || !idMatches(e, id)) {
                continue;
            }
            if (e.box != null && !inBox(e.box, x, y, z)) {
                continue;
            }
            double dx = x - cx;
            double dy = y - cy;
            double dz = z - cz;
            if (dx * dx + dy * dy + dz * dz > (double) e.dist * e.dist) {
                return true;
            }
        }
        return false;
    }

    /**
     * Block baking path variant of {@link #isDistanceExceeded}: the camera
     * position is the per-frame sampled value, because the chunk builder runs
     * on worker threads.
     */
    public static boolean isBlockDistanceExceeded(String id, double x, double y, double z) {
        return isDistanceExceeded(id, x, y, z, camX, camY, camZ);
    }

    /**
     * True when the object with the given id at (x, y, z) is beyond a matching
     * count entry's per-frame budget. Consumes one unit of the first matching
     * entry (config order = priority); the budget resets in {@link #frame()}.
     */
    public static boolean isCountExceeded(String id, double x, double y, double z) {
        if (id == null || !RenderConfig.Hotkeys.TOGGLE_COUNT_LIMITS.getBooleanValue()) {
            return false;
        }
        rebuild();
        for (int i = 0; i < entries.length; i++) {
            Cond e = entries[i];
            if (e.count < 0 || !idMatches(e, id)) {
                continue;
            }
            if (e.box != null && !inBox(e.box, x, y, z)) {
                continue;
            }
            return frameCounts.merge(i, 1, Integer::sum) > e.count;
        }
        return false;
    }

    /**
     * Block baking path variant of {@link #isCountExceeded}: counts per
     * section instead of per frame (a section is baked once and reused across
     * frames, so a per-frame budget would never trip).
     */
    public static boolean isBlockCountExceeded(String id, double x, double y, double z) {
        if (id == null || !RenderConfig.Hotkeys.TOGGLE_COUNT_LIMITS.getBooleanValue()) {
            return false;
        }
        rebuild();
        HashMap<Integer, Integer> used = sectionCounts.get();
        for (int i = 0; i < entries.length; i++) {
            Cond e = entries[i];
            if (e.count < 0 || !idMatches(e, id)) {
                continue;
            }
            if (e.box != null && !inBox(e.box, x, y, z)) {
                continue;
            }
            Integer prev = used.get(i);
            int next = (prev == null ? 0 : prev) + 1;
            used.put(i, next);
            return next > e.count;
        }
        return false;
    }

    // ==================== 解析 (Parsing) ====================

    /**
     * Parses one config line into a {@link Cond}, or returns null when the
     * line carries no valid action. Invalid fields are skipped silently so a
     * typo never breaks the game.
     */
    private static Cond parse(String line) {
        if (line == null) {
            return null;
        }

        int[] box = null;
        int dist = -1;
        int count = -1;
        List<String> ids = new ArrayList<>();
        boolean hide = false;
        boolean keep = false;
        boolean face = false;
        List<String[]> replaces = new ArrayList<>();

        for (String part : line.split(";", -1)) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0 || eq >= part.length() - 1) {
                continue;
            }
            String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String val = part.substring(eq + 1).trim();
            switch (key) {
                case "region", "box" -> box = CoordinateFilter.parseBox(val);
                case "dist", "distance" -> dist = parseNonNegative(val);
                case "count" -> count = parseNonNegative(val);
                case "ids", "id" -> {
                    for (String s : val.split(",")) {
                        String n = normalizeId(s);
                        if (n != null) {
                            ids.add(n);
                        }
                    }
                }
                case "acts", "act", "action", "actions" -> {
                    for (String act : val.split(",")) {
                        act = act.trim();
                        if (act.equalsIgnoreCase("hide") || act.equalsIgnoreCase("blacklist")) {
                            // blacklist 与 hide 同义：区域内隐藏所列 id（无 ids
                            // 时隐藏整个区域）；GUI 用独立的黑名单按钮表达它。
                            hide = true;
                        } else if (act.equalsIgnoreCase("keep") || act.equalsIgnoreCase("whitelist")) {
                            keep = true;
                        } else if (act.equalsIgnoreCase("face") || act.equalsIgnoreCase("facemod")) {
                            face = true;
                        } else if (act.length() > 8 && act.regionMatches(true, 0, "replace:", 0, 8)) {
                            String rule = act.substring(8);
                            // Optional category prefix: "replace:fallingBlocks:anvil=gold"
                            // pins the rule to one render category (an id like
                            // "anvil" is shared between blocks / falling blocks /
                            // item drops); no prefix applies to every category.
                            String cat = null;
                            int colon = rule.indexOf(':');
                            if (colon > 0) {
                                String maybe = rule.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                                if (ReplacementEngine.COORD_REPLACE_CATEGORIES.contains(maybe)) {
                                    cat = maybe;
                                    rule = rule.substring(colon + 1);
                                }
                            }
                            int seq = rule.indexOf('=');
                            if (seq > 0 && seq < rule.length() - 1) {
                                String src = rule.substring(0, seq).trim().toLowerCase(Locale.ROOT);
                                String dst = rule.substring(seq + 1).trim();
                                if (!src.isEmpty() && !dst.isEmpty()) {
                                    replaces.add(new String[] {cat, src, dst});
                                }
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }

        // Region actions require a region: drop them (and log once per edit
        // cycle) when the entry has none.
        boolean hasRegionAction = hide || keep || face || !replaces.isEmpty();
        if (hasRegionAction && box == null) {
            LOGGER.info("[ReignRender] condition entry dropped (region action without region): {}", line);
            hide = false;
            keep = false;
            face = false;
            replaces.clear();
        }
        // keep without ids would hide the whole region: meaningless, dropped.
        if (keep && ids.isEmpty()) {
            keep = false;
        }
        // dist/count without ids cannot match anything sanely (they would
        // affect every object of the world): dropped.
        if (dist >= 0 && ids.isEmpty()) {
            dist = -1;
        }
        if (count >= 0 && ids.isEmpty()) {
            count = -1;
        }

        if (!hide && !keep && !face && replaces.isEmpty() && dist < 0 && count < 0) {
            return null;
        }
        return new Cond(box, dist, count, ids, hide, keep, face, replaces);
    }

    private static int parseNonNegative(String val) {
        try {
            int v = Integer.parseInt(val.trim());
            return v >= 0 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Normalizes an id: registry ids (blocks, entities, particles, fluids,
     * items) are canonicalized through their registry ("stone" and
     * "minecraft:stone" become the same string), ids that resolve to no
     * tracked registry stay raw text for the name tag / player name matching.
     * A blank id returns null.
     */
    private static String normalizeId(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        Identifier id = Identifier.tryParse(t);
        if (id != null && (Registries.BLOCK.containsId(id) || Registries.ENTITY_TYPE.containsId(id)
                || Registries.PARTICLE_TYPE.containsId(id) || Registries.FLUID.containsId(id)
                || Registries.ITEM.containsId(id))) {
            return id.toString();
        }
        return t;
    }

    /** Text match of the object id against the entry's ids. */
    private static boolean idMatches(Cond e, String id) {
        for (String s : e.ids) {
            if (s.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inBox(int[] b, double x, double y, double z) {
        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y);
        int bz = MathHelper.floor(z);
        return bx >= b[0] && bx <= b[3] && by >= b[1] && by <= b[4] && bz >= b[2] && bz <= b[5];
    }

    private static String boxToString(int[] b) {
        return b[0] + "," + b[1] + "," + b[2] + "~" + b[3] + "," + b[4] + "," + b[5];
    }
}
