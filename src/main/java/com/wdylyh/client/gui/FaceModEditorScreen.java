package com.wdylyh.client.gui;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

import com.wdylyh.config.FaceModIndex;
import com.wdylyh.config.FaceModPacks;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Pixel editor for one texture of a face-mod id. The canvas starts as the
 * current look of the texture (the edited pack png when one exists, the
 * original resource pack texture otherwise) and can be painted with the pen /
 * eraser / picker / flood fill tools, seeded from an imported png, and rolled
 * back with undo / redo. Saving writes the png into the generated face-mod
 * resource pack and reloads the resources, which makes every render of this
 * texture use the edited image.
 */
public class FaceModEditorScreen extends GuiBase
{
    /** The preview texture id, registered while this screen is open. */
    private static final Identifier TEX_ID = Identifier.of("reignrender", "face_editor_preview");

    /** Fixed palette colors (ARGB); the first entry is fully transparent. */
    private static final int[] PALETTE = {
            0x00000000,
            0xFFFFFFFF, 0xFF000000, 0xFF808080, 0xFFC0C0C0,
            0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00,
            0xFF00FFFF, 0xFFFF00FF, 0xFF804000, 0xFFC08040,
            0xFF408040, 0xFF404080, 0xFF804060, 0xFF3060C0
    };

    private static final int MAX_UNDO = 50;

    private enum Tool { PEN, ERASE, PICKER, FILL }

    /** Which color-picker widget a drag is currently controlling. */
    private enum DragMode { NONE, SV, HUE }

    private final FaceModListScreen.FaceKind kind;
    private final String id;
    private final String texturePath;

    /** The path the edit is stored under (the shadow path for the shadow categories, texturePath otherwise). */
    private final String savePath;

    private NativeImage img;
    private NativeImageBackedTexture tex;

    private Tool tool = Tool.PEN;
    private int color = 0xFF000000;
    private boolean drawing;
    private int lastX = -1;
    private int lastY = -1;
    private String error;

    // HSV state of the color picker (the hue/saturation/value of this.color)
    private float hue;
    private float sat;
    private float val;

    private final Deque<int[]> undo = new ArrayDeque<>();
    private final Deque<int[]> redo = new ArrayDeque<>();

    // Canvas display geometry, computed in initGui
    private int cx;
    private int cy;
    private int dispW;
    private int dispH;
    private int scale;

    // Top-left of the s/v box + hue bar column (set in rebuild, read by the
    // screen-level click / drag handling)
    private int pickX;
    private int pickY;

    /** Which picker widget the current press started on; NONE when idle. */
    private DragMode dragMode = DragMode.NONE;

    /** True once the user zoomed manually; the auto fit then no longer resets the scale. */
    private boolean scaleSet;

    private GuiTextFieldGeneric importField;

    public FaceModEditorScreen(FaceModListScreen.FaceKind kind, String id, String texturePath)
    {
        this.kind = kind;
        this.id = id;
        this.texturePath = texturePath;
        this.savePath = kind.shadowSavePath(texturePath) != null
                ? kind.shadowSavePath(texturePath) : texturePath;

        // Seed the HSV picker state from the default pen color (black)
        this.sat = 0.0F;
        this.val = 0.0F;
        this.hue = 0.0F;
    }

    @Override
    public void initGui()
    {
        super.initGui();

        this.setParent(new FaceModTextureScreen(this.kind, this.id));
        this.setTitle(StringUtils.translate("reignrender.gui.face.editor.title", this.texturePath));

        if (this.img == null)
        {
            this.load();
        }

        // The canvas image is the texture's own image: painting mutates it in
        // place and upload() pushes it to the GPU. Recreate both only when
        // entering fresh (after removed() nulled them); a plain initGui re-run
        // (window resize) must keep the existing registration, since
        // registerTexture would close the old texture and with it the canvas.
        if (this.tex == null)
        {
            this.tex = new NativeImageBackedTexture(() -> "reignrender_face_edit", this.img);
            this.tex.upload();
            MinecraftClient.getInstance().getTextureManager().registerTexture(TEX_ID, this.tex);
        }

        this.layout();
        this.rebuild();
    }

    /** Computes the centered, integer scaled canvas display rectangle. */
    private void layout()
    {
        if (!this.scaleSet)
        {
            int availW = Math.max(this.img.getWidth(), this.width - 20 - 170);
            int availH = Math.max(this.img.getHeight(), this.height - ReplaceListScreen.LTOP - 20);

            this.scale = Math.min(availW / this.img.getWidth(), availH / this.img.getHeight());
            this.scale = Math.max(1, Math.min(this.scale, 32));
        }

        this.dispW = this.img.getWidth() * this.scale;
        this.dispH = this.img.getHeight() * this.scale;
        this.cx = 20;
        this.cy = ReplaceListScreen.LTOP + 10;
    }

    /**
     * Wheel zoom of the canvas, anchored at the mouse position (the pixel
     * under the cursor stays under the cursor). A manual zoom is kept across
     * rebuilds / window resizes.
     */
    @Override
    public boolean onMouseScrolled(double mx, double my, double ha, double va)
    {
        if (va != 0 && mx >= this.cx - 2 && mx <= this.cx + this.dispW + 2
                && my >= this.cy - 2 && my <= this.cy + this.dispH + 2)
        {
            int px = ((int) mx - this.cx) / this.scale;
            int py = ((int) my - this.cy) / this.scale;

            this.scale = Math.max(1, Math.min(32, this.scale + (va > 0 ? 1 : -1)));
            this.scaleSet = true;
            this.dispW = this.img.getWidth() * this.scale;
            this.dispH = this.img.getHeight() * this.scale;
            this.cx = (int) mx - px * this.scale;
            this.cy = (int) my - py * this.scale;

            this.rebuild();
            return true;
        }

        return super.onMouseScrolled(mx, my, ha, va);
    }

    /**
     * Screen-level press handling for the color pickers. Recording the drag
     * mode here (instead of relying on the widgets' own drag forwarding) is
     * what lets the s/v box and the hue bar keep tracking the cursor once it
     * leaves the widget rectangle.
     */
    @Override
    public boolean onMouseClicked(Click click, boolean doubleClick)
    {
        if (click.getKeycode() == 0)
        {
            int mx = (int) click.x();
            int my = (int) click.y();
            int px0 = this.pickX;
            int py0 = this.pickY;

            // Hue bar (right of the s/v box)
            if (mx >= px0 + 70 && mx < px0 + 84 && my >= py0 && my < py0 + 66)
            {
                this.dragMode = DragMode.HUE;
                this.applyHue(my);
                return true;
            }

            // Saturation/value box
            if (mx >= px0 && mx < px0 + 66 && my >= py0 && my < py0 + 66)
            {
                this.dragMode = DragMode.SV;
                this.applySv(mx, my);
                return true;
            }
        }

        return super.onMouseClicked(click, doubleClick);
    }

    /**
     * Screen-level drag handling. Painting and the color pickers are both
     * driven from drawContents(...) using the live mouse position, because
     * malilib only forwards drag events while the cursor stays inside the
     * widget that received the press.
     */
    @Override
    public boolean onMouseDragged(Click click, double dragXAmount, double dragYAmount)
    {
        return super.onMouseDragged(click, dragXAmount, dragYAmount);
    }

    /**
     * Advances an in-progress canvas stroke and any active picker drag from
     * the live mouse position. Runs every frame, so it keeps working even when
     * the cursor leaves the widget that received the press.
     */
    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        // Canvas stroke
        if (this.drawing && (this.tool == Tool.PEN || this.tool == Tool.ERASE))
        {
            int px = this.clampX((mouseX - this.cx) / this.scale);
            int py = this.clampY((mouseY - this.cy) / this.scale);

            if (px != this.lastX || py != this.lastY)
            {
                this.drawLine(this.lastX, this.lastY, px, py);
                this.lastX = px;
                this.lastY = py;
                this.tex.upload();
            }
        }

        // Color picker drag
        if (this.dragMode == DragMode.HUE)
        {
            this.applyHue(mouseY);
        }
        else if (this.dragMode == DragMode.SV)
        {
            this.applySv(mouseX, mouseY);
        }
    }

    /** Screen-level release: always end the stroke / picker drag. */
    @Override
    public boolean onMouseReleased(Click click)
    {
        this.drawing = false;
        this.dragMode = DragMode.NONE;
        return super.onMouseReleased(click);
    }

    private int clampX(int px)
    {
        return Math.max(0, Math.min(this.img.getWidth() - 1, px));
    }

    private int clampY(int py)
    {
        return Math.max(0, Math.min(this.img.getHeight() - 1, py));
    }

    /** Loads the canvas image: the edited pack png when present, the original resource otherwise. */
    private void load()
    {
        this.img = readTexture();
    }

    /**
     * Reads the edited png from the face-mod resource pack when present, the
     * original resource pack texture otherwise; 16x16 fallback.
     */
    private NativeImage readTexture()
    {
        // Region categories keep their edits in the region face pack.
        NativeImage mod = this.kind.isRegion()
                ? com.wdylyh.config.RegionFacePacks.readModified(this.savePath)
                : FaceModPacks.readModified(this.savePath);

        if (mod != null)
        {
            return mod;
        }

        // Region categories with no edit of their own seed the canvas from the
        // GLOBAL face-mod edit of the same vanilla texture (drawn there first,
        // then carried over by a single save here — the two edit stores are
        // independent, so this is the only bridge between them).
        if (this.kind.isRegion())
        {
            String globalKey = globalIndexKey(this.kind);

            if (globalKey != null
                    && FaceModIndex.overriddenPaths(globalKey, this.id).contains(this.texturePath)
                    && (mod = FaceModPacks.readModified(this.texturePath)) != null)
            {
                return mod;
            }
        }

        try (InputStream in = MinecraftClient.getInstance().getResourceManager()
                .getResourceOrThrow(Identifier.of(this.texturePath)).getInputStream())
        {
            return NativeImage.read(in);
        }
        catch (Exception e)
        {
            FaceModPacks.LOGGER.warn("[ReignRender] failed to load texture {} for the face editor",
                    this.texturePath, e);
            return new NativeImage(16, 16, false);
        }
    }

    /**
     * The global {@link FaceModIndex} category key of a region kind, or null.
     * The keys match except for the items (region ITEMS vs global ITEM_ENTITIES).
     */
    private static String globalIndexKey(FaceModListScreen.FaceKind kind)
    {
        return switch (kind)
        {
            case R_BLOCKS -> "BLOCKS";
            case R_ENTITIES -> "ENTITIES";
            case R_PARTICLES -> "PARTICLES";
            case R_ITEMS -> "ITEM_ENTITIES";
            default -> null;
        };
    }

    /** Nearest neighbor copy of src onto the whole dst canvas. */
    private static void copyNearest(NativeImage src, NativeImage dst)
    {
        int w = dst.getWidth();
        int h = dst.getHeight();

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                dst.setColorArgb(x, y, src.getColorArgb(x * src.getWidth() / w, y * src.getHeight() / h));
            }
        }
    }

    @Override
    public void removed()
    {
        super.removed();

        // destroyTexture closes the texture and its canvas image, so null both
        // out: re-entering reloads (the edited png, if any) from scratch.
        MinecraftClient.getInstance().getTextureManager().destroyTexture(TEX_ID);
        this.tex = null;
        this.img = null;
    }

    private void rebuild()
    {
        // Keep the import text field alive: clearElements() also clears
        // textFields, which would wipe the box added in initGui
        this.clearChildren();
        this.clearButtons();

        // ---- Tool buttons; the active tool's button is disabled as the marker ----
        int bx = 20;
        bx += this.addTool(bx, Tool.PEN, "reignrender.gui.face.tool.pen") + 2;
        bx += this.addTool(bx, Tool.ERASE, "reignrender.gui.face.tool.erase") + 2;
        bx += this.addTool(bx, Tool.PICKER, "reignrender.gui.face.tool.picker") + 2;
        bx += this.addTool(bx, Tool.FILL, "reignrender.gui.face.tool.fill") + 2;

        bx += 8;
        ButtonGeneric ub = new ButtonGeneric(bx, 26, 42, 20, StringUtils.translate("reignrender.gui.face.undo"));
        ub.setEnabled(!this.undo.isEmpty());
        this.addButton(ub, (b, m) -> this.doUndo());
        bx += 44;
        ButtonGeneric rb = new ButtonGeneric(bx, 26, 42, 20, StringUtils.translate("reignrender.gui.face.redo"));
        rb.setEnabled(!this.redo.isEmpty());
        this.addButton(rb, (b, m) -> this.doRedo());

        // ---- Save / reset / import row ----
        this.addButton(new ButtonGeneric(20, 48, 60, 20, StringUtils.translate("reignrender.gui.face.save")),
                (b, m) -> this.save());
        this.addButton(new ButtonGeneric(84, 48, 60, 20, StringUtils.translate("reignrender.gui.face.reset")),
                (b, m) -> this.resetOriginal());

        this.importField = new GuiTextFieldGeneric(154, 50, Math.min(220, this.width - 154 - 190), 16, this.textRenderer);
        this.importField.setPlaceholder(Text.translatable("reignrender.gui.face.import.path"));
        this.addTextField(this.importField, f -> false);

        int ifw = Math.min(220, this.width - 154 - 190);

        this.addButton(new ButtonGeneric(154 + ifw + 4, 48, 50, 20,
                StringUtils.translate("reignrender.gui.face.import")), (b, m) -> this.importPng());
        this.addButton(new ButtonGeneric(154 + ifw + 58, 48, 50, 20,
                StringUtils.translate("reignrender.gui.face.import.browse")), (b, m) -> this.openFileDialog());

        // ---- Cancel (back to the texture list); narrow, on the far right ----
        this.addButton(new ButtonGeneric(this.width - 10, 26, 60, true, "reignrender.gui.filter.cancel"),
                (b, m) -> GuiBase.openGui(new FaceModTextureScreen(this.kind, this.id)));

        if (this.error != null)
        {
            this.addLabel(20, this.height - 14, this.width - 40, 10, 0xFFFF5050, this.error);
        }

        // ---- Canvas ----
        this.addWidget(new Canvas());

        // ---- Color picker (right column): saturation/value box + hue bar ----
        int px0 = this.width - 146;
        int py0 = ReplaceListScreen.LTOP + 10;

        this.pickX = px0;
        this.pickY = py0;

        this.addWidget(new SvBox(px0, py0, 66, 66));
        this.addWidget(new HueBar(px0 + 70, py0, 14, 66));

        // Current color preview
        this.addWidget(new CurPreview(px0 + 70, py0 + 70, 14, 14));

        // ---- Fixed palette (3 columns, below the picker) ----
        int sy0 = py0 + 92;

        for (int i = 0; i < PALETTE.length; i++)
        {
            this.addWidget(new Swatch(i, px0 + (i % 3) * 20, sy0 + (i / 3) * 20, 18));
        }
    }

    private int addTool(int x, Tool t, String key)
    {
        ButtonGeneric b = new ButtonGeneric(x, 26, 46, 20, StringUtils.translate(key));
        b.setEnabled(this.tool != t);
        this.addButton(b, (btn, m) ->
        {
            this.tool = t;
            this.rebuild();
        });

        return 46;
    }

    // ==================== Undo / redo ====================

    private int[] snapshot()
    {
        int[] px = new int[this.img.getWidth() * this.img.getHeight()];

        for (int y = 0; y < this.img.getHeight(); y++)
        {
            for (int x = 0; x < this.img.getWidth(); x++)
            {
                px[y * this.img.getWidth() + x] = this.img.getColorArgb(x, y);
            }
        }

        return px;
    }

    private void restore(int[] px)
    {
        for (int y = 0; y < this.img.getHeight(); y++)
        {
            for (int x = 0; x < this.img.getWidth(); x++)
            {
                this.img.setColorArgb(x, y, px[y * this.img.getWidth() + x]);
            }
        }

        this.tex.upload();
    }

    private void pushUndo()
    {
        this.undo.addLast(this.snapshot());

        if (this.undo.size() > MAX_UNDO)
        {
            this.undo.removeFirst();
        }

        this.redo.clear();
    }

    private void doUndo()
    {
        if (!this.undo.isEmpty())
        {
            this.redo.addLast(this.snapshot());
            this.restore(this.undo.removeLast());
            this.rebuild();
        }
    }

    private void doRedo()
    {
        if (!this.redo.isEmpty())
        {
            this.undo.addLast(this.snapshot());
            this.restore(this.redo.removeLast());
            this.rebuild();
        }
    }

    // ==================== Save / reset / import ====================

    /**
     * Writes the edited texture into the face-mod resource pack (which is
     * enabled and the resources reloaded on the way), so every render of this
     * texture picks the edited image immediately.
     */
    private void save()
    {
        if (this.kind.isRegion())
        {
            // Region categories: the save path lives in the region face pack;
            // saveTexture also regenerates the shadow resources (e.g. the item
            // model of a newly listed id) before the reload.
            com.wdylyh.config.RegionFaceIndex.addOverride(this.kind.indexKey(), this.id, this.savePath);
            com.wdylyh.config.RegionFacePacks.saveTexture(this.savePath, this.img);
        }
        else
        {
            // saveTexture regenerates the held / dropped shadow resources
            // before the reload (covers newly listed ids and the isolation
            // shadows needed when a BLOCK texture was edited).
            FaceModIndex.addOverride(this.kind.indexKey(), this.id, this.savePath);
            FaceModPacks.saveTexture(this.savePath, this.img);
        }

        // Name the destination pack in the confirmation: a save through the
        // wrong category (region vs global) is instantly recognizable.
        this.error = StringUtils.translate(this.kind.isRegion()
                ? "reignrender.gui.face.saved.region" : "reignrender.gui.face.saved.global");
        this.rebuild();
    }

    /** Removes the edit and reloads the original resource pack texture. */
    private void resetOriginal()
    {
        if (this.kind.isRegion())
        {
            com.wdylyh.config.RegionFaceIndex.removeOverride(this.kind.indexKey(), this.id, this.savePath);
            com.wdylyh.config.RegionFacePacks.removeTextures(java.util.List.of(this.savePath));
        }
        else
        {
            FaceModIndex.removeOverride(this.kind.indexKey(), this.id, this.savePath);
            FaceModPacks.removeTextures(java.util.List.of(this.savePath));
        }

        this.undo.clear();
        this.redo.clear();

        // The texture owns the canvas image, so the reloaded original is
        // copied into it in place (never swapped out).
        NativeImage fresh = readTexture();
        copyNearest(fresh, this.img);
        fresh.close();
        this.tex.upload();
        this.rebuild();
    }

    /** Imports a png from the typed file path, nearest neighbor scaled onto the canvas. */
    private void importPng()
    {
        String p = this.importField.getValueWrapper().trim();

        if (p.isEmpty())
        {
            return;
        }

        try (FileInputStream in = new FileInputStream(p))
        {
            this.applyImport(in, null);
        }
        catch (Exception e)
        {
            this.error = StringUtils.translate("reignrender.gui.face.import.fail", e.getMessage());
        }

        this.rebuild();
    }

    /** Applies a png from the stream to the canvas (undo push + upload). */
    private void applyImport(InputStream in, String pickedPath) throws Exception
    {
        NativeImage src = NativeImage.read(in);
        this.pushUndo();
        copyNearest(src, this.img);
        src.close();
        this.tex.upload();
        this.error = null;

        if (pickedPath != null)
        {
            this.importField.setTextWrapper(pickedPath);
        }
    }

    /**
     * Opens the operating system's file open dialog (like a website upload)
     * and imports the picked png.
     *
     * The dialog blocks, so it runs on its own thread; the AWT dialog itself
     * is created on the AWT event thread (required on most platforms, and the
     * reason the previous version threw HeadlessException was that Minecraft
     * sets java.awt.headless=true by default, which must be turned off in the
     * mod's client initializer). The canvas update is posted back to the
     * client thread since the texture upload needs the GL context.
     */

    /** True when the file name has one of the image extensions NativeImage can read. */
    private static boolean isSupportedImage(java.io.File f)
    {
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".png")
                || n.endsWith(".jpg")
                || n.endsWith(".jpeg")
                || n.endsWith(".bmp")
                || n.endsWith(".gif")
                || n.endsWith(".tga");
    }

    private void openFileDialog()
    {
        FaceModPacks.LOGGER.info("[ReignRender] browse button clicked");

        Thread t = new Thread(() ->
        {
            try
            {
                FaceModPacks.LOGGER.info("[ReignRender] file dialog thread started");

                final java.io.File[] holder = new java.io.File[1];

                java.awt.EventQueue.invokeAndWait(() ->
                {
                    FaceModPacks.LOGGER.info("[ReignRender] creating FileDialog on EDT");

                    // NO setFilenameFilter here: the Windows AWT FileDialog is
                    // broken with one set (no files selectable at all). Pick
                    // freely and validate the extension afterwards.
                    java.awt.FileDialog fd = new java.awt.FileDialog((java.awt.Frame) null,
                            StringUtils.translate("reignrender.gui.face.import.browse"),
                            java.awt.FileDialog.LOAD);
                    fd.setVisible(true);

                    FaceModPacks.LOGGER.info("[ReignRender] FileDialog closed");

                    java.io.File[] picked = fd.getFiles();

                    if (picked != null && picked.length > 0)
                    {
                        holder[0] = picked[0];
                    }
                    else if (fd.getFile() != null)
                    {
                        holder[0] = new java.io.File(fd.getDirectory(), fd.getFile());
                    }
                });

                FaceModPacks.LOGGER.info("[ReignRender] picked file: {}", holder[0]);

                final java.io.File sel = holder[0];

                if (sel == null)
                {
                    return;
                }

                if (!isSupportedImage(sel))
                {
                    MinecraftClient.getInstance().execute(() ->
                            this.error = StringUtils.translate("reignrender.gui.face.import.fail", sel.getName()));
                    return;
                }

                FileInputStream in = new FileInputStream(sel);
                MinecraftClient.getInstance().execute(() ->
                {
                    // The dialog thread outlives the screen: removed() has
                    // nulled the canvas when the user closed the editor while
                    // the dialog was open — drop the import instead of NPEing.
                    if (this.img == null || this.tex == null)
                    {
                        return;
                    }

                    try (in)
                    {
                        this.applyImport(in, sel.getAbsolutePath());
                    }
                    catch (Exception e)
                    {
                        this.error = StringUtils.translate("reignrender.gui.face.import.fail", e.getMessage());
                    }

                    this.rebuild();
                });
            }
            catch (Throwable e)
            {
                FaceModPacks.LOGGER.warn("[ReignRender] file dialog failed", e);
                MinecraftClient.getInstance().execute(() ->
                {
                    this.error = StringUtils.translate("reignrender.gui.face.import.fail", e.getMessage());
                    this.rebuild();
                });
            }
        }, "reignrender-face-import-dialog");

        t.setDaemon(true);
        t.start();
    }

    // ==================== Painting ====================

    private int toolColor()
    {
        return this.tool == Tool.ERASE ? 0x00000000 : this.color;
    }

    private void putPixel(int x, int y)
    {
        if (x < 0 || y < 0 || x >= this.img.getWidth() || y >= this.img.getHeight())
        {
            return;
        }

        this.img.setColorArgb(x, y, this.toolColor());
    }

    /** Bresenham line so fast drags leave a connected stroke. */
    private void drawLine(int x0, int y0, int x1, int y1)
    {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        while (true)
        {
            this.putPixel(x0, y0);

            if (x0 == x1 && y0 == y1)
            {
                break;
            }

            int e2 = 2 * err;

            if (e2 >= dy)
            {
                err += dy;
                x0 += sx;
            }

            if (e2 <= dx)
            {
                err += dx;
                y0 += sy;
            }
        }
    }

    private void floodFill(int sx, int sy)
    {
        if (sx < 0 || sy < 0 || sx >= this.img.getWidth() || sy >= this.img.getHeight())
        {
            return;
        }

        int target = this.img.getColorArgb(sx, sy);
        int repl = this.toolColor();

        if (target == repl)
        {
            return;
        }

        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[] { sx, sy });

        while (!stack.isEmpty())
        {
            int[] p = stack.pop();
            int x = p[0];
            int y = p[1];

            if (x < 0 || y < 0 || x >= this.img.getWidth() || y >= this.img.getHeight()
                    || this.img.getColorArgb(x, y) != target)
            {
                continue;
            }

            this.img.setColorArgb(x, y, repl);
            stack.push(new int[] { x + 1, y });
            stack.push(new int[] { x - 1, y });
            stack.push(new int[] { x, y + 1 });
            stack.push(new int[] { x, y - 1 });
        }
    }

    // ==================== Color picking ====================

    /** Applies a hue-bar y coordinate to the current color. */
    private void applyHue(int my)
    {
        int py0 = this.pickY;
        this.hue = Math.max(0.0F, Math.min(0.999F, (my - py0) / 66.0F));
        this.color = hsvToRgb(this.hue, this.sat, this.val);
        this.tool = Tool.PEN;
    }

    /** Applies an s/v-box coordinate to the current color. */
    private void applySv(int mx, int my)
    {
        int px0 = this.pickX;
        int py0 = this.pickY;

        this.sat = Math.max(0.0F, Math.min(1.0F, (mx - px0) / 65.0F));
        this.val = Math.max(0.0F, Math.min(1.0F, 1.0F - (my - py0) / 65.0F));
        this.color = hsvToRgb(this.hue, this.sat, this.val);
        this.tool = Tool.PEN;
    }

    /** Selects a color and syncs the HSV picker state to it. */
    private void pickColor(int argb)
    {
        this.color = argb | 0xFF000000;
        syncHsv(this.color);
        this.tool = Tool.PEN;
        this.rebuild();
    }

    /** Recomputes the picker's hue/sat/val from an rgb color. */
    private void syncHsv(int rgb)
    {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;

        this.val = max;
        this.sat = max == 0 ? 0 : d / max;

        if (d == 0)
        {
            // Achromatic: keep the current hue
            return;
        }

        if (max == r)
        {
            this.hue = ((g - b) / d) % 6.0F;
        }
        else if (max == g)
        {
            this.hue = (b - r) / d + 2.0F;
        }
        else
        {
            this.hue = (r - g) / d + 4.0F;
        }

        this.hue /= 6.0F;

        if (this.hue < 0)
        {
            this.hue += 1.0F;
        }
    }

    /** hsv(0..1) to a packed opaque ARGB color. */
    private static int hsvToRgb(float h, float s, float v)
    {
        h = (h % 1.0F + 1.0F) % 1.0F;
        int i = (int) (h * 6.0F);
        float f = h * 6.0F - i;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);

        float r, g, b;

        switch (i % 6)
        {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }

        return 0xFF000000
                | ((int) (r * 255.0F + 0.5F) << 16)
                | ((int) (g * 255.0F + 0.5F) << 8)
                | (int) (b * 255.0F + 0.5F);
    }

    // ==================== Widgets ====================

    private class Canvas extends WidgetBase
    {
        Canvas()
        {
            super(FaceModEditorScreen.this.cx, FaceModEditorScreen.this.cy,
                    FaceModEditorScreen.this.dispW, FaceModEditorScreen.this.dispH);
        }

        @Override
        public void render(GuiContext ctx, int mx, int my, boolean selected)
        {
            super.render(ctx, mx, my, selected);

            // Dark backdrop so transparent pixels read as transparent
            RenderUtils.drawRect(ctx, this.x - 2, this.y - 2, this.width + 4, this.height + 4, 0xFF0A0A0A);

            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, TEX_ID, this.x, this.y,
                    0.0F, 0.0F, this.width, this.height, this.width, this.height);

            // Grid on sufficiently zoomed canvases
            if (FaceModEditorScreen.this.scale >= 6)
            {
                for (int gx = 0; gx <= FaceModEditorScreen.this.img.getWidth(); gx++)
                {
                    RenderUtils.drawRect(ctx, this.x + gx * FaceModEditorScreen.this.scale - (gx > 0 ? 1 : 0),
                            this.y, gx > 0 ? 1 : 0, this.height, 0x28FFFFFF);
                }

                for (int gy = 0; gy <= FaceModEditorScreen.this.img.getHeight(); gy++)
                {
                    RenderUtils.drawRect(ctx, this.x,
                            this.y + gy * FaceModEditorScreen.this.scale - (gy > 0 ? 1 : 0),
                            this.width, gy > 0 ? 1 : 0, 0x28FFFFFF);
                }
            }

            // Hovered pixel highlight
            int hx = (mx - this.x) / FaceModEditorScreen.this.scale;
            int hy = (my - this.y) / FaceModEditorScreen.this.scale;

            if (hx >= 0 && hy >= 0 && hx < FaceModEditorScreen.this.img.getWidth()
                    && hy < FaceModEditorScreen.this.img.getHeight())
            {
                RenderUtils.drawOutlinedBox(ctx,
                        this.x + hx * FaceModEditorScreen.this.scale,
                        this.y + hy * FaceModEditorScreen.this.scale,
                        FaceModEditorScreen.this.scale, FaceModEditorScreen.this.scale,
                        0, 0xFFFF00FF);
            }
        }

        @Override
        protected boolean onMouseClickedImpl(Click click, boolean doubleClick)
        {
            if (click.getKeycode() != 0)
            {
                return false;
            }

            int px = ((int) click.x() - this.x) / FaceModEditorScreen.this.scale;
            int py = ((int) click.y() - this.y) / FaceModEditorScreen.this.scale;

            if (px < 0 || py < 0 || px >= FaceModEditorScreen.this.img.getWidth()
                    || py >= FaceModEditorScreen.this.img.getHeight())
            {
                return false;
            }

            FaceModEditorScreen ed = FaceModEditorScreen.this;

            if (ed.tool == Tool.PICKER)
            {
                ed.pickColor(ed.img.getColorArgb(px, py));
                return true;
            }

            ed.pushUndo();

            if (ed.tool == Tool.FILL)
            {
                ed.floodFill(px, py);
                ed.tex.upload();
                return true;
            }

            // PEN / ERASE: paint the press pixel here; the rest of the stroke
            // is advanced every frame in FaceModEditorScreen.drawContents from
            // the live mouse position, so leaving the widget no longer breaks it.
            ed.drawLine(px, py, px, py);
            ed.lastX = px;
            ed.lastY = py;
            ed.drawing = true;
            ed.tex.upload();
            return true;
        }

        @Override
        public boolean onMouseDraggedImpl(Click click, double dragXAmount, double dragYAmount)
        {
            // Intentionally empty: malilib stops forwarding drag events as
            // soon as the live cursor leaves this widget, which would cut the
            // stroke. FaceModEditorScreen.drawContents drives the stroke instead.
            return false;
        }

        @Override
        public void onMouseReleasedImpl(Click click)
        {
            FaceModEditorScreen.this.drawing = false;
        }
    }

    private class Swatch extends WidgetBase
    {
        private final int argb;

        Swatch(int index, int x, int y, int size)
        {
            super(x, y, size, size);
            this.argb = PALETTE[index];
        }

        @Override
        public void render(GuiContext ctx, int mx, int my, boolean selected)
        {
            super.render(ctx, mx, my, selected);

            boolean hov = this.isMouseOver(mx, my);
            boolean cur = this.argb == FaceModEditorScreen.this.color;

            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xFF303030);
            RenderUtils.drawRect(ctx, this.x + 1, this.y + 1, this.width - 2, this.height - 2, this.argb);

            if (cur)
            {
                RenderUtils.drawOutlinedBox(ctx, this.x, this.y, this.width, this.height, 0, 0xFF00FF00);
            }
            else if (hov)
            {
                RenderUtils.drawOutlinedBox(ctx, this.x, this.y, this.width, this.height, 0, 0xFFFFFFFF);
            }
        }

        @Override
        protected boolean onMouseClickedImpl(Click click, boolean doubleClick)
        {
            if (click.getKeycode() == 0)
            {
                FaceModEditorScreen.this.pickColor(this.argb);
                return true;
            }

            return false;
        }
    }

    /** The saturation/value square: x = saturation, y = value (top full). */
    private class SvBox extends WidgetBase
    {
        SvBox(int x, int y, int w, int h)
        {
            super(x, y, w, h);
        }

        private int baseColor()
        {
            return hsvToRgb(FaceModEditorScreen.this.hue, 1.0F, 1.0F);
        }

        @Override
        public void render(GuiContext ctx, int mx, int my, boolean selected)
        {
            super.render(ctx, mx, my, selected);

            int base = this.baseColor();

            // Approximate gradients with horizontal (white) and vertical
            // (black) stripes over the full saturation hue.
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, base);

            int steps = 8;

            for (int i = 0; i < steps; i++)
            {
                int a = (int) (0xFF * (i + 1) / (float) steps);
                int w = (this.width * (i + 1) + steps - 1) / steps - this.width * i / steps;
                int wx = this.x + this.width * i / steps;
                int white = (a << 24) | 0xFFFFFF;

                RenderUtils.drawRect(ctx, wx, this.y, w, this.height, white);

                int vy = this.y + this.height * i / steps;
                int vh = (this.height * (i + 1) + steps - 1) / steps - this.height * i / steps;

                RenderUtils.drawRect(ctx, this.x, vy, this.width, vh, (a << 24));
            }

            boolean hov = this.isMouseOver(mx, my);

            if (hov)
            {
                RenderUtils.drawOutlinedBox(ctx, this.x, this.y, this.width, this.height, 0, 0xFFFFFFFF);
            }

            // Marker of the current s/v position
            int mkx = this.x + (int) (FaceModEditorScreen.this.sat * (this.width - 1));
            int mky = this.y + (int) ((1.0F - FaceModEditorScreen.this.val) * (this.height - 1));

            RenderUtils.drawOutlinedBox(ctx, mkx - 2, mky - 2, 5, 5, 0, 0xFFFFFFFF);
        }

        @Override
        protected boolean onMouseClickedImpl(Click click, boolean doubleClick)
        {
            // Handled at the screen level so the drag keeps tracking outside
            // the widget; this override only keeps the widget clickable.
            return false;
        }
    }

    /** The hue strip on the right of the s/v box. */
    private class HueBar extends WidgetBase
    {
        HueBar(int x, int y, int w, int h)
        {
            super(x, y, w, h);
        }

        @Override
        public void render(GuiContext ctx, int mx, int my, boolean selected)
        {
            super.render(ctx, mx, my, selected);

            int steps = 12;

            for (int i = 0; i < steps; i++)
            {
                int hy = this.y + this.height * i / steps;
                int hh = (this.height * (i + 1) + steps - 1) / steps - this.height * i / steps;

                RenderUtils.drawRect(ctx, this.x, hy, this.width, hh,
                        hsvToRgb(i / (float) steps, 1.0F, 1.0F));
            }

            boolean hov = this.isMouseOver(mx, my);

            if (hov)
            {
                RenderUtils.drawOutlinedBox(ctx, this.x, this.y, this.width, this.height, 0, 0xFFFFFFFF);
            }

            // Marker of the current hue
            int mky = this.y + (int) (FaceModEditorScreen.this.hue * (this.height - 1));

            RenderUtils.drawOutlinedBox(ctx, this.x - 1, mky - 1, this.width + 2, 3, 0, 0xFF000000);
            RenderUtils.drawOutlinedBox(ctx, this.x - 2, mky - 2, this.width + 4, 5, 0, 0xFFFFFFFF);
        }

        @Override
        protected boolean onMouseClickedImpl(Click click, boolean doubleClick)
        {
            // Handled at the screen level so the drag keeps tracking outside
            // the widget; this override only keeps the widget clickable.
            return false;
        }
    }

    /** Small preview of the current color under the hue bar. */
    private class CurPreview extends WidgetBase
    {
        CurPreview(int x, int y, int w, int h)
        {
            super(x, y, w, h);
        }

        @Override
        public void render(GuiContext ctx, int mx, int my, boolean selected)
        {
            super.render(ctx, mx, my, selected);

            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xFF303030);
            RenderUtils.drawRect(ctx, this.x + 1, this.y + 1, this.width - 2, this.height - 2,
                    FaceModEditorScreen.this.color);
        }

        @Override
        protected boolean onMouseClickedImpl(Click click, boolean doubleClick)
        {
            return false;
        }
    }
}