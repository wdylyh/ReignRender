package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.FilterEngine;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables the night vision brightness when the night vision fog identity is
 * filtered. getNightVisionStrength is the single strength entry point used by
 * both the fog color (FogRenderer lerps the fog RGB towards white) and the
 * lightmap (LightmapTextureManager brightens the light texture), so forcing the
 * return value to 0 disables both the fog brightening and the lightmap
 * brightening at once.
 */
@Mixin(GameRenderer.class)
public abstract class NightVisionDisable {

    @Inject(method = "getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F",
            at = @At("RETURN"), cancellable = true)
    private static void onNV(LivingEntity ent, float td, CallbackInfoReturnable<Float> cir) {
        if (RenderConfig.Toggles.DISABLE_FOG.getBooleanValue()
                && FilterEngine.isNightVisionFiltered(FilterEngine.revealDown())) {
            cir.setReturnValue(0.0f);
        }
    }
}