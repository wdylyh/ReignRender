package com.wdylyh.client.gui;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * In-game notification toasts styled after the vanilla advancement toast:
 * the {@code toast/advancement} background sprite stretched over the card, a
 * real item icon on the left, a white title and a golden description line.
 * Toasts slide in from the right edge with a linear movement (no easing),
 * exactly like vanilla achievements, stay for a few seconds, then slide back
 * out to the right. They are stacked below the top-right corner.
 */
public final class Toast
{
    private static final Identifier TTXT = Identifier.ofVanilla("toast/advancement");

    private static final int SI_MS = 600;
    private static final int SHOW = 4000;
    private static final int SO_MS = 600;
    private static final int MAX = 4;
    private static final int TH = 32;
    private static final int MIN_W = 160;
    private static final int MARGIN = 4;

    // Accent colors: green for added, amber for removed, red for errors.
    // They only select the title wording; the icon is always provided by the
    // caller (or absent when no matching item exists).
    public static final int CA = 0xFF66D9A8;
    public static final int CR = 0xFFF2C94C;
    public static final int CE = 0xFFFF6B6B;

    private static final Deque<T> Q = new ArrayDeque<>();

    private Toast() {}

    /**
     * Queues a toast, choosing the title automatically from the accent color.
     */
    public static void show(Text msg, int ac)
    {
        show(msg, ac, null);
    }

    /**
     * Queues a toast with an automatic title (chosen from the accent color) and
     * a real item icon. {@code null} or an empty stack means no icon is drawn.
     */
    public static void show(Text msg, int ac, ItemStack ic)
    {
        Text t = switch (ac) {
            case CA -> Text.translatable("reignrender.toast.added");
            case CR -> Text.translatable("reignrender.toast.removed");
            default -> Text.translatable("reignrender.toast.notice");
        };
        show(t, msg, ac, ic);
    }

    /**
     * Queues a fully custom toast: a white title line and a golden description
     * line. The newest toast is rendered on top.
     */
    public static void show(Text t, Text d, int ac)
    {
        show(t, d, ac, null);
    }

    /**
     * Queues a fully custom toast with a real item icon (a vanilla block/item,
     * e.g. the block being added to the filter). {@code null} or an empty
     * stack means no icon is drawn.
     */
    public static void show(Text t, Text d, int ac, ItemStack ic)
    {
        Q.addFirst(new T(t, d, ic, System.currentTimeMillis()));

        while (Q.size() > MAX)
        {
            Q.removeLast();
        }
    }

    /**
     * Renders all live toasts stacked below the top-right corner of the screen.
     * Called from the HUD render hook.
     */
    public static void render(DrawContext ctx)
    {
        long now = System.currentTimeMillis();
        Q.removeIf(x -> now - x.b >= SHOW + SO_MS);

        if (Q.isEmpty())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();

        int y = 4;

        for (T t : Q)
        {
            long age = now - t.b;
            boolean hasIc = t.ic != null && !t.ic.isEmpty();
            int tw = tr.getWidth(t.t);
            int dw = tr.getWidth(t.d);
            int width = Math.max(MIN_W, (hasIc ? 46 : 16) + Math.max(tw, dw));
            width = Math.min(width, sw - 8);
            int height = TH;

            int x = sx(age, width, sw);

            // Vanilla advancement-toast background sprite, stretched over the
            // card. It is dark translucent with a border, so no extra fills or
            // accents are drawn on top (vanilla draws none either).
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, TTXT, x, y, width, height);

            // Real item icon at (8, 8) like the vanilla advancement toast, but
            // only when the caller supplied a matching item; otherwise the text
            // moves to the left edge of the card.
            if (hasIc)
            {
                ctx.drawItem(t.ic, x + 8, y + 8);
            }

            int tx = x + (hasIc ? 30 : 8);

            // Title (white) and description (golden), no shadow like vanilla.
            ctx.drawText(tr, t.t, tx, y + 7, 0xFFFFFFFF, false);
            ctx.drawText(tr, t.d, tx, y + 18, 0xFFFFBB00, false);

            y += height + 6;
        }
    }

    /**
     * Linear right-to-left slide. At age 0 the card sits fully off-screen on
     * the right edge; it moves with a constant speed to its resting position
     * (4px from the right edge), stays there, then slides back out to the
     * right with the same linear movement.
     */
    private static int sx(long age, int width, int sw)
    {
        float t;

        if (age < SI_MS)
        {
            t = age / (float) SI_MS;
        }
        else
        {
            long rem = SHOW + SO_MS - age;
            t = rem < SO_MS ? Math.max(rem, 0) / (float) SO_MS : 1.0F;
        }

        return Math.round(sw - t * (width + MARGIN));
    }

    private record T(Text t, Text d, ItemStack ic, long b) {}
}