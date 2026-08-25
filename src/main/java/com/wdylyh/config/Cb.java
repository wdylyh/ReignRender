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
import com.wdylyh.client.gui.GConfigs;
import com.wdylyh.client.gui.Toast;
import com.wdylyh.api.PickAcc;
import com.wdylyh.util.Names;
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
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Cb {

    private static final Logger LOGGER = LoggerFactory.getLogger(Cb.class);

    public static void init(MinecraftClient mc) {
        Cfg.HK.OPEN_CONFIG_GUI.getKeybind().setCallback(new OGui());
        Cfg.HK.PICK_ENTITY_HOTKEY.getKeybind().setCallback(new PickE());
        // The reveal hotkey was registered with INGAME_BOTH, so this callback
        // fires on both press and release and rebuilds the chunk meshes either
        // way (see RHotkey).
        Cfg.HK.REVEAL_HOTKEY.getKeybind().setCallback(new RHotkey());

        // Block/fluid rendering data is cached per chunk. When these toggles change,
        // force the world renderer to rebuild so the change takes effect immediately.
        Cfg.Off.DISABLE_BLOCKS.setValueChangeCallback(Cb::onRCfg);
        Cfg.Off.DISABLE_FLUIDS.setValueChangeCallback(Cb::onRCfg);

        // The reveal hotkey mode affects the baked block/fluid meshes too (in
        // force-hide mode the hotkey hides every block while held), so toggling
        // it also triggers a rebuild.
        Cfg.G.REVEAL_HOTKEY_MODE.setValueChangeCallback(Cb::onRCfg);

        // The hotkey affected types list decides which categories the reveal
        // hotkey can touch. Blocks/fluids are baked into chunk meshes, so a
        // change must trigger a rebuild; the cache invalidation covers the rest.
        Cfg.G.REVEAL_AFFECTED_TYPES.setValueChangeCallback(Cb::onFCfg);

        // Per-type filters. Blocks/fluids are baked into chunk meshes, so their
        // changes must trigger a rebuild; the cache invalidation covers the rest.
        Cfg.F.ENTITY_MODE.setValueChangeCallback(Cb::onFCfg);
        Cfg.F.BLOCK_MODE.setValueChangeCallback(Cb::onFCfg);
        Cfg.F.FLUID_MODE.setValueChangeCallback(Cb::onFCfg);
        Cfg.F.BLOCK_ENTITY_MODE.setValueChangeCallback(Cb::onFCfg);
        Cfg.F.PARTICLE_MODE.setValueChangeCallback(Cb::onFCfg);
        Cfg.F.FILTERED_ENTITIES.setValueChangeCallback(Cb::onFCfg);
        Cfg.F.FILTERED_BLOCKS.setValueChangeCallback(Cb::onFCfg);
        Cfg.F.FILTERED_FLUIDS.setValueChangeCallback(Cb::onFCfg);
        Cfg.F.FILTERED_BLOCK_ENTITIES.setValueChangeCallback(Cb::onFCfg);
        Cfg.F.FILTERED_PARTICLES.setValueChangeCallback(Cb::onFCfg);
        Cfg.F.HIDDEN_HUD_ELEMENTS.setValueChangeCallback(Cb::onFFCfg);

        // Armor, fog, name tags and players are evaluated per frame and are not
        // baked into chunk meshes, so their changes only need the id caches
        // invalidated.
        Cfg.F.ARMOR_MODE.setValueChangeCallback(Cb::onFFCfg);
        Cfg.F.FOG_MODE.setValueChangeCallback(Cb::onFFCfg);
        Cfg.F.NAME_TAG_MODE.setValueChangeCallback(Cb::onFFCfg);
        Cfg.F.PLAYER_MODE.setValueChangeCallback(Cb::onFFCfg);
        Cfg.F.FILTERED_ARMOR.setValueChangeCallback(Cb::onFFCfg);
        Cfg.F.FILTERED_FOGS.setValueChangeCallback(Cb::onFFCfg);
        Cfg.F.FILTERED_NAME_TAGS.setValueChangeCallback(Cb::onFFCfg);
        Cfg.F.FILTERED_PLAYERS.setValueChangeCallback(Cb::onFFCfg);

        // Player hiding and name tags are evaluated per frame in the entity
        // renderers, so toggling them needs no chunk rebuild, just a log line.
        Cfg.Off.HIDE_SELF.setValueChangeCallback(Cb::onLogCfg);
        Cfg.Off.HIDE_OTHER_PLAYERS.setValueChangeCallback(Cb::onLogCfg);
        Cfg.Off.DISABLE_NAME_TAGS.setValueChangeCallback(Cb::onLogCfg);
        Cfg.Off.DISABLE_HELD_ITEMS.setValueChangeCallback(Cb::onLogCfg);
        Cfg.Off.DISABLE_ELYTRA.setValueChangeCallback(Cb::onLogCfg);

        // Distance and count limits are evaluated per frame and do not touch
        // chunk meshes, so a log line is all they need.
        Cfg.G.ENTITY_RENDER_DISTANCE.setValueChangeCallback(Cb::onLogCfg);
        Cfg.G.MAX_ENTITIES.setValueChangeCallback(Cb::onLogCfg);
        Cfg.G.MAX_PARTICLES.setValueChangeCallback(Cb::onLogCfg);

        // The per-section block cap is baked into chunk meshes, so changing it
        // must trigger a mesh rebuild just like the block filter.
        Cfg.G.MAX_BLOCKS_PER_CHUNK.setValueChangeCallback(Cb::onRCfg);

        // Universal replacement system: the master switch and every per-category
        // replacement list feed the Rpl caches. Block, fluid and
        // falling-block replacements are baked into chunk meshes, so changing
        // them must trigger a rebuild; the per-frame categories only need the
        // caches invalidated.
        Cfg.G.REPLACE_ENABLED.setValueChangeCallback(Cb::onRepCfg);
        Cfg.F.REPLACE_PARTICLES.setValueChangeCallback(Cb::onFRepCfg);
        Cfg.F.REPLACE_BLOCKS.setValueChangeCallback(Cb::onRepCfg);
        Cfg.F.REPLACE_ENTITIES.setValueChangeCallback(Cb::onFRepCfg);
        Cfg.F.REPLACE_FOGS.setValueChangeCallback(Cb::onFRepCfg);
        Cfg.F.REPLACE_ARMOR.setValueChangeCallback(Cb::onFRepCfg);
        Cfg.F.REPLACE_NAME_TAGS.setValueChangeCallback(Cb::onFRepCfg);
        Cfg.F.REPLACE_PLAYER_NAMES.setValueChangeCallback(Cb::onFRepCfg);
        Cfg.F.REPLACE_FLUIDS.setValueChangeCallback(Cb::onRepCfg);
        Cfg.F.REPLACE_BLOCK_ENTITIES.setValueChangeCallback(Cb::onFRepCfg);
        Cfg.F.REPLACE_FALLING_BLOCKS.setValueChangeCallback(Cb::onRepCfg);
        Cfg.F.REPLACE_ITEM_ENTITIES.setValueChangeCallback(Cb::onFRepCfg);
    }

    private static void onFRepCfg(IConfigBase config) {
        Rpl.invalidateCaches();
        LOGGER.info("[ReignRender] frame replace config '{}' changed to: {}", config.getName(), descCfg(config));
    }

    private static void onRepCfg(IConfigBase config) {
        Rpl.invalidateCaches();
        LOGGER.info("[ReignRender] replace config '{}' changed to: {}", config.getName(), descCfg(config));
        onRCfg(config);
    }

    private static void onLogCfg(IConfigBase config) {
        LOGGER.info("[ReignRender] config '{}' changed to: {}", config.getName(), descCfg(config));
    }

    private static void onFFCfg(IConfigBase config) {
        FR.invalidateCaches();
        LOGGER.info("[ReignRender] frame filter config '{}' changed to: {}", config.getName(), descCfg(config));
    }

    private static void onFCfg(IConfigBase config) {
        FR.invalidateCaches();
        LOGGER.info("[ReignRender] filter config '{}' changed to: {}", config.getName(), descCfg(config));
        onRCfg(config);
    }

    private static void onRCfg(IConfigBase config) {
        LOGGER.info("[ReignRender] render config '{}' changed to: {}", config.getName(), descCfg(config));
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
    private static String descCfg(IConfigBase config) {
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
    private static class RHotkey implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            rebuildMeshes();
            return true;
        }
    }

    private static class OGui implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            GuiBase.openGui(new GConfigs());
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
    private static class PickE implements IHotkeyCallback {
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
                toggle(mc, Cfg.F.FILTERED_ENTITIES,
                        Registries.ENTITY_TYPE.getId(entityHit.getEntity().getType()).toString(),
                        "reignrender.message.pickEntityAdded", "reignrender.message.pickEntityRemoved",
                        eIcon(entityHit.getEntity().getType()));
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
            PickAcc.PickRes particle = pickP(mc);

            if (particle != null && particle.distance() < blockHitDistance) {
                toggle(mc, Cfg.F.FILTERED_PARTICLES,
                        Registries.PARTICLE_TYPE.getId(particle.type()).toString(),
                        "reignrender.message.pickParticleAdded", "reignrender.message.pickParticleRemoved", null);
                return true;
            }

            if (blockPos != null) {
                // Fluids have no hit result of their own; a non-empty fluid
                // state at the hit position means the crosshair is on a fluid.
                FluidState fluid = mc.world.getFluidState(blockPos);

                if (!fluid.isEmpty()) {
                    toggle(mc, Cfg.F.FILTERED_FLUIDS,
                            Registries.FLUID.getId(fluid.getFluid()).toString(),
                            "reignrender.message.pickFluidAdded", "reignrender.message.pickFluidRemoved",
                            new ItemStack(fluid.getFluid().getBucketItem()));
                    return true;
                }

                Block block = mc.world.getBlockState(blockPos).getBlock();

                // Blocks backed by a block entity are rendered via the block
                // entity filter, so picking them into the block list would be
                // a no-op (and they are cleaned out again on the next load).
                if (FR.isBlockManagedByBlockEntityFilter(block)) {
                    Toast.show(Text.translatable("reignrender.message.pickBlockManagedByBlockEntity",
                            Registries.BLOCK.getId(block).toString()), Toast.CE,
                            new ItemStack(block.asItem()));
                    return true;
                }

                toggle(mc, Cfg.F.FILTERED_BLOCKS,
                        Registries.BLOCK.getId(block).toString(),
                        "reignrender.message.pickBlockAdded", "reignrender.message.pickBlockRemoved",
                        new ItemStack(block.asItem()));
                return true;
            }

            Toast.show(Text.translatable("reignrender.message.pickNone"), Toast.CE);
            return true;
        }
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
            Toast.show(Text.translatable("reignrender.message.pickBiomeNone"), Toast.CE);
            return;
        }

        toggle(mc, Cfg.F.FILTERED_FOGS, biomeId,
                "reignrender.message.pickBiomeAdded", "reignrender.message.pickBiomeRemoved",
                bIcon(biomeId));
    }

    /**
     * Returns the spawn egg of the given entity type as the toast icon, or
     * null when the type has no spawn egg (then no icon is drawn).
     */
    private static ItemStack eIcon(EntityType<?> type) {
        Item egg = SpawnEggItem.forEntity(type);
        return egg != null && egg != Items.AIR ? new ItemStack(egg) : null;
    }

    /**
     * Returns an item that best represents the given biome for the toast icon.
     * The mapping favors the biome's signature block, plant or creature; biomes
     * without a fitting icon (e.g. the void) map to null so no icon is drawn.
     */
    private static ItemStack bIcon(String biomeId) {
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
    private static PickAcc.PickRes pickP(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            return null;
        }

        Camera camera = mc.gameRenderer.getCamera();
        return ((PickAcc) mc.particleManager)
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
        FR.invalidateCaches();
        Toast.show(Text.translatable(added ? addedKey : removedKey, Names.name(id)),
                added ? Toast.CA : Toast.CR, icon);
    }
}