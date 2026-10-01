package com.wdylyh.config;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigOptionValues;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.config.value.BaseOptionListConfigValue;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import com.wdylyh.client.FaceModItemModels;
import com.wdylyh.client.gui.ConfigScreen;
import com.wdylyh.client.gui.ToastRenderer;
import com.wdylyh.api.ParticlePickAccessor;
import com.wdylyh.util.IdLocalizer;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ConfigCallbacks {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigCallbacks.class);

    public static void init(MinecraftClient mc) {
        // Face mod item shadow models must be registered before the first
        // model reload, so the plugin participates in every bake cycle.
        FaceModItemModels.register();
        FaceModPacks.ensureShadowItemResources();

        RenderConfig.Hotkeys.OPEN_CONFIG_GUI.getKeybind().setCallback(new Open_Gui());
        RenderConfig.Hotkeys.PICK_ENTITY_HOTKEY.getKeybind().setCallback(new Pick_Entity());
        // The reveal hotkey was registered with INGAME_BOTH, so this callback
        // fires on both press and release and rebuilds the chunk meshes either
        // way (see Reveal_Hotkey).
        RenderConfig.Hotkeys.REVEAL_HOTKEY.getKeybind().setCallback(new Reveal_Hotkey());

        // Block/fluid rendering data is cached per chunk. When these toggles change,
        // force the world renderer to rebuild so the change takes effect immediately.
        RenderConfig.Toggles.DISABLE_BLOCKS.setValueChangeCallback(ConfigCallbacks::on_Render_Config);
        RenderConfig.Toggles.DISABLE_FLUIDS.setValueChangeCallback(ConfigCallbacks::on_Render_Config);

        // The reveal hotkey mode affects the baked block/fluid meshes too (in
        // force-hide mode the hotkey hides every block while held), so toggling
        // it also triggers a rebuild.
        RenderConfig.General.REVEAL_HOTKEY_MODE.setValueChangeCallback(ConfigCallbacks::on_Render_Config);

        // The hotkey affected types list decides which categories the reveal
        // hotkey can touch. Blocks/fluids are baked into chunk meshes, so a
        // change must trigger a rebuild; the cache invalidation covers the rest.
        RenderConfig.General.REVEAL_AFFECTED_TYPES.setValueChangeCallback(ConfigCallbacks::on_Filter_Config);

        // Per-type filters. Blocks/fluids are baked into chunk meshes, so their
        // changes must trigger a rebuild; the cache invalidation covers the rest.
        RenderConfig.Filters.ENTITY_MODE.setValueChangeCallback(ConfigCallbacks::on_Filter_Config);
        RenderConfig.Filters.BLOCK_MODE.setValueChangeCallback(ConfigCallbacks::on_Filter_Config);
        RenderConfig.Filters.FLUID_MODE.setValueChangeCallback(ConfigCallbacks::on_Filter_Config);
        RenderConfig.Filters.BLOCK_ENTITY_MODE.setValueChangeCallback(ConfigCallbacks::on_Filter_Config);
        RenderConfig.Filters.PARTICLE_MODE.setValueChangeCallback(ConfigCallbacks::on_Filter_Config);
        RenderConfig.Filters.FILTERED_ENTITIES.setValueChangeCallback(ConfigCallbacks::on_Filter_Config);
        RenderConfig.Filters.FILTERED_BLOCKS.setValueChangeCallback(ConfigCallbacks::on_Filter_Config);
        RenderConfig.Filters.FILTERED_FLUIDS.setValueChangeCallback(ConfigCallbacks::on_Filter_Config);
        RenderConfig.Filters.FILTERED_BLOCK_ENTITIES.setValueChangeCallback(ConfigCallbacks::on_Filter_Config);
        RenderConfig.Filters.FILTERED_PARTICLES.setValueChangeCallback(ConfigCallbacks::on_Filter_Config);
        RenderConfig.Filters.HIDDEN_HUD_ELEMENTS.setValueChangeCallback(ConfigCallbacks::on_Frame_Filter_Config);

        // Armor, fog, name tags and players are evaluated per frame and are not
        // baked into chunk meshes, so their changes only need the id caches
        // invalidated.
        RenderConfig.Filters.ARMOR_MODE.setValueChangeCallback(ConfigCallbacks::on_Frame_Filter_Config);
        RenderConfig.Filters.FOG_MODE.setValueChangeCallback(ConfigCallbacks::on_Frame_Filter_Config);
        RenderConfig.Filters.NAME_TAG_MODE.setValueChangeCallback(ConfigCallbacks::on_Frame_Filter_Config);
        RenderConfig.Filters.PLAYER_MODE.setValueChangeCallback(ConfigCallbacks::on_Frame_Filter_Config);
        RenderConfig.Filters.FILTERED_ARMOR.setValueChangeCallback(ConfigCallbacks::on_Frame_Filter_Config);
        RenderConfig.Filters.FILTERED_FOGS.setValueChangeCallback(ConfigCallbacks::on_Frame_Filter_Config);
        RenderConfig.Filters.FILTERED_NAME_TAGS.setValueChangeCallback(ConfigCallbacks::on_Frame_Filter_Config);
        RenderConfig.Filters.FILTERED_PLAYERS.setValueChangeCallback(ConfigCallbacks::on_Frame_Filter_Config);

        // Player hiding and name tags are evaluated per frame in the entity
        // renderers, so toggling them needs no chunk rebuild, just a log line.
        RenderConfig.Toggles.HIDE_SELF.setValueChangeCallback(ConfigCallbacks::on_Log_Config);
        RenderConfig.Toggles.HIDE_OTHER_PLAYERS.setValueChangeCallback(ConfigCallbacks::on_Log_Config);
        RenderConfig.Toggles.DISABLE_NAME_TAGS.setValueChangeCallback(ConfigCallbacks::on_Log_Config);
        RenderConfig.Toggles.DISABLE_HELD_ITEMS.setValueChangeCallback(ConfigCallbacks::on_Log_Config);
        RenderConfig.Toggles.DISABLE_ELYTRA.setValueChangeCallback(ConfigCallbacks::on_Log_Config);

        // The face-mod master switch enables/disables the generated
        // ReignRender_FaceMod resource pack (and reloads the resources), so
        // every edited texture applies or falls back to the original look.
        RenderConfig.General.ENABLE_FACE_MOD.setValueChangeCallback(
                config -> FaceModPacks.applyEnabled(RenderConfig.General.ENABLE_FACE_MOD.getBooleanValue()));

        // Face-mod id lists: dropping an id (including through the config
        // reset button) must also delete its edited textures from the pack,
        // otherwise the object silently keeps the modification.
        for (com.wdylyh.client.gui.FaceModListScreen.FaceKind kind
                : com.wdylyh.client.gui.FaceModListScreen.FaceKind.values()) {
            if (kind.getCfg() != null) {
                kind.getCfg().setValueChangeCallback(ConfigCallbacks::on_Face_List_Config);
            }
        }

        // Universal replacement system: the master switch and every per-category
        // replacement list feed the ReplacementEngine caches. Block, fluid and
        // falling-block replacements are baked into chunk meshes, so changing
        // them must trigger a rebuild; the per-frame categories only need the
        // caches invalidated.
        RenderConfig.General.REPLACE_ENABLED.setValueChangeCallback(ConfigCallbacks::on_Replace_Config);
        RenderConfig.Filters.REPLACE_PARTICLES.setValueChangeCallback(ConfigCallbacks::on_Frame_Replace_Config);
        RenderConfig.Filters.REPLACE_BLOCKS.setValueChangeCallback(ConfigCallbacks::on_Replace_Config);
        RenderConfig.Filters.REPLACE_ENTITIES.setValueChangeCallback(ConfigCallbacks::on_Frame_Replace_Config);
        RenderConfig.Filters.REPLACE_FOGS.setValueChangeCallback(ConfigCallbacks::on_Frame_Replace_Config);
        RenderConfig.Filters.REPLACE_ARMOR.setValueChangeCallback(ConfigCallbacks::on_Frame_Replace_Config);
        RenderConfig.Filters.REPLACE_NAME_TAGS.setValueChangeCallback(ConfigCallbacks::on_Frame_Replace_Config);
        RenderConfig.Filters.REPLACE_PLAYER_NAMES.setValueChangeCallback(ConfigCallbacks::on_Frame_Replace_Config);
        RenderConfig.Filters.REPLACE_FLUIDS.setValueChangeCallback(ConfigCallbacks::on_Replace_Config);
        RenderConfig.Filters.REPLACE_BLOCK_ENTITIES.setValueChangeCallback(ConfigCallbacks::on_Frame_Replace_Config);
        RenderConfig.Filters.REPLACE_FALLING_BLOCKS.setValueChangeCallback(ConfigCallbacks::on_Replace_Config);
        RenderConfig.Filters.REPLACE_ITEM_ENTITIES.setValueChangeCallback(ConfigCallbacks::on_Frame_Replace_Config);
        RenderConfig.Filters.REPLACE_HELD_ITEMS.setValueChangeCallback(ConfigCallbacks::on_Frame_Replace_Config);
        RenderConfig.Filters.REPLACE_HUD_ELEMENTS.setValueChangeCallback(ConfigCallbacks::on_Frame_Replace_Config);

        // Coordinate filter: the master toggle is baked into the block/fluid
        // meshes. The hide/keep entries come from the condition system
        // (invalidated by on_Condition_Config below). Invalidating the caches
        // and rebuilding the meshes covers all of them.
        RenderConfig.Hotkeys.TOGGLE_COORD_FILTER.setValueChangeCallback(ConfigCallbacks::on_Coord_Config);

        // Coordinate aware replacements are baked into the block/fluid meshes
        // the same way the per-category block/fluid replacement lists are, so
        // a change of the master toggle must rebuild the meshes, not just
        // invalidate the caches (the replace entries come from the condition
        // system).
        RenderConfig.Hotkeys.TOGGLE_COORD_REPLACE.setValueChangeCallback(ConfigCallbacks::on_Replace_Config);

        // Region face-mod: the toggle drives the generated
        // ReignRender_RegionFace resource pack (enable/disable + reload, which
        // also re-bakes the models). The face entries come from the condition
        // system (see on_Condition_Config).
        RenderConfig.Hotkeys.TOGGLE_REGION_FACE.setValueChangeCallback(
                config -> RegionFacePacks.onRegionConfigChanged());

        // Condition system entries: the parsed views feed the coordinate
        // filter, the coordinate replacement and the region face-mod, so a
        // change invalidates every engine cache, re-applies the region face
        // pack (a newly listed face id needs its shadow resources generated)
        // and rebuilds the meshes for the baked block/fluid paths.
        RenderConfig.Conditions.CONDITION_ENTRIES.setValueChangeCallback(ConfigCallbacks::on_Condition_Config);

        // Bootstrap: config values loaded from the json never fire their
        // change callbacks, so apply the pack states once at startup. The
        // second argument disables the redundant reload — the game's own
        // initial resource load applies the (possibly changed) pack state,
        // and an already correct state does nothing at all.
        RegionFacePacks.applyEnabled(RenderConfig.Hotkeys.TOGGLE_REGION_FACE.getBooleanValue(), false);
        FaceModPacks.applyEnabled(RenderConfig.General.ENABLE_FACE_MOD.getBooleanValue(), false);

        // The coordinate pick mode only changes what the pick hotkey does; it
        // does not touch any render data.
        RenderConfig.General.COORD_PICK_MODE.setValueChangeCallback(ConfigCallbacks::on_Log_Config);

        RenderConfig.Hotkeys.PICK_COORD_HOTKEY.getKeybind().setCallback(new Pick_Coord());
    }

    private static void on_Frame_Replace_Config(IConfigBase config) {
        ReplacementEngine.invalidateCaches();
        LOGGER.info("[ReignRender] frame replace config '{}' changed to: {}", config.getName(), describe_Config(config));
    }

    private static void on_Replace_Config(IConfigBase config) {
        ReplacementEngine.invalidateCaches();
        LOGGER.info("[ReignRender] replace config '{}' changed to: {}", config.getName(), describe_Config(config));
        on_Render_Config(config);
    }

    private static void on_Log_Config(IConfigBase config) {
        LOGGER.info("[ReignRender] config '{}' changed to: {}", config.getName(), describe_Config(config));
    }

    private static void on_Frame_Filter_Config(IConfigBase config) {
        FilterEngine.invalidateCaches();
        LOGGER.info("[ReignRender] frame filter config '{}' changed to: {}", config.getName(), describe_Config(config));
    }

    private static void on_Filter_Config(IConfigBase config) {
        FilterEngine.invalidateCaches();
        LOGGER.info("[ReignRender] filter config '{}' changed to: {}", config.getName(), describe_Config(config));
        on_Render_Config(config);
    }

    /**
     * A face-mod id list changed: prune the edits of every id that is no
     * longer listed (the reset button on the config row goes through here too)
     * so the affected objects revert to their original textures.
     */
    private static void on_Face_List_Config(IConfigBase config) {
        FaceModPacks.onFaceListChanged();
    }

    private static void on_Coord_Config(IConfigBase config) {
        CoordinateFilter.invalidateCaches();
        LOGGER.info("[ReignRender] coord config '{}' changed to: {}", config.getName(), describe_Config(config));
        // The coordinate filter is baked into the block/fluid meshes, so any
        // of its config changes must trigger a rebuild (the invalidation above
        // covers the per-frame entity/particle/name-tag paths). The rebuild
        // also re-reads the baked paths, so both are always kept in sync.
        on_Render_Config(config);
    }

    /**
     * A condition entry changed: the parsed views feed the coordinate filter,
     * the coordinate replacement and the region face-mod, so all engine
     * caches are invalidated, the region face pack re-applies (a newly listed
     * face id needs its shadow resources generated) and the meshes rebuild
     * for the baked block/fluid paths. The dist/count actions are evaluated
     * live, the invalidation covers them as well.
     */
    private static void on_Condition_Config(IConfigBase config) {
        ConditionEngine.invalidateCaches();
        CoordinateFilter.invalidateCaches();
        ReplacementEngine.invalidateCaches();
        RegionFaceEngine.invalidateCaches();
        RegionFacePacks.onRegionConfigChanged();
        LOGGER.info("[ReignRender] condition config '{}' changed to: {}", config.getName(), describe_Config(config));
        on_Render_Config(config);
    }

    private static void on_Render_Config(IConfigBase config) {
        LOGGER.info("[ReignRender] render config '{}' changed to: {}", config.getName(), describe_Config(config));
        rebuildMeshes();
    }

    public static void rebuildMeshes() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.worldRenderer == null || mc.world == null || mc.player == null) {
            return;
        }

        // Reschedule all chunks within the render distance for a mesh rebuild.
        // Unlike reload() (which clears the built chunk storage and causes a
        // visible flicker), scheduleChunkRenders(...) only re-bakes the loaded
        // chunk meshes, so the block/fluid filters take effect without flashing.
        int distance = mc.options.getViewDistance().getValue();
        ChunkPos center = mc.player.getChunkPos();
        int minX = center.x - distance;
        int maxX = center.x + distance;
        int minZ = center.z - distance;
        int maxZ = center.z + distance;
        var dimension = mc.world.getDimension();
        int minY = ChunkSectionPos.getSectionCoord(dimension.minY());
        int maxY = ChunkSectionPos.getSectionCoord(dimension.minY() + dimension.height() - 1);

        mc.worldRenderer.scheduleChunkRenders(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Renders the current value of a config option into a readable string,
     * so config changes can be logged for debugging. String lists show all
     * entries, mode options show the active mode name.
     */
    private static String describe_Config(IConfigBase config) {
        if (config instanceof ConfigStringList) {
            return String.join(", ", ((ConfigStringList) config).getStrings());
        }

        if (config instanceof ConfigOptionValues) {
            Object value = ((ConfigOptionValues<?>) config).getOptionValue();

            if (value instanceof BaseOptionListConfigValue) {
                return ((BaseOptionListConfigValue) value).getName();
            }

            return String.valueOf(value);
        }

        return String.valueOf(config.getAsJsonElement());
    }

    /**
     * Rebuilds the chunk meshes every time the reveal hotkey is pressed or
     * released (the keybind is registered with INGAME_BOTH, so both actions
     * reach this callback). The block/fluid filters are baked into the meshes,
     * so a reveal requires rebuilding them without the filter, and releasing
     * the key requires rebuilding them with the filter again.
     * <p>
     * The rebuild is unconditional: whether the hotkey currently runs in
     * reveal mode or force-hide mode must always be re-applied to the baked
     * meshes. Skipping it based on the mode or the affected-types state leaves
     * stale meshes behind after a mode switch (the first press after the
     * switch would otherwise do nothing until the key is released and pressed
     * again).
     */
    private static class Reveal_Hotkey implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            rebuildMeshes();
            return true;
        }
    }

    private static class Open_Gui implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            GuiBase.openGui(new ConfigScreen());
            return true;
        }
    }

    /**
     * Adds or removes the object currently being looked at to/from the matching
     * filter list. Only fires on the press of the bound key (not the release).
     * <p>
     * Sneaking while pressing picks the biome the player is currently standing
     * in into the fog filter list instead; any biome is accepted.
     * <p>
     * Priority without sneaking: entity (unchanged), then a particle close to
     * the crosshair (so ephemeral particles can be targeted even when a block
     * sits behind them), then the fluid or block at the hit position.
     */
    private static class Pick_Entity implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            if (action != KeyAction.PRESS) {
                return false;
            }

            MinecraftClient mc = MinecraftClient.getInstance();

            // Sneak key state is also checked directly: the key event that
            // fires this callback can arrive in the same frame as the sneak
            // key press, before the player tick updates the entity's sneak
            // flag, and it also covers toggle-sneak mode.
            if (mc.player != null && (mc.player.isSneaking() || mc.options.sneakKey.isPressed())) {
                pickBiome(mc);
                return true;
            }

            if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
                toggle(mc, RenderConfig.Filters.FILTERED_ENTITIES,
                        Registries.ENTITY_TYPE.getId(entityHit.getEntity().getType()).toString(),
                        "reignrender.message.pickEntityAdded", "reignrender.message.pickEntityRemoved",
                        entity_Spawn_Egg(entityHit.getEntity().getType()));
                return true;
            }

            double blockHitDistance = Double.MAX_VALUE;
            BlockPos blockPos = null;

            if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
                blockPos = blockHit.getBlockPos();
                blockHitDistance = mc.gameRenderer.getCamera().getCameraPos().distanceTo(blockHit.getPos());
            }

            // Particles are ephemeral and have no hit result of their own, so
            // they are picked by ray-casting the particle manager; they win
            // when one sits closer to the crosshair than the hit block.
            ParticlePickAccessor.PickRes particle = pick_Particle(mc);

            if (particle != null && particle.distance() < blockHitDistance) {
                toggle(mc, RenderConfig.Filters.FILTERED_PARTICLES,
                        Registries.PARTICLE_TYPE.getId(particle.type()).toString(),
                        "reignrender.message.pickParticleAdded", "reignrender.message.pickParticleRemoved", null);
                return true;
            }

            if (blockPos != null) {
                // Fluids have no hit result of their own; a non-empty fluid
                // state at the hit position means the crosshair is on a fluid.
                FluidState fluid = mc.world.getFluidState(blockPos);

                if (!fluid.isEmpty()) {
                    toggle(mc, RenderConfig.Filters.FILTERED_FLUIDS,
                            Registries.FLUID.getId(fluid.getFluid()).toString(),
                            "reignrender.message.pickFluidAdded", "reignrender.message.pickFluidRemoved",
                            new ItemStack(fluid.getFluid().getBucketItem()));
                    return true;
                }

                Block block = mc.world.getBlockState(blockPos).getBlock();

                // Blocks backed by a block entity are rendered via the block
                // entity filter, so picking them into the block list would be
                // a no-op (and they are cleaned out again on the next load).
                if (FilterEngine.isBlockManagedByBlockEntityFilter(block)) {
                    ToastRenderer.show(Text.translatable("reignrender.message.pickBlockManagedByBlockEntity",
                            Registries.BLOCK.getId(block).toString()), ToastRenderer.TOAST_COLOR_ERROR,
                            new ItemStack(block.asItem()));
                    return true;
                }

                toggle(mc, RenderConfig.Filters.FILTERED_BLOCKS,
                        Registries.BLOCK.getId(block).toString(),
                        "reignrender.message.pickBlockAdded", "reignrender.message.pickBlockRemoved",
                        new ItemStack(block.asItem()));
                return true;
            }

            ToastRenderer.show(Text.translatable("reignrender.message.pickNone"), ToastRenderer.TOAST_COLOR_ERROR);
            return true;
        }
    }

    /**
     * Picks the coordinates of the block currently being looked at into the
     * condition entry list (as a "region=..." entry). Only fires on the press
     * of the bound key (not the release).
     * <p>
     * With {@link RenderConfig.General#COORD_PICK_MODE} set to "single" the looked-at block
     * position is stored directly as "x,y,z". With "box" the first press
     * remembers corner 1 and the second press stores the region
     * "x1,y1,z1~x2,y2,z2" between the two corners. Pressing the hotkey again
     * on an identical coordinate toggles the entry out of the list again.
     */
    private static class Pick_Coord implements IHotkeyCallback
    {
        private static BlockPos corner = null;

        /**
         * Dimension the pending corner was picked in. Compared by identity on
         * the second press, so a corner left over from before a world or
         * dimension change is treated as stale and a fresh corner is picked
         * instead of building a box between two unrelated points (the
         * identity comparison also discards the corner when leaving the world
         * and rejoining the same dimension, which is the safe behavior since
         * the coordinates may belong to a different save).
         */
        private static RegistryKey<World> cornerDim = null;

        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key)
        {
            if (action != KeyAction.PRESS)
            {
                return false;
            }

            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc.world == null || mc.player == null || !(mc.crosshairTarget instanceof BlockHitResult hit))
            {
                ToastRenderer.show(Text.translatable("reignrender.message.pickCoordNone"), ToastRenderer.TOAST_COLOR_ERROR);
                return true;
            }

            BlockPos pos = hit.getBlockPos();

            if (RenderConfig.General.COORD_PICK_MODE.getOptionValue() == RenderConfig.General.COORD_PICK_BOX)
            {
                if (corner == null || cornerDim != mc.world.getRegistryKey())
                {
                    corner = pos;
                    cornerDim = mc.world.getRegistryKey();
                    ToastRenderer.show(Text.translatable("reignrender.message.pickCoordCorner1",
                            corner.getX(), corner.getY(), corner.getZ()), ToastRenderer.TOAST_COLOR_ERROR);
                    return true;
                }

                BlockPos c1 = corner;
                corner = null;
                cornerDim = null;
                addCoord(c1.getX() + "," + c1.getY() + "," + c1.getZ() +
                        "~" + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                return true;
            }

            addCoord(pos.getX() + "," + pos.getY() + "," + pos.getZ());
            return true;
        }
    }

    /**
     * Toggles the given coordinate region in the condition entry list (as a
     * "region=..." entry with no actions, ready to be extended with acts in
     * the GUI) and reports the result to the player.
     */
    private static void addCoord(String line) {
        String entry = "region=" + line;
        List<String> cur = new ArrayList<>(RenderConfig.Conditions.CONDITION_ENTRIES.getStrings());
        boolean added = !cur.remove(entry);

        if (added)
        {
            cur.add(entry);
        }

        RenderConfig.Conditions.CONDITION_ENTRIES.setStrings(cur);
        ConditionEngine.invalidateCaches();
        ToastRenderer.show(Text.translatable(added ? "reignrender.message.pickCoordAdded"
                                          : "reignrender.message.pickCoordRemoved", line),
                added ? ToastRenderer.TOAST_COLOR_ADDED : ToastRenderer.TOAST_COLOR_REMOVED);
    }

    /**
     * Toggles the biome the player is currently standing in in the fog filter
     * list: adds it if missing, removes it if already present.
     */
    private static void pickBiome(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        String biomeId = mc.world.getBiome(BlockPos.ofFloored(cameraPos))
                .getKey().map(key -> key.getValue().toString()).orElse(null);

        if (biomeId == null) {
            ToastRenderer.show(Text.translatable("reignrender.message.pickBiomeNone"), ToastRenderer.TOAST_COLOR_ERROR);
            return;
        }

        toggle(mc, RenderConfig.Filters.FILTERED_FOGS, biomeId,
                "reignrender.message.pickBiomeAdded", "reignrender.message.pickBiomeRemoved",
                biome_Icon(biomeId));
    }

    /**
     * Returns the spawn egg of the given entity type as the toast icon, or
     * null when the type has no spawn egg (then no icon is drawn).
     */
    private static ItemStack entity_Spawn_Egg(EntityType<?> type) {
        Item egg = SpawnEggItem.forEntity(type);
        return egg != null && egg != Items.AIR ? new ItemStack(egg) : null;
    }

    /**
     * Returns an item that best represents the given biome for the toast icon.
     * The mapping favors the biome's signature block, plant or creature; biomes
     * without a fitting icon (e.g. the void) map to null so no icon is drawn.
     */
    private static ItemStack biome_Icon(String biomeId) {
        Item item = switch (biomeId) {
            case "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands" -> Items.RED_SAND;
            case "minecraft:bamboo_jungle" -> Items.BAMBOO;
            case "minecraft:basalt_deltas" -> Items.BASALT;
            case "minecraft:beach", "minecraft:desert" -> Items.SAND;
            case "minecraft:birch_forest", "minecraft:old_growth_birch_forest" -> Items.BIRCH_LOG;
            case "minecraft:cherry_grove" -> Items.CHERRY_LOG;
            case "minecraft:cold_ocean", "minecraft:deep_cold_ocean" -> Items.COD;
            case "minecraft:crimson_forest" -> Items.CRIMSON_FUNGUS;
            case "minecraft:dark_forest" -> Items.DARK_OAK_LOG;
            case "minecraft:deep_dark" -> Items.SCULK;
            case "minecraft:deep_frozen_ocean", "minecraft:frozen_ocean", "minecraft:frozen_river" -> Items.ICE;
            case "minecraft:deep_lukewarm_ocean", "minecraft:deep_ocean", "minecraft:lukewarm_ocean",
                 "minecraft:ocean", "minecraft:river" -> Items.WATER_BUCKET;
            case "minecraft:dripstone_caves" -> Items.DRIPSTONE_BLOCK;
            case "minecraft:end_barrens", "minecraft:end_midlands", "minecraft:small_end_islands",
                 "minecraft:the_end" -> Items.END_STONE;
            case "minecraft:end_highlands" -> Items.CHORUS_PLANT;
            case "minecraft:flower_forest", "minecraft:meadow" -> Items.POPPY;
            case "minecraft:forest", "minecraft:windswept_forest" -> Items.OAK_LOG;
            case "minecraft:frozen_peaks", "minecraft:snowy_beach", "minecraft:snowy_plains",
                 "minecraft:snowy_slopes" -> Items.SNOW_BLOCK;
            case "minecraft:grove", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
                 "minecraft:snowy_taiga", "minecraft:taiga" -> Items.SPRUCE_LOG;
            case "minecraft:ice_spikes" -> Items.PACKED_ICE;
            case "minecraft:jagged_peaks", "minecraft:stony_peaks", "minecraft:stony_shore",
                 "minecraft:windswept_hills" -> Items.STONE;
            case "minecraft:jungle", "minecraft:sparse_jungle" -> Items.JUNGLE_LOG;
            case "minecraft:lush_caves" -> Items.MOSS_BLOCK;
            case "minecraft:mangrove_swamp" -> Items.MANGROVE_LOG;
            case "minecraft:mushroom_fields" -> Items.RED_MUSHROOM;
            case "minecraft:nether_wastes" -> Items.NETHERRACK;
            case "minecraft:pale_garden" -> Items.PALE_OAK_LOG;
            case "minecraft:plains" -> Items.GRASS_BLOCK;
            case "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna" -> Items.ACACIA_LOG;
            case "minecraft:soul_sand_valley" -> Items.SOUL_SAND;
            case "minecraft:sunflower_plains" -> Items.SUNFLOWER;
            case "minecraft:swamp" -> Items.LILY_PAD;
            case "minecraft:warm_ocean" -> Items.TROPICAL_FISH;
            case "minecraft:warped_forest" -> Items.WARPED_FUNGUS;
            case "minecraft:windswept_gravelly_hills" -> Items.GRAVEL;
            default -> null;
        };

        return item != null ? new ItemStack(item) : null;
    }

    /**
     * Ray-casts the particle manager along the camera view direction.
     */
    private static ParticlePickAccessor.PickRes pick_Particle(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            return null;
        }

        Camera camera = mc.gameRenderer.getCamera();
        return ((ParticlePickAccessor) mc.particleManager)
                .pick(camera.getCameraPos(), mc.player.getRotationVec(1.0F), 0.6, 256.0);
    }

    /**
     * Toggles the given registry id in the given string-list config and reports
     * the result to the player. A non-null, non-empty icon is shown as the
     * achievement-toast item (e.g. the block being filtered); otherwise the
     * accent-colored default icon is used.
     */
    private static void toggle(MinecraftClient mc, ConfigStringList config, String id,
                               String addedKey, String removedKey, ItemStack icon) {
        List<String> list = new ArrayList<>(config.getStrings());
        // remove() returns true if the id was present, replacing the separate
        // contains() + remove() passes with a single O(n) lookup
        boolean added = !list.remove(id);

        if (added) {
            list.add(id);
        }

        config.setStrings(list);
        FilterEngine.invalidateCaches();
        ToastRenderer.show(Text.translatable(added ? addedKey : removedKey, IdLocalizer.name(id)),
                added ? ToastRenderer.TOAST_COLOR_ADDED : ToastRenderer.TOAST_COLOR_REMOVED, icon);
    }
}