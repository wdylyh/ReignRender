package com.wdylyh.mixin;

import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
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
public class FBER {

    // Universal falling block replacement: the render state carries the falling
    // block's block state in movingBlockRenderState.blockState. Swapping it
    // after updateRenderState fills it in makes the renderer draw the target
    // block's model and texture instead of the original one.
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/FallingBlockEntity;Lnet/minecraft/client/render/entity/state/FallingBlockEntityRenderState;F)V",
            at = @At("RETURN"))
    private void onUpdate(FallingBlockEntity ent, FallingBlockEntityRenderState st,
                          float td, CallbackInfo ci) {
        if (!Rpl.isReplaceEnabled() || st.movingBlockRenderState == null) {
            return;
        }
        // The reveal hotkey skips the replacement (shows the original block).
        if (FR.isReplaceBlocked(FR.TYPE_FALLING_BLOCKS)) {
            return;
        }
        BlockState bs = st.movingBlockRenderState.blockState;
        if (bs == null) {
            return;
        }
        // 用 FR 的注册表 ID 缓存，避免每个下落方块每帧 toString() 分配。
        String sid = FR.getBlockId(bs.getBlock());
        if (sid == null) {
            return;
        }
        String tid = Rpl.getReplacementFallingBlock(sid);
        if (tid == null) {
            return;
        }
        // 懒缓存的目标解析：避免每个下落方块都做 Identifier.tryParse + containsId + get。
        Block t = Rpl.getBlockTarget(tid);
        if (t != null && t != bs.getBlock()) {
            // 只替换渲染：共享属性（朝向等）保留原方块的值，世界数据不受影响。
            st.movingBlockRenderState.blockState = copyShared(bs, t.getDefaultState());
        }
    }

    private static BlockState copyShared(BlockState src, BlockState dst) {
        for (Property<?> prop : src.getProperties()) {
            if (dst.contains(prop)) {
                dst = copyP(src, dst, prop);
            }
        }
        return dst;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyP(BlockState src, BlockState dst,
                                                              Property<?> prop) {
        Property<T> tp = (Property<T>) prop;
        return dst.with(tp, src.get(tp));
    }
}