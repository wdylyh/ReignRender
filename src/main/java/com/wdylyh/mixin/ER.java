package com.wdylyh.mixin;

import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
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
public abstract class ER {

    // Every non-player entity renders its name tag through this method: living
    // entity renderers do not override it, so they reach this base
    // implementation. Cancelling the call skips the whole label draw while the
    // toggle is on. Players override it and are handled by PER.
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
        if (Cfg.Off.DISABLE_NAME_TAGS.getBooleanValue()) {
            boolean kd = FR.revealDown();
            if (hideTag(src, kd)) {
                ci.cancel();
                return;
            }
        }
        if (Rpl.isReplaceEnabled()) {
            // The reveal hotkey skips the replacement (shows the original name).
            if (FR.isReplaceBlocked(FR.TYPE_NAME_TAGS)) {
                return;
            }
            String rep = Rpl.getReplacementNameTag(src);
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
        if (!Cfg.Off.DISABLE_NAME_TAGS.getBooleanValue() || name == null) {
            return false;
        }

        return FR.isNameTagHidden(name, kd);
    }
}