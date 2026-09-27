package com.wdylyh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.DragonEggBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Shadow blocks backing the falling-block face modification.
 *
 * <p>A falling block entity renders the block state it carries, so its
 * texture while falling can never differ from the placed block's texture.
 * To split the two, every block that can become a falling block entity
 * ({@link FallingBlock}, anvils, the dragon egg) gets a "shadow" block
 * registered under {@code reignrender:falling_<name>}. While the entity is
 * falling the renderer swaps the carried state for the shadow block's state
 * (see {@code com.wdylyh.mixin.FallingBlockReplace}); the shadow's model
 * references textures under {@code reignrender:falling/...} which the face
 * mod GUI can edit. Once the block lands, the real block state renders with
 * its original textures, so falling and placed appearances are independent.</p>
 *
 * <p>The shadow blocks are pure render helpers: they are never placed in the
 * world, have no item form and no loot table. Their blockstate/model/texture
 * resources are generated into the face-mod resource pack by
 * {@code FaceModPacks#ensureShadowResources()}.</p>
 */
public class ShadowBlocks {

    /** Vanilla block that can fall -> its shadow render block. */
    public static final Map<Block, Block> SHADOWS = new HashMap<>();

    private ShadowBlocks() {}

    /** Registers one shadow block per fall-capable vanilla block. */
    public static void register() {
        List<Block> fallers = new ArrayList<>();

        for (Block b : Registries.BLOCK) {
            if (b instanceof FallingBlock || b instanceof AnvilBlock || b instanceof DragonEggBlock) {
                fallers.add(b);
            }
        }

        for (Block b : fallers) {
            Identifier id = ReignRenderMod.id("falling_" + Registries.BLOCK.getId(b).getPath());
            RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, id);
            Block shadow = Registry.register(Registries.BLOCK, key,
                    new Block(AbstractBlock.Settings.create().registryKey(key)));
            SHADOWS.put(b, shadow);
        }

        ReignRenderMod.LOGGER.info("[ReignRender] registered {} falling-block shadow blocks", SHADOWS.size());
    }

    /** The shadow block rendering the given vanilla block while falling, or null. */
    public static Block getShadow(Block vanilla) {
        return SHADOWS.get(vanilla);
    }
}
