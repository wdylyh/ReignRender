package com.wdylyh.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import fi.dy.masa.malilib.config.value.BaseOptionListConfigValue;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized blacklist/whitelist matching for the per-type render filters.
 *
 * The current filter mode (OFF / BLACKLIST / WHITELIST) and the five registry
 * id lists are read from {@link Configs.Filter}. All methods return true when
 * the given type should be hidden from rendering.
 */
public class FilterRules {

    private static final Set<String> entities = new HashSet<>();
    private static final Set<String> blocks = new HashSet<>();
    private static final Set<String> fluids = new HashSet<>();
    private static final Set<String> blockEntities = new HashSet<>();
    private static final Set<String> particles = new HashSet<>();
    private static final Set<String> armor = new HashSet<>();
    private static final Set<String> fogs = new HashSet<>();

    // Pre-computed lowercase enum names so isFogFiltered does not allocate a
    // new String on every frame. CameraSubmersionType has only a handful of
    // constants, so this map is tiny.
    private static final EnumMap<CameraSubmersionType, String> FOG_TYPE_NAMES = new EnumMap<>(CameraSubmersionType.class);
    static {
        for (CameraSubmersionType t : CameraSubmersionType.values()) {
            FOG_TYPE_NAMES.put(t, t.name().toLowerCase(Locale.ROOT));
        }
    }

    private static boolean dirty = true;

    private static final Logger LOGGER = LoggerFactory.getLogger(FilterRules.class);

    // The filter methods below are called for every visible block/block entity
    // on every frame. To keep the log readable we record each (block id -> decision)
    // only once per config change instead of logging every single call.
    private static final Set<String> loggedBlockDecisions = new HashSet<>();
    private static final Set<String> loggedBlockEntityDecisions = new HashSet<>();

    /**
     * Called whenever the filter mode or any of the filter lists change,
     * so the cached id sets are rebuilt on the next query.
     */
    public static void invalidateCaches() {
        dirty = true;
    }

    private static void rebuildCacheIfDirty() {
        if (dirty) {
            entities.clear();
            blocks.clear();
            fluids.clear();
            blockEntities.clear();
            particles.clear();
            armor.clear();
            fogs.clear();

            entities.addAll(Configs.Filter.FILTERED_ENTITIES.getStrings());
            blocks.addAll(Configs.Filter.FILTERED_BLOCKS.getStrings());
            fluids.addAll(Configs.Filter.FILTERED_FLUIDS.getStrings());
            blockEntities.addAll(Configs.Filter.FILTERED_BLOCK_ENTITIES.getStrings());
            particles.addAll(Configs.Filter.FILTERED_PARTICLES.getStrings());
            armor.addAll(Configs.Filter.FILTERED_ARMOR.getStrings());
            fogs.addAll(Configs.Filter.FILTERED_FOGS.getStrings());

            // A config change means the old logged decisions are stale.
            loggedBlockDecisions.clear();
            loggedBlockEntityDecisions.clear();

            dirty = false;
        }
    }

    private static boolean isFiltered(BaseOptionListConfigValue mode, Set<String> list, String id) {
        if (mode == Configs.Filter.MODE_OFF) {
            return false;
        }

        // BLACKLIST hides entries present in the list,
        // WHITELIST hides entries absent from the list.
        boolean inList = list.contains(id);
        return (mode == Configs.Filter.MODE_WHITELIST) != inList;
    }

    private static String idToString(Identifier id) {
        return id != null ? id.toString() : null;
    }

    public static boolean isEntityFiltered(EntityType<?> type) {
        if (type == null) {
            return false;
        }
        rebuildCacheIfDirty();
        BaseOptionListConfigValue mode = Configs.Filter.ENTITY_MODE.getOptionValue();
        // With the master "disable entities" toggle on, an OFF mode means
        // every entity is hidden; otherwise the blacklist/whitelist applies.
        if (mode == Configs.Filter.MODE_OFF) {
            return true;
        }
        String id = idToString(Registries.ENTITY_TYPE.getId(type));
        return id != null && isFiltered(mode, entities, id);
    }

    public static boolean isBlockFiltered(BlockState state) {
        if (state == null) {
            return false;
        }
        rebuildCacheIfDirty();
        BaseOptionListConfigValue mode = Configs.Filter.BLOCK_MODE.getOptionValue();
        // With the master "disable blocks" toggle on, an OFF mode means
        // every block is hidden; otherwise the blacklist/whitelist applies.
        if (mode == Configs.Filter.MODE_OFF) {
            return true;
        }
        String id = idToString(Registries.BLOCK.getId(state.getBlock()));
        boolean filtered = id != null && isFiltered(mode, blocks, id);

        if (id != null && loggedBlockDecisions.add(id)) {
            LOGGER.info("[ReignRender] BLOCK filter: id={} mode={} inList={} -> filtered={}",
                    id, mode.getName(), blocks.contains(id), filtered);
        }

        return filtered;
    }

    public static boolean isFluidFiltered(FluidState state) {
        if (state == null) {
            return false;
        }
        rebuildCacheIfDirty();
        BaseOptionListConfigValue mode = Configs.Filter.FLUID_MODE.getOptionValue();
        // With the master "disable fluids" toggle on, an OFF mode means
        // every fluid is hidden; otherwise the blacklist/whitelist applies.
        if (mode == Configs.Filter.MODE_OFF) {
            return true;
        }
        String id = idToString(Registries.FLUID.getId(state.getFluid()));
        return id != null && isFiltered(mode, fluids, id);
    }

    /**
     * Returns true when the given block entity should be hidden from rendering.
     * The list stores concrete variant block ids (e.g. "minecraft:white_shulker_box"),
     * so each variant (shulker color, sign wood, ...) can be filtered independently.
     */
    public static boolean isBlockEntityFiltered(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        rebuildCacheIfDirty();
        BaseOptionListConfigValue mode = Configs.Filter.BLOCK_ENTITY_MODE.getOptionValue();
        // With the master "disable block entities" toggle on, an OFF mode means
        // every block entity is hidden; otherwise the blacklist/whitelist applies.
        if (mode == Configs.Filter.MODE_OFF) {
            return true;
        }
        String id = idToString(Registries.BLOCK.getId(blockEntity.getCachedState().getBlock()));
        boolean filtered = id != null && isFiltered(mode, blockEntities, id);

        if (id != null && loggedBlockEntityDecisions.add(id)) {
            LOGGER.info("[ReignRender] BLOCK_ENTITY filter: id={} mode={} inList={} -> filtered={}",
                    id, mode.getName(), blockEntities.contains(id), filtered);
        }

        return filtered;
    }

    public static boolean isParticleFiltered(ParticleType<?> type) {
        if (type == null) {
            return false;
        }
        rebuildCacheIfDirty();
        BaseOptionListConfigValue mode = Configs.Filter.PARTICLE_MODE.getOptionValue();
        // With the master "disable particles" toggle on, an OFF mode means
        // every particle is hidden; otherwise the blacklist/whitelist applies.
        if (mode == Configs.Filter.MODE_OFF) {
            return true;
        }
        String id = idToString(Registries.PARTICLE_TYPE.getId(type));
        return id != null && isFiltered(mode, particles, id);
    }

    /**
     * Returns true when the given armor piece should be hidden. The list stores
     * item registry ids (e.g. "minecraft:diamond_helmet").
     */
    public static boolean isArmorFiltered(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        rebuildCacheIfDirty();
        BaseOptionListConfigValue mode = Configs.Filter.ARMOR_MODE.getOptionValue();
        // With the master "disable armor" toggle on, an OFF mode means
        // every armor piece is hidden; otherwise the blacklist/whitelist applies.
        if (mode == Configs.Filter.MODE_OFF) {
            return true;
        }
        String id = idToString(Registries.ITEM.getId(stack.getItem()));
        return id != null && isFiltered(mode, armor, id);
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
        if (type == null) {
            return false;
        }
        rebuildCacheIfDirty();
        BaseOptionListConfigValue mode = Configs.Filter.FOG_MODE.getOptionValue();
        // With the master "disable fog" toggle on, an OFF mode means
        // every fog type is hidden; otherwise the blacklist/whitelist applies.
        if (mode == Configs.Filter.MODE_OFF) {
            return true;
        }

        boolean anyInList = fogs.contains(FOG_TYPE_NAMES.get(type));

        if (!anyInList && world != null && pos != null) {
            RegistryEntry<Biome> biome = world.getBiome(BlockPos.ofFloored(pos));
            if (biome != null) {
                anyInList = biome.getKey()
                        .map(key -> fogs.contains(key.getValue().toString()))
                        .orElse(false);
            }
        }

        if (!anyInList && world != null) {
            anyInList = fogs.contains(world.getRegistryKey().getValue().toString());
        }

        if (mode == Configs.Filter.MODE_BLACKLIST) {
            return anyInList;
        }
        // Whitelist: hide the fog when none of its identities is listed.
        return !anyInList;
    }

    /**
     * Returns true when the given block is backed by a block entity type that is
     * managed by the block entity filter list (e.g. every shulker box color, sign
     * wood, banner color or copper chest oxidation stage).
     */
    public static boolean isBlockManagedByBlockEntityFilter(Block block) {
        if (block == null) {
            return false;
        }

        BlockState state = block.getDefaultState();

        for (String id : Configs.Filter.BLOCK_ENTITY_TYPE_IDS) {
            Identifier identifier = Identifier.tryParse(id);
            BlockEntityType<?> type = identifier != null ? Registries.BLOCK_ENTITY_TYPE.get(identifier) : null;

            if (type != null && type.supports(state)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Removes block ids that are backed by a managed block entity type from the
     * block filter list (copper chests, signs, banners, ...). Called after
     * loading the config to clean up leftovers from older versions.
     */
    public static void cleanUpBlockFilter() {
        List<String> current = Configs.Filter.FILTERED_BLOCKS.getStrings();
        List<String> blocks = new ArrayList<>();

        for (String rawId : current) {
            Identifier id = Identifier.tryParse(rawId);
            Block block = id != null ? Registries.BLOCK.get(id) : null;

            if (block == null || !isBlockManagedByBlockEntityFilter(block)) {
                blocks.add(rawId);
            }
        }

        if (blocks.size() != current.size()) {
            Configs.Filter.FILTERED_BLOCKS.setStrings(blocks);
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
        List<String> current = Configs.Filter.FILTERED_BLOCK_ENTITIES.getStrings();
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String rawId : current) {
            Identifier id = Identifier.tryParse(rawId);

            if (id == null || !Configs.Filter.BLOCK_ENTITY_TYPE_IDS.contains(id.toString())) {
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

        if (result.size() != current.size()) {
            Configs.Filter.FILTERED_BLOCK_ENTITIES.setStrings(result);
        }
    }

    private static void addUnique(List<String> list, Set<String> seen, String id) {
        if (seen.add(id)) {
            list.add(id);
        }
    }
}
