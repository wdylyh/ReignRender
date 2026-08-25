package com.wdylyh.mixin;

import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderManager.class)
public class BRM {
    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private void onFluid(BlockPos p, BlockRenderView w, VertexConsumer vc, BlockState bs, FluidState fs, CallbackInfo ci) {
        boolean kd = FR.revealDown();
        int bh = FR.bhv(FR.TYPE_FLUIDS, kd);
        if (bh == FR.HOTKEY_BEHAVIOR_FORCE) {
            ci.cancel();
            return;
        }
        if (bh == FR.HOTKEY_BEHAVIOR_BYPASS) {
            return;
        }

        if (Cfg.Off.DISABLE_FLUIDS.getBooleanValue() &&
                FR.isFluidFiltered(fs, kd, bh)) {
            ci.cancel();
        }
    }
    @ModifyVariable(method = "renderFluid", at = @At("HEAD"), argsOnly = true, index = 5)
    private FluidState rplFluid(FluidState fs) {
        if (!Rpl.isReplaceEnabled() || fs == null || fs.isEmpty()) {
            return fs;
        }
        boolean kd = FR.revealDown();
        if (FR.isReplaceBlocked(FR.TYPE_FLUIDS, kd)) {
            return fs;
        }
        String tid = Rpl.getReplacementFluid(FR.getFluidId(fs.getFluid()));
        if (tid == null) {
            return fs;
        }
        Fluid t = Rpl.getFluidTarget(tid);
        if (t == null || t == fs.getFluid()) {
            return fs;
        }
        return t.getDefaultState();
    }
}