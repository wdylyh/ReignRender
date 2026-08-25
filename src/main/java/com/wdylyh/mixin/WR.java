package com.wdylyh.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WR {

    // ==================== 粒子 (Particles) ====================

    @Inject(method = "renderParticles", at = @At("HEAD"), cancellable = true)
    private void particles(FrameGraphBuilder fgb,
                           GpuBufferSlice gbuf, CallbackInfo ci) {
        // Full kill only applies when the master toggle is on AND the filter
        // mode is OFF (everything hidden); otherwise per-type filtering in
        // ParticleManager.addParticle handles the blacklist/whitelist.
        if (Cfg.Off.DISABLE_PARTICLES.getBooleanValue() &&
                Cfg.F.PARTICLE_MODE.getOptionValue() == Cfg.F.MODE_OFF) {
            MinecraftClient.getInstance().particleManager.clearParticles();
            ci.cancel();
        }
    }

    // ==================== 实体 (Entities) ====================

    // fillEntityRenderStates() is invoked once per frame to rebuild the world
    // render state, and every EntityRenderManager.shouldRender() call for that
    // frame happens inside it. Resetting the counter here gives the entity
    // count cap a clean per-frame budget.
    @Inject(method = "fillEntityRenderStates", at = @At("HEAD"))
    private void fillStates(Camera cam, Frustum f,
                            RenderTickCounter rtc,
                            WorldRenderState wrs, CallbackInfo ci) {
        FR.frame();
    }

    // ==================== 方块轮廓 (Block outline) ====================

    @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), cancellable = true)
    private void blockOutline(VertexConsumerProvider.Immediate imm,
                              MatrixStack m, boolean soft,
                              WorldRenderState wrs, CallbackInfo ci) {
        if (Cfg.Off.DISABLE_BLOCK_OUTLINE.getBooleanValue()) {
            ci.cancel();
        }
    }
}