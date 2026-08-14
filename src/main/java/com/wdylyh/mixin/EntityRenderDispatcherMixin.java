package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import com.wdylyh.config.FilterRules;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public class EntityRenderDispatcherMixin {

    // Per-type blacklist/whitelist filter (registry id based).
    // shouldRender() is called with the concrete Entity before the render state
    // is created, so filtered entities are skipped as early as possible.
    // The filter only applies while the master "disable entities" toggle is on;
    // when it is off every entity renders normally. The independent
    // "disable falling blocks" toggle also stops falling block entities here.
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void onShouldRender(Entity entity, Frustum frustum,
                                double offsetX, double offsetY, double offsetZ,
                                CallbackInfoReturnable<Boolean> cir) {
        if ((Configs.Disable.DISABLE_ENTITIES.getBooleanValue() &&
                FilterRules.isEntityFiltered(entity.getType())) ||
                (Configs.Disable.DISABLE_FALLING_BLOCKS.getBooleanValue() &&
                        entity instanceof FallingBlockEntity)) {
            cir.setReturnValue(false);
        }
    }
}