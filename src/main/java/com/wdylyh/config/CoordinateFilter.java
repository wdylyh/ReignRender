package com.wdylyh.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
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
 * The entries are serialized from the condition system's hide/keep actions by
 * {@link ConditionEngine#coordFilterLines()}, one per line, in one of these
 * formats (additional ids after a semicolon are optional):
 * <pre>
 *   x,y,z               a single block position
 *   x1,y1,z1~x2,y2,z2   the box between two corners
 *   x,y,z;id;id         coordinate target with attached ids
 * </pre>
 * A leading {@code "+"} marks a keep (whitelist) entry; unprefixed lines are
 * hide (blacklist) entries. An attached id is matched against the registry
 * that it resolves to (blocks, entities, particles, fluids); ids that resolve
 * to no registry are matched against the text of name tags / player names.
 * Blocks, fluids, block entities and entities are matched by registry id, name
 * tags and player names by text, case-insensitively.
 *
 * The per-entry action decides what happens with the objects at a matched
 * coordinate:
 * <ul>
 *   <li>hide ("禁止渲染"): the objects whose id is attached are hidden; an
 *       entry without attached ids hides everything at its coordinate.</li>
 *   <li>keep ("白名单"): only the objects whose id is attached are kept
 *       visible, everything else is hidden.</li>
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
    /** HUD element id ("hotbar", "bossbar", ...): matched while the player stands in the region. */
    public static final int CAT_HUD = 5;

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
                    for (String line : ConditionEngine.coordFilterLines()) {
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
     * typo in the config never breaks the game. A leading "+" marks the entry
     * as a keep (whitelist) entry.
     */
    private static Entry parse(String line) {
        if (line == null) {
            return null;
        }

        boolean whitelist = line.startsWith("+");
        if (whitelist) {
            line = line.substring(1);
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
        return new Entry(box[0], box[1], box[2], box[3], box[4], box[5], ids, whitelist);
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
        // HUD 元素 id（"hotbar" 等，见 FilterEngine.HUD_ELEMENT_IDS）。它们不是
        // 渲染注册表条目，单独标记；大小写不敏感比较（HUD id 本身全小写）。
        boolean hud = FilterEngine.HUD_ELEMENT_IDS.contains(trimmed.toLowerCase(Locale.ROOT));

        // The registry lookup is done on the canonical form so "stone" and
        // "minecraft:stone" are treated as the same block id. An id that
        // resolves to no registry stays the raw text: a lowercase name like
        // "bot" parses as "minecraft:bot" but must still be matched against
        // name tag / player name text ("bot") by the CAT_TEXT category.
        boolean registry = block || entity || particle || fluid;
        return new Id(registry ? id.toString() : trimmed, trimmed, block, entity, particle, fluid, hud);
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
     * Returns true when the given position is inside a hide entry that carries
     * no ids (a whole-region hide). Used only for objects whose id cannot be
     * resolved (some particles reach the renderer without a ParticleType): an
     * id-less hide hides the whole region regardless of any ids, while every
     * other entry always needs an id to match against.
     */
    public static boolean isRegionHiddenAt(double x, double y, double z) {
        if (!active()) {
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
            if (!e.whitelist && e.ids.isEmpty()
                    && bx >= e.x1 && bx <= e.x2 && by >= e.y1 && by <= e.y2 && bz >= e.z1 && bz <= e.z2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true when the given HUD element should be hidden by a coordinate
     * entry while the player stands inside its region. HUD elements have no
     * world position of their own, so the region is matched against the
     * player's position: a hide (blacklist) entry with attached HUD ids hides
     * them in the region, a keep (whitelist) entry only leaves the attached
     * ids visible there. Independent of the per-category master toggles.
     */
    public static boolean isHudElementHidden(String hudId) {
        if (!active() || hudId == null) {
            return false;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return false;
        }
        return hiddenAt(CAT_HUD, hudId, player.getX(), player.getY(), player.getZ());
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
     * hidden. See the class comment for the per-entry action semantics.
     */
    private static boolean hidden(Entry e, int cat, String id) {
        if (e.whitelist) {
            // keep: only the attached ids stay visible.
            return !inList(e, cat, id);
        }
        // hide: the attached ids are hidden; an entry without ids hides
        // everything at its coordinate.
        return e.ids.isEmpty() || inList(e, cat, id);
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
                case CAT_HUD -> {
                    // HUD 元素 id：用原始输入文本匹配（"fire" 同时也是方块 id，
                    // 规范化为 "minecraft:fire" 后无法再匹配 HUD id）。
                    if (a.hud && a.raw.equalsIgnoreCase(id)) {
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
        /** True for keep ("白名单") entries: only the ids stay visible. */
        final boolean whitelist;

        Entry(int x1, int y1, int z1, int x2, int y2, int z2, List<Id> ids, boolean whitelist) {
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
            this.ids = ids;
            this.whitelist = whitelist;
        }
    }

    /** One attached id with the render categories it resolves to. */
    private static class Id {
        final String text;
        /** The raw trimmed input text (used by the HUD element matching). */
        final String raw;
        final boolean block;
        final boolean entity;
        final boolean particle;
        final boolean fluid;
        final boolean hud;

        Id(String text, String raw, boolean block, boolean entity, boolean particle, boolean fluid, boolean hud) {
            this.text = text;
            this.raw = raw;
            this.block = block;
            this.entity = entity;
            this.particle = particle;
            this.fluid = fluid;
            this.hud = hud;
        }
    }
}