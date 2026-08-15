package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import com.wdylyh.config.FilterRules;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.render.block.entity.BlockEntityRenderManager.class)
public class BlockEntityRenderManagerMixin {

    // Per-variant blacklist/whitelist filter (block registry id based, e.g. each
    // shulker box color or sign wood is matched independently). The filter only
    // applies while the master "disable block entities" toggle is on; when it
    // is off every block entity renders normally. With the toggle on, an OFF
    // filter mode hides everything (keep sign text exception applies).
    @Inject(method = "getRenderState", at = @At("HEAD"), cancellable = true)
    private void onGetBlockEntityRenderState(BlockEntity blockEntity, float tickProgress,
                                             ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay,
                                             CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if (Configs.Disable.DISABLE_BLOCK_ENTITIES.getBooleanValue() &&
                FilterRules.isBlockEntityFiltered(blockEntity)) {
            // When every block entity is hidden (filter mode OFF) and "Keep Sign
            // Text" is enabled, allow sign states so the text can still render.
            // instanceof is cheapest and most discriminating, so it goes first.
            if (blockEntity instanceof SignBlockEntity &&
                    Configs.Generic.KEEP_SIGN_TEXT.getBooleanValue() &&
                    Configs.Filter.BLOCK_ENTITY_MODE.getOptionValue() == Configs.Filter.MODE_OFF) {
                return;
            }
            cir.cancel();
        }
    }
}