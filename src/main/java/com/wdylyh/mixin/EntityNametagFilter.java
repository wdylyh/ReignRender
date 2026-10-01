package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.CoordinateFilter;
import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.ReplacementEngine;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityNametagFilter {

    // Every non-player entity renders its name tag through this method: living
    // entity renderers do not override it, so they reach this base
    // implementation. Cancelling the call skips the whole label draw while the
    // toggle is on. Players override it and are handled by PlayerNametagFilter.
    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void label(EntityRenderState st, MatrixStack m,
                       OrderedRenderCommandQueue q, CameraRenderState cam,
                       CallbackInfo ci) {
        // The filter decides against the ORIGINAL name so it keeps matching the
        // entity's real identity. Only surviving tags get their text replaced.
        // The native GLFW key query (the most expensive step of the filter
        // chain) only runs when the name tag filter is actually on; the
        // replacement path needs no key state at all.
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
        // entity (x/y/z), so the name tag is matched against the entry regions
        // at the entity's current position.
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
            if (FilterEngine.isReplaceBlocked(FilterEngine.TYPE_NAME_TAGS)) {
                return;
            }
            // Coordinate aware rules win over the global list: a rule inside
            // the region the entity sits in (render state x/y/z) replaces its
            // label directly, anything else falls back to the global list.
            String rep = ReplacementEngine.getReplacementNameTagAt(src, st.x, st.y, st.z, FilterEngine.TYPE_NAME_TAGS);
            if (rep == null) {
                rep = ReplacementEngine.getReplacementNameTag(src);
            }
            if (rep != null) {
                st.displayName = Text.literal(rep);
            }
        }
    }

    // With the master toggle on, the name tag filter mode decides: OFF hides
    // every name tag, BLACKLIST hides the listed names, WHITELIST only shows
    // the listed names. Entities without a display name have nothing to draw,
    // so they never reach the filter.
    private static boolean hideTag(String name, boolean kd) {
        if (!RenderConfig.Toggles.DISABLE_NAME_TAGS.getBooleanValue() || name == null) {
            return false;
        }

        return FilterEngine.isNameTagHidden(name, kd);
    }
}