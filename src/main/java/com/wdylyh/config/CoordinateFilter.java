package com.wdylyh.config;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.malilib.config.value.BaseOptionListConfigValue;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Coordinate based render filter.
 *
 * The entries live in {@link RenderConfig.Filters#COORD_ENTRIES}, one per line, in one of
 * these formats (additional ids after a semicolon are optional):
 * <pre>
 *   x,y,z               a single block position
 *   x1,y1,z1~x2,y2,z2   the box between two corners
 *   x,y,z;id;id         coordinate target with attached ids
 * </pre>
 * An attached id is matched against the registry that it resolves to (blocks,
 * entities, particles, fluids); ids that resolve to no registry are matched
 * against the text of name tags / player names. Blocks, fluids, block
 * entities and entities are matched by registry id, name tags and player
 * names by text, case-insensitively.
 *
 * The mode {@link RenderConfig.General#COORD_MODE} decides what happens with the objects at
 * a matched coordinate:
 * <ul>
 *   <li>OFF ("关闭"): every object at the coordinate is hidden, ignoring the
 *       attached ids entirely.</li>
 *   <li>WHITELIST ("白名单"): only the objects whose id is attached are kept
 *       visible, everything else is hidden. An entry without attached ids
 *       therefore hides everything at its coordinate.</li>
 *   <li>BLACKLIST ("黑名单"): only the objects whose id is attached are
 *       hidden.</li>
 * </ul>
 *
 * The filter is independent of the per-category master toggles: it applies
 * whenever {@link RenderConfig.Hotkeys#TOGGLE_COORD_FILTER} is on, in every dimension, no
 * matter whether e.g. the entity or block filter is enabled. The reveal
 * hotkey still takes precedence: in its temporary bypass / inactive states
 * the coordinate checks are skipped together with the regular filters, in
 * its force-hide state everything hidden by the regular logic stays hidden.
 *
 * <p>Published through a volatile immutable snapshot (like {@link FilterEngine}), since
 * the block path runs on the ChunkBuilder worker threads while the entity /
 * particle / name tag paths run on the render thread.
 */
public class CoordinateFilter {

    /** Object categories the attached ids can match against. */
    public static final int CAT_BLOCK = 0;
    public static final int CAT_ENTITY = 1;
    public static final int CAT_PARTICLE = 2;
    public static final int CAT_FLUID = 3;
    /** Not a registry id of any tracked registry: matched against name tag / player name text. */
    public static final int CAT_TEXT = 4;

    private static volatile boolean dirty = true;
    private static volatile Entry[] entries = new Entry[0];

    /**
     * Called whenever the coordinate list, the mode or the master toggle
     * change, so the parsed entries are rebuilt on the next query.
     */
    public static void invalidateCaches() {
        dirty = true;
    }

    /**
     * Returns true while the coordinate filter master toggle is on. The
     * per-frame callers short-circuit on this first, so a disabled filter
     * costs one boolean read per object instead of a registry id lookup.
     */
    public static boolean active() {
        return RenderConfig.Hotkeys.TOGGLE_COORD_FILTER.getBooleanValue();
    }

    private static void rebuild() {
        if (dirty) {
            synchronized (CoordinateFilter.class) {
                if (dirty) {
                    List<Entry> parsed = new ArrayList<>();
                    for (String line : RenderConfig.Filters.COORD_ENTRIES.getStrings()) {
                        Entry entry = parse(line);
                        if (entry != null) {
                            parsed.add(entry);
                        }
                    }
                    entries = parsed.toArray(new Entry[0]);
                    dirty = false;
                }
            }
        }
    }

    // ==================== 解析 (Parsing) ====================

    /**
     * Parses a config line into an entry, or returns null when the line is
     * not a valid coordinate target. Invalid lines are skipped silently so a
     * typo in the config never breaks the game.
     */
    private static Entry parse(String line) {
        if (line == null) {
            return null;
        }

        String[] parts = line.split(";", -1);
        int[] box = parseBox(parts[0]);

        if (box == null) {
            return null;
        }

        List<Id> ids = new ArrayList<>(parts.length - 1);
        for (int i = 1; i < parts.length; i++) {
            Id id = parseId(parts[i]);
            if (id != null) {
                ids.add(id);
            }
        }
        return new Entry(box[0], box[1], box[2], box[3], box[4], box[5], ids);
    }

    /**
     * Parses "x,y,z" or "x1,y1,z1~x2,y2,z2" into a normalized
     * [x1,y1,z1,x2,y2,z2] box, or returns null for an invalid coordinate.
     * Public so the coordinate aware replacement system in {@link ReplacementEngine} reuses
     * the exact same region syntax and validation.
     */
    public static int[] parseBox(String coord) {
        String[] corners = coord.split("~", -1);
        int[] p1 = parsePoint(corners[0]);
        int[] p2 = corners.length == 1 ? p1 : parsePoint(corners[1]);

        if (p1 == null || p2 == null) {
            return null;
        }

        return new int[] {
                Math.min(p1[0], p2[0]), Math.min(p1[1], p2[1]), Math.min(p1[2], p2[2]),
                Math.max(p1[0], p2[0]), Math.max(p1[1], p2[1]), Math.max(p1[2], p2[2])
        };
    }

    private static int[] parsePoint(String point) {
        String[] nums = point.split(",", -1);

        if (nums.length != 3)
        {
            // Accept whitespace separated "x y z" (e.g. "0 -63 0") as a
            // fallback, so comma-less coordinates are not silently dropped.
            nums = point.trim().split("\\s+");
        }

        if (nums.length != 3) {
            return null;
        }

        try {
            return new int[] {
                    Integer.parseInt(nums[0].trim()),
                    Integer.parseInt(nums[1].trim()),
                    Integer.parseInt(nums[2].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses an attached id and figures out which render category it belongs
     * to. The id is looked up in the block, entity, particle and fluid
     * registries (a single id may match several, e.g. "minecraft:water" is
     * both a block and a fluid); ids that resolve to none of them are
     * treated as free text for the name tag / player name matching. A blank
     * id is ignored.
     */
    private static Id parseId(String raw) {
        if (raw == null) {
            return null;
        }

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        Identifier id = Identifier.tryParse(trimmed);
        boolean block = id != null && Registries.BLOCK.containsId(id);
        boolean entity = id != null && Registries.ENTITY_TYPE.containsId(id);
        boolean particle = id != null && Registries.PARTICLE_TYPE.containsId(id);
        boolean fluid = id != null && Registries.FLUID.containsId(id);

        // The registry lookup is done on the canonical form so "stone" and
        // "minecraft:stone" are treated as the same block id. An id that
        // resolves to no registry stays the raw text: a lowercase name like
        // "bot" parses as "minecraft:bot" but must still be matched against
        // name tag / player name text ("bot") by the CAT_TEXT category.
        boolean registry = block || entity || particle || fluid;
        return new Id(registry ? id.toString() : trimmed, block, entity, particle, fluid);
    }

    // ==================== 查询 (Queries) ====================

    /**
     * Returns true when the block at the given absolute position should be
     * hidden by the coordinate filter. Called from the chunk building path,
     * so it must stay allocation-free after the initial rebuild.
     */
    public static boolean isBlockHidden(BlockPos pos, BlockState state) {
        if (!active() || RenderConfig.Toggles.DISABLE_BLOCKS.getBooleanValue()) {
            return false;
        }
        String id = state != null ? FilterEngine.getBlockId(state.getBlock()) : null;
        return id != null && hiddenAt(CAT_BLOCK, id, pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Returns true when the block entity at the given absolute position should
     * be hidden. Block entities are matched by the block id of their backing
     * block (same convention as {@link FilterEngine#isBlockEntityFiltered}).
     */
    public static boolean isBlockEntityHidden(BlockEntity be) {
        if (!active() || be == null || RenderConfig.Toggles.DISABLE_BLOCK_ENTITIES.getBooleanValue()) {
            return false;
        }
        BlockPos pos = be.getPos();
        BlockState state = be.getCachedState();
        String id = state != null ? FilterEngine.getBlockId(state.getBlock()) : null;
        return id != null && hiddenAt(CAT_BLOCK, id, pos.getX(), pos.getY(), pos.getZ());
    }

    /** Returns true when the fluid at the given absolute position should be hidden. */
    public static boolean isFluidHidden(BlockPos pos, FluidState state) {
        if (!active() || RenderConfig.Toggles.DISABLE_FLUIDS.getBooleanValue()) {
            return false;
        }
        String id = state != null ? FilterEngine.getFluidId(state.getFluid()) : null;
        return id != null && hiddenAt(CAT_FLUID, id, pos.getX(), pos.getY(), pos.getZ());
    }

    /** Returns true when the entity at its current position should be hidden. */
    public static boolean isEntityHidden(Entity e) {
        if (!active() || e == null || RenderConfig.Toggles.DISABLE_ENTITIES.getBooleanValue()) {
            return false;
        }
        String id = FilterEngine.getEntityId(e.getType());
        return id != null && hiddenAt(CAT_ENTITY, id, e.getX(), e.getY(), e.getZ());
    }

    /** Returns true when the particle spawning at (x, y, z) should be hidden. */
    public static boolean isParticleHidden(double x, double y, double z, ParticleType<?> type) {
        if (!active() || type == null || RenderConfig.Toggles.DISABLE_PARTICLES.getBooleanValue()) {
            return false;
        }
        String id = FilterEngine.getParticleId(type);
        return id != null && hiddenAt(CAT_PARTICLE, id, x, y, z);
    }

    /**
     * Returns true when the name tag / player name at the given position
     * should be hidden. Matched against every attached id as free text,
     * case-insensitively, since a name tag has no registry id.
     */
    public static boolean isNameTagHidden(double x, double y, double z, String name) {
        if (!active() || name == null || RenderConfig.Toggles.DISABLE_NAME_TAGS.getBooleanValue()) {
            return false;
        }
        return hiddenAt(CAT_TEXT, name, x, y, z);
    }

    /**
     * Returns true when the given position is inside a coordinate entry region
     * and the mode is OFF ("关闭"). Used only for objects whose id cannot be
     * resolved (some particles reach the renderer without a ParticleType): the
     * OFF mode hides the whole region regardless of any attached ids, while the
     * blacklist/whitelist modes always need an id to match against.
     */
    public static boolean isRegionHiddenAt(double x, double y, double z) {
        if (!active() || RenderConfig.General.COORD_MODE.getOptionValue() != RenderConfig.Filters.MODE_OFF) {
            return false;
        }
        rebuild();
        if (entries.length == 0) {
            return false;
        }
        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y);
        int bz = MathHelper.floor(z);
        for (Entry e : entries) {
            if (bx >= e.x1 && bx <= e.x2 && by >= e.y1 && by <= e.y2 && bz >= e.z1 && bz <= e.z2) {
                return true;
            }
        }
        return false;
    }

    private static boolean hiddenAt(int cat, String id, double x, double y, double z) {
        rebuild();
        if (entries.length == 0) {
            return false;
        }

        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y);
        int bz = MathHelper.floor(z);

        for (Entry e : entries) {
            if (bx >= e.x1 && bx <= e.x2 && by >= e.y1 && by <= e.y2 && bz >= e.z1 && bz <= e.z2) {
                return hidden(e, cat, id);
            }
        }
        return false;
    }

    /**
     * Decides whether the object (category + id) at a matched coordinate is
     * hidden. See the class comment for the mode semantics.
     */
    private static boolean hidden(Entry e, int cat, String id) {
        BaseOptionListConfigValue mode = RenderConfig.General.COORD_MODE.getOptionValue();

        // OFF ("关闭"): the whole region of the entry is hidden regardless of
        // the attached ids.
        if (mode == RenderConfig.Filters.MODE_OFF) {
            return true;
        }

        boolean inList = inList(e, cat, id);
        // WHITELIST hides ids absent from the entry, BLACKLIST hides ids
        // present in it.
        return (mode == RenderConfig.Filters.MODE_WHITELIST) != inList;
    }

    private static boolean inList(Entry e, int cat, String id) {
        for (Id a : e.ids) {
            switch (cat) {
                case CAT_BLOCK -> {
                    if (a.block && a.text.equals(id)) {
                        return true;
                    }
                }
                case CAT_ENTITY -> {
                    if (a.entity && a.text.equals(id)) {
                        return true;
                    }
                }
                case CAT_PARTICLE -> {
                    if (a.particle && a.text.equals(id)) {
                        return true;
                    }
                }
                case CAT_FLUID -> {
                    if (a.fluid && a.text.equals(id)) {
                        return true;
                    }
                }
                case CAT_TEXT -> {
                    if (a.text.equalsIgnoreCase(id)) {
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    // ==================== 数据 (Data) ====================

    /** One parsed coordinate target plus its attached ids. */
    private static class Entry {
        final int x1, y1, z1, x2, y2, z2;
        final List<Id> ids;

        Entry(int x1, int y1, int z1, int x2, int y2, int z2, List<Id> ids) {
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
            this.ids = ids;
        }
    }

    /** One attached id with the render categories it resolves to. */
    private static class Id {
        final String text;
        final boolean block;
        final boolean entity;
        final boolean particle;
        final boolean fluid;

        Id(String text, boolean block, boolean entity, boolean particle, boolean fluid) {
            this.text = text;
            this.block = block;
            this.entity = entity;
            this.particle = particle;
            this.fluid = fluid;
        }
    }
}