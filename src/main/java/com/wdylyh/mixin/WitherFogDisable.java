package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.FilterEngine;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the fog-thickening applied while a wither boss is nearby.
 * <p>
 * The wither effect fog is not part of the fog color: after the fog color is
 * computed, GameRenderer calls {@link BossBarHud#shouldThickenFog()} and passes
 * the resulting boolean to WorldRenderer, which shrinks the fog distance while
 * it is true. Forcing it to false disables the effect fog independently of the
 * fog color filter.
 */
@Mixin(BossBarHud.class)
public abstract class WitherFogDisable {

    @Inject(method = "shouldThickenFog", at = @At("RETURN"), cancellable = true)
    private void onFog(CallbackInfoReturnable<Boolean> cir) {
        // 每帧只查询一次原生按键状态，行为码也只求值一次（替代原来
        // isFilterForced + isWitherFogFiltered 各读一次模式）。
        boolean kd = FilterEngine.revealDown();
        int bh = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_FOG, kd);
        // Force-hide mode + hotkey held: remove the effect regardless of the
        // master "disable fog" toggle.
        if (bh == FilterEngine.HOTKEY_BEHAVIOR_FORCE
                || (bh == FilterEngine.HOTKEY_BEHAVIOR_NORMAL
                && RenderConfig.Toggles.DISABLE_FOG.getBooleanValue()
                && FilterEngine.isWitherFogFiltered(kd, bh))) {
            cir.setReturnValue(false);
        }
    }
}