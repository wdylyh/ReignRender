package com.wdylyh.mixin;

import com.mojang.blaze3d.systems.VertexSorter;
import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.state.property.Property;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionBuilder.class)
public class Section {

    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    // Per-thread counter for the per-section block cap. Chunk sections are
    // built concurrently on the ChunkBuilder worker threads and SectionBuilder
    // is a single shared instance, so each thread counts its own section. The
    // counter is reset at the start of every build() call, which processes
    // exactly one 16x16x16 section. A single-element array avoids the Integer
    // boxing/unboxing of every incremented block.
    private static final ThreadLocal<int[]> BCNT = ThreadLocal.withInitial(() -> new int[1]);

    // Per-thread per-section constants, captured once at the start of every
    // build() call instead of once per block:
    //   [0] reveal key down state (native GLFW query, once per section)
    //   [1] reveal behavior code for TYPE_BLOCKS (hotkey mode read, once per
    //       section; the old per-block isRevealHeld/isFilterForced calls read
    //       the mode twice per block)
    //   [2] replace master switch state (once per section instead of per block)
    private static final ThreadLocal<int[]> SST = ThreadLocal.withInitial(() -> new int[3]);

    // build() returns a value (the built section data), so the HEAD injection
    // must use a CallbackInfoReturnable to match the target's descriptor.
    @Inject(method = "build", at = @At("HEAD"))
    private void onBuild(ChunkSectionPos sp, ChunkRendererRegion reg,
                         VertexSorter vs,
                         BlockBufferAllocatorStorage as,
                         CallbackInfoReturnable<?> cir) {
        BCNT.get()[0] = 0;
        boolean kd = FR.revealDown();
        int[] st = SST.get();
        st[0] = kd ? 1 : 0;
        st[1] = FR.bhv(FR.TYPE_BLOCKS, kd);
        st[2] = Rpl.isReplaceEnabled() ? 1 : 0;
    }

    // Replaces the block state of filtered blocks with air right where the
    // chunk mesh builder reads it from the region. Air produces no model
    // vertices, no fluid and no occlusion data, so the filtered block simply
    // does not exist in the mesh that is handed to the GPU. Every block is
    // decided individually, so the blacklist/whitelist applies per block id
    // instead of hiding everything at once.
    @Redirect(method = "build",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/render/chunk/ChunkRendererRegion;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState rplBlock(ChunkRendererRegion reg, BlockPos p) {
        BlockState st = reg.getBlockState(p);

        // The reveal behavior was decided once per section in onBuild;
        // reading it here is a single ThreadLocal lookup with no config reads.
        int[] ss = SST.get();
        int bh = ss[1];

        if (bh == FR.HOTKEY_BEHAVIOR_FORCE) {
            // Enable mode + hotkey held: force-hide every block, the same as
            // the block disable logic.
            return AIR;
        }
        if (bh == FR.HOTKEY_BEHAVIOR_BYPASS) {
            // Release mode + hotkey held: reveal the real block (no filter,
            // no replacement).
            return st;
        }
        if (bh == FR.HOTKEY_BEHAVIOR_INACTIVE) {
            // Enable mode + hotkey not held: the filters are inactive and the
            // world renders as-is, so neither the hide filter, the cap nor the
            // replacement apply here.
            return st;
        }

        // bh == NORMAL (release mode, hotkey not held): the filters, the
        // per-section block cap and the replacement all apply.
        if (Cfg.Off.DISABLE_BLOCKS.getBooleanValue() &&
                FR.isBlockFiltered(st, ss[0] != 0, bh)) {
            return AIR;
        }

        if (!st.isAir()) {
            int max = Cfg.G.MAX_BLOCKS_PER_CHUNK.getIntegerValue();

            if (max >= 0) {
                int[] cnt = BCNT.get();

                if (cnt[0] >= max) {
                    return AIR;
                }

                cnt[0]++;
            }
        }

        // Universal block replacement: while the master replace switch is on
        // and the source block id has a rule, the chunk mesh is built from the
        // target block's default state instead of the original one. This runs
        // after every hide filter so hidden blocks (air) stay hidden and the
        // reveal hotkey keeps showing the real world. The master switch state
        // is cached per section, so a disabled replace system costs one
        // ThreadLocal lookup per block and no config read. It only runs in
        // NORMAL state (enable mode suppresses it via the INACTIVE early
        // return above).
        if (ss[2] != 0) {
            String tid = Rpl.getReplacementBlock(FR.getBlockId(st.getBlock()));
            if (tid != null) {
                Block t = Rpl.getBlockTarget(tid);
                if (t != null && t != st.getBlock()) {
                    // Pure rendering swap: the mesh is built from the target
                    // block's model, but every property the two states share
                    // (facing, waterlogged, lit, half, ...) keeps the source
                    // block's value. The world data is never modified.
                    return copyShared(st, t.getDefaultState());
                }
            }
        }

        return st;
    }

    // Copies the source state's property values onto the target state for
    // every property both states share. This is what keeps a replaced block in
    // the original block's orientation/state (e.g. a chest or door pointing
    // the same way, a slab in the same half).
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