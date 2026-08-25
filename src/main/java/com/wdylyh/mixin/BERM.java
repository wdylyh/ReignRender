package com.wdylyh.mixin;

import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.render.block.entity.BlockEntityRenderManager.class)
public class BERM {

    // Per-variant blacklist/whitelist filter (block registry id based, e.g. each
    // shulker box color or sign wood is matched independently). The filter only
    // applies while the master "disable block entities" toggle is on; when it
    // is off every block entity renders normally. With the toggle on, an OFF
    // filter mode hides everything (keep sign text exception applies).
    @Inject(method = "getRenderState", at = @At("HEAD"), cancellable = true)
    private void getState(BlockEntity be, float tp,
                          ModelCommandRenderer.CrumblingOverlayCommand co,
                          CallbackInfoReturnable<BlockEntityRenderState> cbr) {
        boolean kd = FR.revealDown();
        // 合并后的行为码只求值一次，替代原 isFilterForced + isBlockEntityFiltered 各读一次模式。
        int bhv = FR.bhv(FR.TYPE_BLOCK_ENTITIES, kd);

        // Force-hide mode + hotkey held: hide every block entity, overriding the
        // per-toggle gates below (and the keep-sign-text exception).
        if (bhv == FR.HOTKEY_BEHAVIOR_FORCE) {
            cbr.cancel();
            return;
        }

        if (bhv == FR.HOTKEY_BEHAVIOR_NORMAL
                && Cfg.Off.DISABLE_BLOCK_ENTITIES.getBooleanValue()
                && FR.isBlockEntityFiltered(be, kd, bhv)) {
            // When every block entity is hidden (filter mode OFF) and "Keep Sign
            // Text" is enabled, allow sign states so the text can still render.
            // instanceof is cheapest and most discriminating, so it goes first.
            if (be instanceof SignBlockEntity &&
                    Cfg.G.KEEP_SIGN_TEXT.getBooleanValue() &&
                    Cfg.F.BLOCK_ENTITY_MODE.getOptionValue() == Cfg.F.MODE_OFF) {
                return;
            }
            cbr.cancel();
        }
    }

    // Universal block entity replacement: the source id is the block id the
    // block entity sits on (e.g. "minecraft:chest"), and the target is looked
    // up as another block id. The matching BlockEntityType is the one that
    // supports the target block's default state, and a fresh instance is
    // created at the same position and attached to the same world, so the
    // render state extraction renders the target block entity instead.
    // Holding the reveal key skips the swap, showing the original.
    @ModifyVariable(method = "getRenderState", at = @At("HEAD"), argsOnly = true, index = 1)
    private BlockEntity repBe(BlockEntity be) {
        if (!Rpl.isReplaceEnabled() || be == null) {
            return be;
        }
        // The reveal key state is only queried when replacement is enabled,
        // so the native GLFW lookup is skipped for the common no-replace case.
        boolean kd = FR.revealDown();
        if (FR.isReplaceBlocked(FR.TYPE_BLOCK_ENTITIES, kd)) {
            return be;
        }
        String sId = FR.getBlockId(be.getCachedState().getBlock());
        String tId = Rpl.getReplacementBlockEntity(sId);
        if (tId == null) {
            return be;
        }
        // 懒缓存的目标解析：避免每个方块实体都做 Identifier.tryParse + containsId + get。
        Block tBlock = Rpl.getBlockTarget(tId);
        if (tBlock == null || tBlock == be.getCachedState().getBlock()) {
            return be;
        }
        BlockState tState = tBlock.getDefaultState();
        for (BlockEntityType<?> t : Registries.BLOCK_ENTITY_TYPE) {
            if (!t.supports(tState)) {
                continue;
            }
            BlockEntity rep = t.instantiate(be.getPos(), tState);
            if (rep != null) {
                World w = be.getWorld();
                if (w != null) {
                    rep.setWorld(w);
                }
                return rep;
            }
        }
        return be;
    }
}