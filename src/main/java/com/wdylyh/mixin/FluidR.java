package com.wdylyh.mixin;

import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
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
public class FluidR {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fluid(BlockRenderView w, BlockPos p, VertexConsumer vc,
                       BlockState bs, FluidState fs, CallbackInfo ci) {
        boolean kd = FR.revealDown();
        // 合并后的行为码只求值一次（原来 isFilterForced / isRevealHeld 各读一次模式）。
        int bhv = FR.bhv(FR.TYPE_FLUIDS, kd);

        // Force-hide mode + hotkey held: hide every fluid.
        if (bhv == FR.HOTKEY_BEHAVIOR_FORCE) {
            ci.cancel();
            return;
        }

        // Reveal mode + hotkey held: bypass the fluid filter (the hotkey
        // callback rebuilt the meshes to show fluids when the key was pressed).
        if (bhv == FR.HOTKEY_BEHAVIOR_BYPASS) {
            return;
        }

        // Full kill only applies when the master toggle is on AND the filter
        // mode is OFF (everything hidden); otherwise per-type filtering in
        // BlockRenderManager.renderFluid handles the blacklist/whitelist.
        if (Cfg.Off.DISABLE_FLUIDS.getBooleanValue() &&
                Cfg.F.FLUID_MODE.getOptionValue() == Cfg.F.MODE_OFF) {
            ci.cancel();
        }
    }
}