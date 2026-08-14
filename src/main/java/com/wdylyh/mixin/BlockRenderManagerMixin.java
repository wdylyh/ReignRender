package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import com.wdylyh.config.FilterRules;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderManager.class)
public class BlockRenderManagerMixin {

    // Hides individual fluid types during chunk mesh building. The filter only
    // applies while the master "disable fluids" toggle is on.
    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private void onRenderFluid(BlockPos pos, BlockRenderView world, VertexConsumer vertexConsumer,
                               BlockState blockState, FluidState fluidState,
                               CallbackInfo ci) {
        if (Configs.Disable.DISABLE_FLUIDS.getBooleanValue() &&
                FilterRules.isFluidFiltered(fluidState)) {
            ci.cancel();
        }
    }
}
