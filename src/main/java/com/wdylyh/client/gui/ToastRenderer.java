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
public final class ToastRenderer
{
    private static final Identifier TOAST_TEXTURE = Identifier.ofVanilla("toast/advancement");

    private static final int SLIDE_IN_MS = 600;
    private static final int SHOW = 4000;
    private static final int SLIDE_OUT_MS = 600;
    private static final int MAX = 4;
    private static final int TOAST_HEIGHT = 32;
    private static final int MIN_W = 160;
    private static final int MARGIN = 4;

    // Accent colors: green for added, amber for removed, red for errors.
    // They only select the title wording; the icon is always provided by the
    // caller (or absent when no matching item exists).
    public static final int TOAST_COLOR_ADDED = 0xFF66D9A8;
    public static final int TOAST_COLOR_REMOVED = 0xFFF2C94C;
    public static final int TOAST_COLOR_ERROR = 0xFFFF6B6B;

    private static final Deque<Toast> TOAST_QUEUE = new ArrayDeque<>();

    private ToastRenderer() {}

    /**
     * Queues a toast, choosing the title automatically from the accent color.
     */
    public static void show(Text msg, int accentColor)
    {
        show(msg, accentColor, null);
    }

    /**
     * Queues a toast with an automatic title (chosen from the accent color) and
     * a real item icon. {@code null} or an empty stack means no icon is drawn.
     */
    public static void show(Text msg, int accentColor, ItemStack icon)
    {
        Text text = switch (accentColor) {
            case TOAST_COLOR_ADDED -> Text.translatable("reignrender.toast.added");
            case TOAST_COLOR_REMOVED -> Text.translatable("reignrender.toast.removed");
            default -> Text.translatable("reignrender.toast.notice");
        };
        show(text, msg, accentColor, icon);
    }

    /**
     * Queues a fully custom toast: a white title line and a golden description
     * line. The newest toast is rendered on top.
     */
    public static void show(Text text, Text description, int accentColor)
    {
        show(text, description, accentColor, null);
    }

    /**
     * Queues a fully custom toast with a real item icon (a vanilla block/item,
     * e.g. the block being added to the filter). {@code null} or an empty
     * stack means no icon is drawn.
     */
    public static void show(Text text, Text description, int accentColor, ItemStack icon)
    {
        TOAST_QUEUE.addFirst(new Toast(text, description, icon, System.currentTimeMillis()));

        while (TOAST_QUEUE.size() > MAX)
        {
            TOAST_QUEUE.removeLast();
        }
    }

    /**
     * Renders all live toasts stacked below the top-right corner of the screen.
     * Called from the HUD render hook.
     */
    public static void render(DrawContext ctx)
    {
        long now = System.currentTimeMillis();
        TOAST_QUEUE.removeIf(x -> now - x.born() >= SHOW + SLIDE_OUT_MS);

        if (TOAST_QUEUE.isEmpty())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();

        int y = 4;

        for (Toast toast : TOAST_QUEUE)
        {
            long age = now - toast.born();
            boolean hasIc = toast.icon() != null && !toast.icon().isEmpty();
            int tw = tr.getWidth(toast.text());
            int dw = tr.getWidth(toast.description());
            int width = Math.max(MIN_W, (hasIc ? 46 : 16) + Math.max(tw, dw));
            width = Math.min(width, sw - 8);
            int height = TOAST_HEIGHT;

            int x = sx(age, width, sw);

            // Vanilla advancement-toast background sprite, stretched over the
            // card. It is dark translucent with a border, so no extra fills or
            // accents are drawn on top (vanilla draws none either).
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, TOAST_TEXTURE, x, y, width, height);

            // Real item icon at (8, 8) like the vanilla advancement toast, but
            // only when the caller supplied a matching item; otherwise the text
            // moves to the left edge of the card.
            if (hasIc)
            {
                ctx.drawItem(toast.icon(), x + 8, y + 8);
            }

            int tx = x + (hasIc ? 30 : 8);

            // Title (white) and description (golden), no shadow like vanilla.
            ctx.drawText(tr, toast.text(), tx, y + 7, 0xFFFFFFFF, false);
            ctx.drawText(tr, toast.description(), tx, y + 18, 0xFFFFBB00, false);

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

        if (age < SLIDE_IN_MS)
        {
            t = age / (float) SLIDE_IN_MS;
        }
        else
        {
            long rem = SHOW + SLIDE_OUT_MS - age;
            t = rem < SLIDE_OUT_MS ? Math.max(rem, 0) / (float) SLIDE_OUT_MS : 1.0F;
        }

        return Math.round(sw - t * (width + MARGIN));
    }

    private record Toast(Text text, Text description, ItemStack icon, long born) {}
}