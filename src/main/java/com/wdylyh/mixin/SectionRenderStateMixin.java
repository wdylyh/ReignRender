package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.BlockRenderLayerGroup;
import net.minecraft.client.render.SectionRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderState.class)
public class SectionRenderStateMixin {

    @Inject(method = "renderSection", at = @At("HEAD"), cancellable = true)
    private void onRenderSection(BlockRenderLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
        if (Configs.Disable.DISABLE_BLOCKS.getBooleanValue()) {
            ci.cancel();
        }
    }
}