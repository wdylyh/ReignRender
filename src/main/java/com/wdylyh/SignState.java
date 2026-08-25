package com.wdylyh;

/**
 * Holds the ThreadLocal flag that controls whether the sign block model
 * rendering should be skipped (while keeping the sign text visible).
 *
 * This is a plain class (not a Mixin) to avoid Mixin's restriction on
 * static members inside mixin classes.
 */
public final class SignState {

    // A single-cell boolean[] instead of Boolean avoids the boxing/unboxing
    // that ThreadLocal<Boolean> would incur on the hot sign render path.
    private static final ThreadLocal<boolean[]> SKIP = ThreadLocal.withInitial(() -> new boolean[1]);

    private SignState() {
    }

    public static void setSkip(boolean skip) {
        SKIP.get()[0] = skip;
    }

    public static boolean skip() {
        return SKIP.get()[0];
    }
}