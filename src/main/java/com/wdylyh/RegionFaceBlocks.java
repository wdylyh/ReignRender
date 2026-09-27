package com.wdylyh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wdylyh.ModReference;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Shadow blocks backing the region face modification ("区域面修改").
 *
 * <p>To swap a block's (or a falling block's) textures inside a region, the
 * chunk mesh builder renders a shadow block instead of the original one. Every
 * vanilla block therefore gets a shadow registered under
 * {@code reignrender:rface_<name>}; the blockstate/model/texture resources of a
 * shadow block are generated into the ReignRender_RegionFace resource pack by
 * {@code RegionFacePacks#ensureResources()} only when the block id is actually
 * listed in a region entry, so nothing is generated for unlisted blocks.</p>
 *
 * <p>Like {@link ShadowBlocks} these shadow blocks are pure render helpers:
 * never placed in the world, no item form, no loot table. Registration collects
 * the blocks into a list first — registering while iterating
 * {@link Registries#BLOCK} throws a ConcurrentModificationException.</p>
 */
public class RegionFaceBlocks {

    /** Vanilla block -> its region face-mod render block. */
    public static final Map<Block, Block> SHADOWS = new HashMap<>();

    private RegionFaceBlocks() {}

    /** Registers one shadow block per vanilla block (skipping the mod's own shadows). */
    public static void register() {
        List<Block> vanilla = new ArrayList<>();

        for (Block b : Registries.BLOCK) {
            Identifier id = Registries.BLOCK.getId(b);

            // Skip the shadow blocks themselves (both the falling shadows and
            // the ones this class registered earlier in the loop) and the
            // falling shadows' targets handled by ShadowBlocks.
            if (id.getNamespace().equals(ModReference.MOD_ID)
                    || ShadowBlocks.SHADOWS.containsKey(b)) {
                continue;
            }

            vanilla.add(b);
        }

        for (Block b : vanilla) {
            Identifier id = ReignRenderMod.id("rface_" + Registries.BLOCK.getId(b).getPath());
            RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, id);
            Block shadow = Registry.register(Registries.BLOCK, key,
                    new Block(AbstractBlock.Settings.create().registryKey(key)));
            SHADOWS.put(b, shadow);
        }

        ReignRenderMod.LOGGER.info("[ReignRender] registered {} region face-mod shadow blocks", SHADOWS.size());
    }

    /** The region face-mod shadow block of the given vanilla block, or null. */
    public static Block getShadow(Block vanilla) {
        return SHADOWS.get(vanilla);
    }
}
