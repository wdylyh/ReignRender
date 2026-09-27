package com.wdylyh.mixin;

import com.wdylyh.ShadowBlocks;
import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.FaceModPacks;
import com.wdylyh.config.ReplacementEngine;
import com.wdylyh.config.RenderConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.entity.FallingBlockEntityRenderer;
import net.minecraft.client.render.entity.state.FallingBlockEntityRenderState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.state.property.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntityRenderer.class)
public class FallingBlockReplace {

    // Universal falling block replacement: the render state carries the falling
    // block's block state in movingBlockRenderState.blockState. Swapping it
    // after updateRenderState fills it in makes the renderer draw the target
    // block's model and texture instead of the original one.
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/FallingBlockEntity;Lnet/minecraft/client/render/entity/state/FallingBlockEntityRenderState;F)V",
            at = @At("RETURN"))
    private void onUpdate(FallingBlockEntity ent, FallingBlockEntityRenderState st,
                          float td, CallbackInfo ci) {
        if (st.movingBlockRenderState == null) {
            return;
        }
        BlockState bs = st.movingBlockRenderState.blockState;
        if (bs == null) {
            return;
        }
        // 用 FilterEngine 的注册表 ID 缓存，避免每个下落方块每帧 toString() 分配。
        String sid = FilterEngine.getBlockId(bs.getBlock());
        if (sid == null) {
            return;
        }

        // (1) Explicit "source=target" replacement rules keep the highest priority.
        if (ReplacementEngine.isReplaceEnabled() && !FilterEngine.isReplaceBlocked(FilterEngine.TYPE_FALLING_BLOCKS)) {
            // 坐标规则优先于全局列表：以下落方块当前位置落点为准。
            String tid = ReplacementEngine.getReplacementFallingBlockAt(sid, ent.getX(), ent.getY(), ent.getZ());
            if (tid == null) {
                tid = ReplacementEngine.getReplacementFallingBlock(sid);
            }
            if (tid != null) {
                // 懒缓存的目标解析：避免每个下落方块都做 Identifier.tryParse + containsId + get。
                Block t = ReplacementEngine.getBlockTarget(tid);
                if (t != null && t != bs.getBlock()) {
                    // 只替换渲染：共享属性（朝向等）保留原方块的值，世界数据不受影响。
                    st.movingBlockRenderState.blockState = copyShared(bs, t.getDefaultState());
                }
                return;
            }
        }

        // (2) Face-mod falling shadow: while the entity is falling, render the
        // shadow block whose textures (reignrender:falling/...) are editable in
        // the face-mod GUI; the landed block itself keeps its own textures.
        if (!RenderConfig.General.ENABLE_FACE_MOD.getBooleanValue() || !FaceModPacks.isPackActive()) {
            return;
        }
        if (!RenderConfig.Face.FACE_FALLING_BLOCKS.getStrings().contains(sid)) {
            return;
        }
        Block shadow = ShadowBlocks.getShadow(bs.getBlock());
        if (shadow != null) {
            // The shadow is a plain block without properties, so the default
            // state is all the renderer needs.
            st.movingBlockRenderState.blockState = shadow.getDefaultState();
            return;
        }

        // (3) Region face-mod falling shadow: same idea, but the rface shadow
        // block only applies while the entity is inside a region entry and the
        // global face-mod is off (mutual exclusion).
        if (!com.wdylyh.config.RegionFacePacks.active()) {
            return;
        }
        if (!com.wdylyh.config.RegionFaceEngine.isBlockFaceAt(sid, ent.getX(), ent.getY(), ent.getZ())) {
            return;
        }
        Block rfShadow = com.wdylyh.RegionFaceBlocks.getShadow(bs.getBlock());
        if (rfShadow != null) {
            st.movingBlockRenderState.blockState = rfShadow.getDefaultState();
        }
    }

    private static BlockState copyShared(BlockState src, BlockState dst) {
        for (Property<?> prop : src.getProperties()) {
            if (dst.contains(prop)) {
                dst = copyProperty(src, dst, prop);
            }
        }
        return dst;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyProperty(BlockState src, BlockState dst,
                                                              Property<?> prop) {
        Property<T> tp = (Property<T>) prop;
        return dst.with(tp, src.get(tp));
    }
}