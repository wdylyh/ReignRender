package com.wdylyh.mixin;

import com.mojang.blaze3d.systems.VertexSorter;
import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.CoordinateFilter;
import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.ReplacementEngine;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.state.property.Property;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import java.util.List;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionBuilder.class)
public class ChunkMeshFilter {

    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    // Per-thread per-section constants, captured once at the start of every
    // build() call instead of once per block:
    //   [0] reveal key down state (native GLFW query, once per section)
    //   [1] reveal behavior code for TYPE_BLOCKS (hotkey mode read, once per
    //       section; the old per-block isRevealHeld/isFilterForced calls read
    //       the mode twice per block)
    //   [2] replace master switch state (once per section instead of per block)
    //   [3] 1 when the current position was hidden with its fluid preserved
    //       (set by hidden(), cleared at the start of every replaceBlockState call)
    //   [4] reveal behavior code for TYPE_BLOCK_ENTITIES (once per section):
    //       while the block entity category is temporarily suspended (BYPASS /
    //       INACTIVE) hidden block-entity blocks must still publish their block
    //       entity, otherwise the "disable block entities" release hotkey could
    //       never bring a sign / chest back (its whole look lives in the block
    //       entity renderer).
    //   [5] 1 when the current position was hidden and its block entity must
    //       still be collected (set by hidden(), cleared per position together
    //       with slot [3])
    private static final ThreadLocal<int[]> sectionBuildState = ThreadLocal.withInitial(() -> new int[6]);

    // build() returns a value (the built section data), so the HEAD injection
    // must use a CallbackInfoReturnable to match the target's descriptor.
    @Inject(method = "build", at = @At("HEAD"))
    private void onSectionBuildHead(ChunkSectionPos sp, ChunkRendererRegion reg,
                         VertexSorter vs,
                         BlockBufferAllocatorStorage as,
                         CallbackInfoReturnable<?> cir) {
        boolean kd = FilterEngine.revealDown();
        int[] st = sectionBuildState.get();
        st[0] = kd ? 1 : 0;
        st[1] = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_BLOCKS, kd);
        st[2] = ReplacementEngine.isReplaceEnabled() ? 1 : 0;
        st[4] = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_BLOCK_ENTITIES, kd);
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
    private BlockState replaceBlockState(ChunkRendererRegion reg, BlockPos p) {
        BlockState st = reg.getBlockState(p);

        // The reveal behavior was decided once per section in onSectionBuildHead;
        // reading it here is a single ThreadLocal lookup with no config reads.
        int[] ss = sectionBuildState.get();
        int bh = ss[1];
        ss[3] = 0;
        ss[5] = 0;

        if (bh == FilterEngine.HOTKEY_BEHAVIOR_FORCE) {
            // Enable mode + hotkey held: force-hide every block, the same as
            // the block disable logic.
            return hidden(st, ss);
        }
        if (bh == FilterEngine.HOTKEY_BEHAVIOR_BYPASS) {
            // Release mode + hotkey held: reveal the real block (no filter,
            // no replacement).
            return st;
        }
        if (bh == FilterEngine.HOTKEY_BEHAVIOR_INACTIVE) {
            // Enable mode + hotkey not held: the filters are inactive and the
            // world renders as-is, so neither the hide filter nor the
            // replacement apply here.
            return st;
        }

        // bh == NORMAL (release mode, hotkey not held): the filters and the
        // replacement all apply.
        // Block-category hiding exempts block-entity-managed blocks (signs,
        // banners, chests, shulker boxes, ...): they have no static chunk model
        // at all - their whole look is drawn by the block entity renderer - so
        // hiding them here would only delete their block entity from the
        // section. Their visibility is governed by the block entity category
        // alone (the same set cleanUpBlockFilter removes from the block filter
        // lists), otherwise "disable blocks" would irreversibly swallow signs
        // even with "disable block entities" off. FORCE above still hides them
        // on purpose: force-hide means a clean terrain view.
        if (RenderConfig.Toggles.DISABLE_BLOCKS.getBooleanValue()
                && !FilterEngine.isBlockManagedByBlockEntityFilter(st.getBlock())
                && FilterEngine.isBlockFiltered(st, ss[0] != 0, bh)) {
            return hidden(st, ss);
        }

        // Coordinate filter: independent of the block filter master toggle.
        // The position p is the absolute world coordinate, so it is matched
        // directly against the entry regions (in every dimension).
        if (CoordinateFilter.isBlockHidden(p, st)) {
            return hidden(st, ss);
        }

        // Region face-mod ("区域面修改"): inside a region entry the block
        // renders through its rface shadow block whose textures are editable
        // in the region face GUI. Active only while the global face-mod is
        // off (mutual exclusion, same as the region replacements). Block
        // entities are not covered (no generic texture hook) and fluids are
        // skipped (fluid sprites are not region-swappable in 1.21.11).
        if (com.wdylyh.config.RegionFacePacks.active()
                && !FilterEngine.isBlockManagedByBlockEntityFilter(st.getBlock())
                && st.getFluidState().isEmpty()) {
            String bid = FilterEngine.getBlockId(st.getBlock());
            if (bid != null && com.wdylyh.config.RegionFaceEngine.isBlockFaceAt(bid, p.getX(), p.getY(), p.getZ())) {
                Block sh = com.wdylyh.RegionFaceBlocks.getShadow(st.getBlock());
                if (sh != null) {
                    return copyShared(st, sh.getDefaultState());
                }
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
            // Coordinate aware rules win over the global list: a rule inside
            // the region the block sits in (position p) replaces directly,
            // anything else falls back to the global per-category list.
            String sourceId = FilterEngine.getBlockId(st.getBlock());
            String tid = ReplacementEngine.getReplacementBlockAt(sourceId, p.getX(), p.getY(), p.getZ());
            if (tid == null) {
                tid = ReplacementEngine.getReplacementBlock(sourceId);
            }
            if (tid != null) {
                Block t = ReplacementEngine.getBlockTarget(tid);
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

    // Hiding a block must not hide the fluid at the same position: fluids are
    // controlled by their own filter (DISABLE_FLUIDS / the fluid lists), so a
    // water block removed through the block list still keeps its water body.
    // Returning the real state keeps BlockState.getFluidState() non-empty for
    // the fluid pass of SectionBuilder.build; the two redirects below then
    // suppress the block model and the block entity of this position only.
    // Slot [4] suspend check: while the block entity category is temporarily
    // suspended (release mode with the hotkey held = BYPASS, enable mode with
    // the hotkey up = INACTIVE), a position hidden here (block list or
    // coordinate filter) must still publish its block entity. Block-entity
    // blocks have no static chunk model at all - their whole look is drawn by
    // the block entity renderer - so without this the block entity release
    // hotkey could never bring them back while the block category keeps them
    // hidden. Block-entity-managed blocks never reach hidden() through the
    // block filter master toggle (exempted in replaceBlockState); they only
    // get here via FORCE (which wants a clean terrain view, no BE publication
    // since slot [4] is FORCE or NORMAL there) or the coordinate filter.
    private static BlockState hidden(BlockState st, int[] ss) {
        if (st.hasBlockEntity() &&
                (ss[4] == FilterEngine.HOTKEY_BEHAVIOR_BYPASS ||
                 ss[4] == FilterEngine.HOTKEY_BEHAVIOR_INACTIVE)) {
            ss[5] = 1;
        }
        if (st.getFluidState().isEmpty()) {
            return AIR;
        }
        ss[3] = 1;
        return st;
    }

    // The position that replaceBlockState hid with its fluid preserved must not render
    // its model or its block entity. Both redirects run on the same BlockState
    // that replaceBlockState returned for the current position and the bytecode order
    // (getBlockState -> hasBlockEntity -> getFluidState/renderFluid ->
    // renderBlock) guarantees the per-position flag in slot [3] is in sync
    // with the state being processed.
    @Redirect(method = "build",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/block/BlockState;hasBlockEntity()Z"))
    private boolean noBlockEntity(BlockState st) {
        int[] ss = sectionBuildState.get();
        // Slot [5]: the position was hidden but its block entity category is
        // temporarily suspended, so the real block entity (fetched from the
        // region by SectionBuilder right after this call) must be collected
        // and rendered even though the block state here was replaced by air.
        if (ss[5] != 0) {
            return true;
        }
        return ss[3] == 0 && st.hasBlockEntity();
    }

    // The block model is suppressed one step below the render-type check that
    // SectionBuilder.build does, because fabric-renderer-indigo's
    // SectionCompilerMixin redirects that exact BlockState.getRenderType()
    // call and a second redirect of the same call site fails Indigo's
    // injection check, breaking world join entirely. renderBlock is the actual
    // vertex emission call, so skipping it leaves the position model-less
    // while the fluid pass (renderFluid) still renders the real state.
    @Redirect(method = "build",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderBlock(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLjava/util/List;)V"))
    private void noBlockModel(BlockRenderManager mgr, BlockState st, BlockPos p,
                              BlockRenderView world, MatrixStack ms,
                              VertexConsumer vc, boolean ao, List<BlockModelPart> parts) {
        if (sectionBuildState.get()[3] == 0) {
            mgr.renderBlock(st, p, world, ms, vc, ao, parts);
        }
    }

    // Copies the source state's property values onto the target state for
    // every property both states share. This is what keeps a replaced block in
    // the original block's orientation/state (e.g. a chest or door pointing
    // the same way, a slab in the same half).
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