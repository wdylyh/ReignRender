package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import com.wdylyh.config.FilterRules;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SectionBuilder.class)
public class SectionBuilderMixin {

    // Replaces the block state of filtered blocks with air right where the
    // chunk mesh builder reads it from the region. Air produces no model
    // vertices, no fluid and no occlusion data, so the filtered block simply
    // does not exist in the mesh that is handed to the GPU. Every block is
    // decided individually, so the blacklist/whitelist applies per block id
    // instead of hiding everything at once.
    @Redirect(method = "build",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/render/chunk/ChunkRendererRegion;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState replaceFilteredBlockState(ChunkRendererRegion region, BlockPos pos) {
        BlockState state = region.getBlockState(pos);

        if (Configs.Disable.DISABLE_BLOCKS.getBooleanValue() &&
                FilterRules.isBlockFiltered(state)) {
            return Blocks.AIR.getDefaultState();
        }

        return state;
    }
}
