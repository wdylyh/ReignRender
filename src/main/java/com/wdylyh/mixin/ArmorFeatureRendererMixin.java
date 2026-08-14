package com.wdylyh.mixin;

import com.wdylyh.config.Configs;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public class ArmorFeatureRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderArmor(CallbackInfo ci) {
        if (Configs.Disable.DISABLE_ARMOR.getBooleanValue()) {
            ci.cancel();
        }
    }
}