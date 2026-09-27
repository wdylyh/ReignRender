package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.CoordinateFilter;
import com.wdylyh.config.FilterEngine;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public class FluidHideFilter {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fluid(BlockRenderView w, BlockPos p, VertexConsumer vc,
                       BlockState bs, FluidState fs, CallbackInfo ci) {
        boolean kd = FilterEngine.revealDown();
        // 合并后的行为码只求值一次（原来 isFilterForced / isRevealHeld 各读一次模式）。
        int bhv = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_FLUIDS, kd);

        // Force-hide mode + hotkey held: hide every fluid.
        if (bhv == FilterEngine.HOTKEY_BEHAVIOR_FORCE) {
            ci.cancel();
            return;
        }

        // Reveal mode + hotkey held: bypass the fluid filter (the hotkey
        // callback rebuilt the meshes to show fluids when the key was pressed).
        if (bhv == FilterEngine.HOTKEY_BEHAVIOR_BYPASS) {
            return;
        }

        // Full kill only applies when the master toggle is on AND the filter
        // mode is OFF (everything hidden); otherwise per-type filtering in
        // BlockRenderManager.renderFluid handles the blacklist/whitelist.
        if (RenderConfig.Toggles.DISABLE_FLUIDS.getBooleanValue() &&
                RenderConfig.Filters.FLUID_MODE.getOptionValue() == RenderConfig.Filters.MODE_OFF) {
            ci.cancel();
            return;
        }

        // Coordinate filter: independent of the fluid filter master toggle.
        // The position p is the absolute world coordinate.
        if (bhv == FilterEngine.HOTKEY_BEHAVIOR_NORMAL && CoordinateFilter.isFluidHidden(p, fs)) {
            ci.cancel();
        }
    }
}