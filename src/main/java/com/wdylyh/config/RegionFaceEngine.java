package com.wdylyh.config;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Coordinate aware face modification ("区域面修改").
 *
 * The entries live in {@link RenderConfig.Filters#REGION_FACE_ENTRIES}, one per line,
 * in the same format as the coordinate filter entries:
 * <pre>
 *   x,y,z               a single block position
 *   x1,y1,z1~x2,y2,z2   the box between two corners
 *   x,y,z;id;id         coordinate target with attached ids
 * </pre>
 * While the region face-mod master switch
 * ({@link RenderConfig.Hotkeys#TOGGLE_REGION_FACE}) is on AND the global face
 * modification ({@link RenderConfig.General#ENABLE_FACE_MOD}) is OFF, the objects
 * whose id is attached to a matching entry (or every object, when the entry
 * carries no ids) render with the textures edited for them in the region
 * face-mod GUI (stored in the generated ReignRender_RegionFace resource pack by
 * {@link RegionFacePacks}). Outside the matched regions the vanilla textures
 * render, so a region edit never leaks into the rest of the world.
 *
 * The covered categories are blocks (including falling blocks), entities,
 * particles and items (dropped and held). Fluids are not covered: 1.21.11
 * resolves their sprites through a hardcoded cache with no per-fluid lookup
 * point, and block entities render entirely through their block entity
 * renderers, so neither offers a texture swap hook.
 *
 * Published through a volatile immutable snapshot like {@link CoordinateFilter}:
 * the block path runs on the ChunkBuilder worker threads while the entity /
 * particle / item paths run on the render thread.
 */
public class RegionFaceEngine {

    /** Object categories the attached ids can match against. */
    public static final int CAT_BLOCK = 0;
    public static final int CAT_ENTITY = 1;
    public static final int CAT_PARTICLE = 2;
    public static final int CAT_ITEM = 3;

    private static volatile boolean dirty = true;
    private static volatile Entry[] entries = new Entry[0];

    /** True when region face modification applies: region switch on, global face mod off. */
    public static boolean active() {
        return RenderConfig.Hotkeys.TOGGLE_REGION_FACE.getBooleanValue()
                && !RenderConfig.General.ENABLE_FACE_MOD.getBooleanValue();
    }

    /**
     * Called whenever the region entries or the master toggles change, so the
     * parsed entries are rebuilt on the next query.
     */
    public static void invalidateCaches() {
        dirty = true;
    }

    private static void rebuild() {
        if (dirty) {
            synchronized (RegionFaceEngine.class) {
                if (dirty) {
                    List<Entry> parsed = new ArrayList<>();
                    for (String line : RenderConfig.Filters.REGION_FACE_ENTRIES.getStrings()) {
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
     * not a valid region target. Invalid lines are skipped silently so a
     * typo in the config never breaks the game.
     */
    private static Entry parse(String line) {
        if (line == null) {
            return null;
        }

        String[] parts = line.split(";", -1);
        int[] box = CoordinateFilter.parseBox(parts[0]);

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
     * Parses an attached id and figures out which region face category it
     * belongs to. The id is looked up in the block, entity, particle and item
     * registries (a single id may match several, e.g. "minecraft:stone" is
     * both a block and an item). A blank id or one resolving to no tracked
     * registry is ignored.
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
        if (id == null) {
            return null;
        }

        boolean block = Registries.BLOCK.containsId(id);
        boolean entity = Registries.ENTITY_TYPE.containsId(id);
        boolean particle = Registries.PARTICLE_TYPE.containsId(id);
        boolean item = Registries.ITEM.containsId(id);

        if (!block && !entity && !particle && !item) {
            return null;
        }

        return new Id(id.toString(), block, entity, particle, item);
    }

    // ==================== 查询 (Queries) ====================

    private static boolean matchesAt(int cat, String id, double x, double y, double z) {
        if (id == null || !active()) {
            return false;
        }

        rebuild();
        int ix = MathHelper.floor(x);
        int iy = MathHelper.floor(y);
        int iz = MathHelper.floor(z);

        for (Entry e : entries) {
            if (ix < e.x1 || iy < e.y1 || iz < e.z1 || ix > e.x2 || iy > e.y2 || iz > e.z2) {
                continue;
            }

            // An entry without ids applies to every object in the region; the
            // per-object hooks still require an edited texture for the id, so
            // unedited objects render normally.
            if (e.ids.isEmpty()) {
                return true;
            }

            for (Id i : e.ids) {
                if (i.matches(cat, id)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** True when the block (or falling block entity) at the position uses its region-edited textures. */
    public static boolean isBlockFaceAt(String blockId, double x, double y, double z) {
        return matchesAt(CAT_BLOCK, blockId, x, y, z);
    }

    /** True when the entity at the position uses its region-edited textures. */
    public static boolean isEntityFaceAt(String entityId, double x, double y, double z) {
        return matchesAt(CAT_ENTITY, entityId, x, y, z);
    }

    /** True when the particle spawned at the position uses its region-edited textures. */
    public static boolean isParticleFaceAt(String particleId, double x, double y, double z) {
        return matchesAt(CAT_PARTICLE, particleId, x, y, z);
    }

    /** True when a dropped / held item at the position uses its region-edited textures. */
    public static boolean isItemFaceAt(String itemId, double x, double y, double z) {
        return matchesAt(CAT_ITEM, itemId, x, y, z);
    }

    /**
     * Bake-time precheck: true when the item id is explicitly attached to some
     * region entry (an entry without ids cannot have an item shadow model
     * baked, since that would mean baking one for every registered item).
     */
    public static boolean isItemListed(String itemId) {
        if (itemId == null) {
            return false;
        }

        rebuild();

        for (Id i : listedIds()) {
            if (i.item() && i.id().equals(itemId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * All ids of the currently configured region entries (normalized), used by
     * the resource pack generator to know which shadow resources to build.
     */
    public static List<Id> listedIds() {
        rebuild();
        List<Id> out = new ArrayList<>();

        for (Entry e : entries) {
            for (Id i : e.ids) {
                if (!out.contains(i)) {
                    out.add(i);
                }
            }
        }

        return out;
    }

    private static final class Entry {
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

    /** One attached id: the normalized registry id plus the categories it resolves to. */
    public record Id(String id, boolean block, boolean entity, boolean particle, boolean item) {

        boolean matches(int cat, String other) {
            boolean catOk = switch (cat) {
                case CAT_BLOCK -> this.block;
                case CAT_ENTITY -> this.entity;
                case CAT_PARTICLE -> this.particle;
                case CAT_ITEM -> this.item;
                default -> false;
            };
            return catOk && this.id.equals(other);
        }
    }
}
