package com.wdylyh;

/**
 * Holds the ThreadLocal flag that controls whether the sign block model
 * rendering should be skipped (while keeping the sign text visible).
 *
 * This is a plain class (not a Mixin) to avoid Mixin's restriction on
 * static members inside mixin classes.
 */
public final class SignRenderStateHolder {

    private static final ThreadLocal<Boolean> SKIP_SIGN_MODEL = ThreadLocal.withInitial(() -> false);

    private SignRenderStateHolder() {
    }

    public static void setSkipSignModel(boolean skip) {
        SKIP_SIGN_MODEL.set(skip);
    }

    public static boolean shouldSkipSignModel() {
        return SKIP_SIGN_MODEL.get();
    }
}
