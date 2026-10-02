package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.ReplacementEngine;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderManager.class)
public class FluidFilterReplace {
    // renderFluid has no position on the replacement handler's FluidState
    // parameter, so the HEAD handler below hands it over through a ThreadLocal
    // (both handlers run on the same thread, HEAD inject first).
    @Unique
    private static final ThreadLocal<BlockPos> FLUID_POS = new ThreadLocal<>();

    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private void onFluid(BlockPos p, BlockRenderView w, VertexConsumer vc, BlockState bs, FluidState fs, CallbackInfo ci) {
        FLUID_POS.set(p);
        boolean kd = FilterEngine.revealDown();
        int bh = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_FLUIDS, kd);
        if (bh == FilterEngine.HOTKEY_BEHAVIOR_FORCE) {
            ci.cancel();
            return;
        }
        if (bh == FilterEngine.HOTKEY_BEHAVIOR_BYPASS) {
            return;
        }

        if (RenderConfig.Toggles.DISABLE_FLUIDS.getBooleanValue() &&
                FilterEngine.isFluidFiltered(fs, kd, bh)) {
            ci.cancel();
        }
    }
    @ModifyVariable(method = "renderFluid", at = @At("HEAD"), argsOnly = true, index = 5)
    private FluidState rplFluid(FluidState fs) {
        // No master-switch gate: the coordinate rules below are gated by the
        // coordinate replace toggle (global switch OFF), the global fallback
        // checks the master switch internally.
        if (fs == null || fs.isEmpty() || !ReplacementEngine.anyReplaceActive()) {
            return fs;
        }
        boolean kd = FilterEngine.revealDown();
        if (FilterEngine.isReplaceBlocked(FilterEngine.TYPE_FLUIDS, kd)) {
            return fs;
        }
        String src = FilterEngine.getFluidId(fs.getFluid());
        // Coordinate aware rules win over the global list: the position was
        // stashed by the HEAD handler above.
        String tid = null;
        BlockPos p = FLUID_POS.get();
        if (p != null) {
            tid = ReplacementEngine.getReplacementFluidAt(src, p.getX(), p.getY(), p.getZ(), FilterEngine.TYPE_FLUIDS);
        }
        if (tid == null) {
            tid = ReplacementEngine.getReplacementFluid(src);
        }
        if (tid == null) {
            return fs;
        }
        Fluid t = ReplacementEngine.getFluidTarget(tid);
        if (t == null || t == fs.getFluid()) {
            return fs;
        }
        return t.getDefaultState();
    }
}