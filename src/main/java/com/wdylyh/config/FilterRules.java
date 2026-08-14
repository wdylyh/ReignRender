package com.wdylyh.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fi.dy.masa.malilib.config.value.BaseOptionListConfigValue;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
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

            entities.addAll(Configs.Filter.FILTERED_ENTITIES.getStrings());
            blocks.addAll(Configs.Filter.FILTERED_BLOCKS.getStrings());
            fluids.addAll(Configs.Filter.FILTERED_FLUIDS.getStrings());
            blockEntities.addAll(Configs.Filter.FILTERED_BLOCK_ENTITIES.getStrings());
            particles.addAll(Configs.Filter.FILTERED_PARTICLES.getStrings());

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
     * Returns true when the given block is backed by a block entity type that is
     * managed by the block entity filter list (e.g. every shulker box color, sign
     * wood, banner color or copper chest oxidation stage).
     */
    public static boolean isBlockManagedByBlockEntityFilter(Block block) {
        if (block == null) {
            return false;
        }

        for (Identifier id : Registries.BLOCK_ENTITY_TYPE.getIds()) {
            if (Configs.Filter.BLOCK_ENTITY_TYPE_IDS.contains(id.toString())) {
                BlockEntityType<?> type = Registries.BLOCK_ENTITY_TYPE.get(id);

                if (type != null && type.supports(block.getDefaultState())) {
                    return true;
                }
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
        List<String> blocks = new ArrayList<>();

        for (String rawId : Configs.Filter.FILTERED_BLOCKS.getStrings()) {
            Identifier id = Identifier.tryParse(rawId);
            Block block = id != null ? Registries.BLOCK.get(id) : null;

            if (block == null || !isBlockManagedByBlockEntityFilter(block)) {
                blocks.add(rawId);
            }
        }

        if (blocks.size() != Configs.Filter.FILTERED_BLOCKS.getStrings().size()) {
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
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String rawId : Configs.Filter.FILTERED_BLOCK_ENTITIES.getStrings()) {
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

        if (result.size() != Configs.Filter.FILTERED_BLOCK_ENTITIES.getStrings().size()) {
            Configs.Filter.FILTERED_BLOCK_ENTITIES.setStrings(result);
        }
    }

    private static void addUnique(List<String> list, Set<String> seen, String id) {
        if (seen.add(id)) {
            list.add(id);
        }
    }
}
