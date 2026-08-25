package com.wdylyh.mixin;

import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
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
public abstract class PER {

    // PlayerEntityRenderer overrides renderLabelIfPresent(), so the base class
    // injection in ER never fires for players. Intercept the
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
            String rep = Rpl.getReplacementPlayerName(src);
            if (rep != null) {
                st.displayName = Text.literal(rep);
            }
        }
    }

    // PlayerEntityRenderState extends EntityRenderState, so the shared logic in
    // ER applies unchanged: the displayName text is matched
    // against the name tag filter list.
    private static boolean hideTag(String name, boolean kd) {
        if (!Cfg.Off.DISABLE_NAME_TAGS.getBooleanValue() || name == null) {
            return false;
        }

        return FR.isNameTagHidden(name, kd);
    }
}