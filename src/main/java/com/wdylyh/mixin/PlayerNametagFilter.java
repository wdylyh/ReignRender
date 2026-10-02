package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.CoordinateFilter;
import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.ReplacementEngine;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerNametagFilter {

    // PlayerEntityRenderer overrides renderLabelIfPresent(), so the base class
    // injection in EntityNametagFilter never fires for players. Intercept the
    // concrete override here to hide player name tags too.
    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void label(PlayerEntityRenderState st, MatrixStack m,
                       OrderedRenderCommandQueue q, CameraRenderState cam,
                       CallbackInfo ci) {
        // The filter decides against the ORIGINAL name; only surviving tags get
        // their text replaced. The native GLFW key query (the most expensive
        // step of the filter chain) only runs when the name tag filter is
        // actually on; the replacement path needs no key state at all.
        if (st.displayName == null) {
            return;
        }
        String src = st.displayName.getString();
        boolean kd = FilterEngine.revealDown();
        if (RenderConfig.Toggles.DISABLE_NAME_TAGS.getBooleanValue()) {
            if (hideTag(src, kd)) {
                ci.cancel();
                return;
            }
        }
        // Coordinate filter: independent of the name tag filter master toggle.
        // The render state carries the interpolated absolute position of the
        // player (x/y/z), so the name tag is matched against the entry regions
        // at the player's current position.
        if (FilterEngine.hotkey_Behavior(FilterEngine.TYPE_NAME_TAGS, kd) == FilterEngine.HOTKEY_BEHAVIOR_NORMAL &&
                CoordinateFilter.isNameTagHidden(st.x, st.y, st.z, src)) {
            ci.cancel();
            return;
        }
        // No master-switch gate: the coordinate rules below are gated by the
        // coordinate replace toggle (global switch OFF), the global fallback
        // checks the master switch internally.
        {
            // The reveal hotkey skips the replacement (shows the original name).
            if (!ReplacementEngine.anyReplaceActive()
                    || FilterEngine.isReplaceBlocked(FilterEngine.TYPE_PLAYER_NAMES)) {
                return;
            }
            // Coordinate aware rules win over the global list, exactly like
            // the non-player name tag path in EntityNametagFilter.
            String rep = ReplacementEngine.getReplacementPlayerNameAt(src, st.x, st.y, st.z, FilterEngine.TYPE_PLAYER_NAMES);
            if (rep == null) {
                rep = ReplacementEngine.getReplacementPlayerName(src);
            }
            if (rep != null) {
                st.displayName = Text.literal(rep);
            }
        }
    }

    // PlayerEntityRenderState extends EntityRenderState, so the shared logic in
    // EntityNametagFilter applies unchanged: the displayName text is matched
    // against the name tag filter list.
    private static boolean hideTag(String name, boolean kd) {
        if (!RenderConfig.Toggles.DISABLE_NAME_TAGS.getBooleanValue() || name == null) {
            return false;
        }

        return FilterEngine.isNameTagHidden(name, kd);
    }
}