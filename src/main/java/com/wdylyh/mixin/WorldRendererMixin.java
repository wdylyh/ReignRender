package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.SectionRenderState;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.List;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    // Empty SectionRenderState singleton, lazily initialized
    private static SectionRenderState EMPTY_BLOCK_STATE = null;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SectionRenderState getEmptyBlockState() {
        if (EMPTY_BLOCK_STATE == null) {
            EnumMap draws = new EnumMap(BlockRenderLayer.class);
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                draws.put(layer, List.of());
            }
            EMPTY_BLOCK_STATE = new SectionRenderState(null, draws, 1, new com.mojang.blaze3d.buffers.GpuBufferSlice[0]);
        }
        return EMPTY_BLOCK_STATE;
    }

    // ==================== 粒子 (Particles) ====================

    /**
     * Cancel renderParticles at rendering level.
     * Also clear particles to prevent memory leak.
     * This is rendering code, not tick/mechanism code.
     */
    @Inject(method = "renderParticles", at = @At("HEAD"), cancellable = true)
    private void onRenderParticles(CallbackInfo ci) {
        if (Configs.Disable.DISABLE_PARTICLES.getBooleanValue()) {
            // Clear particles to prevent memory leak (particles still accumulate
            // since we don't touch addParticle/addEmitter/tick)
            MinecraftClient.getInstance().particleManager.clearParticles();
            ci.cancel();
        }
    }

    // ==================== 方块 (Blocks) ====================

    /**
     * Return an empty SectionRenderState, so no GPU data is built at all.
     * This prevents the render task from being created (not cleared after creation).
     */
    @Inject(method = "renderBlockLayers", at = @At("HEAD"), cancellable = true)
    private void onRenderBlockLayers(CallbackInfoReturnable<SectionRenderState> ci) {
        if (Configs.Disable.DISABLE_BLOCKS.getBooleanValue()) {
            ci.setReturnValue(getEmptyBlockState());
        }
    }
}