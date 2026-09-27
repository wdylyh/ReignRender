package com.wdylyh.client;

/**
 * Position context for the region item face modification ("区域面修改：物品").
 *
 * <p>The wrapped region item model ({@code FaceModItemModels.RegionShadowItemModel})
 * runs inside {@code ItemModel.update}, which has no access to the entity
 * carrying the item. The render-path mixins
 * ({@code ItemEntityReplace} for drops, {@code HeldItemReplace} and
 * {@code HeldItemFacePos} for held items) set the carrying entity's position
 * here around the state update / render call, so the wrapper can run the
 * region lookup. The value is set at HEAD and cleared at RETURN of the hooked
 * call, so it can never leak into a later call.</p>
 */
public final class RegionFaceItemPos {

    private static final ThreadLocal<double[]> POS = new ThreadLocal<>();

    private RegionFaceItemPos() {}

    public static void set(double x, double y, double z) {
        double[] p = new double[3];
        p[0] = x;
        p[1] = y;
        p[2] = z;
        POS.set(p);
    }

    public static void clear() {
        POS.remove();
    }

    /** The carrying entity's position, or null outside a hooked update / render call. */
    public static double[] pos() {
        return POS.get();
    }
}
