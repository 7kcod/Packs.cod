package dev.cod.packcod.ui;

import dev.cod.packcod.pack.Assets;
import dev.cod.packcod.pack.PackExporter;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static dev.cod.packcod.ui.Theme.*;

/**
 * Pixel editor. Opens on any texture (whichever pack's version is in use), lets you draw
 * over it or start blank, and saves into a folder pack called "pack.cod edits" inside your
 * resourcepacks folder. That pack then shows up in pack.cod like any other, so your drawings
 * can be mixed, previewed and exported, and it also works as a normal pack on its own.
 */
public final class PixelEditorScreen extends Screen {

    public static final String EDITS_PACK = "pack.cod edits";

    private enum Tool { PENCIL("Pencil"), ERASER("Eraser"), FILL("Fill"), PICK("Pick");
        final String label; Tool(String l) { label = l; } }

    /** A plain, game-like palette to start from. */
    private static final int[] PALETTE = {
            0xFF000000, 0xFF3F3F3F, 0xFF7F7F7F, 0xFFBFBFBF, 0xFFFFFFFF, 0xFF5B3A1E, 0xFF8B5A2B, 0xFFC69C6D,
            0xFF7A1F1F, 0xFFC0392B, 0xFFE67E22, 0xFFF1C40F, 0xFF2E7D32, 0xFF5FBF3A, 0xFF17A589, 0xFF4AEDD9,
            0xFF1F4E9A, 0xFF3A7BD5, 0xFF6C3FA0, 0xFFB565D8, 0xFFE84393, 0xFFF5B7C6, 0xFF3C2F23, 0xFFE8D5A9
    };

    private final MixerScreen parent;
    private final String key;
    private final String fromName;
    private final byte[] originalPng;
    private final byte[] animationMeta;
    private final int texW, texH;

    private NativeImage image;
    private DynamicTexture texture;
    private Identifier texId;

    private Tool tool = Tool.PENCIL;
    private int color = 0xFF3A7BD5;
    private boolean grid = true;
    private boolean mirror;
    private int dragSlider = -1;
    private boolean painting;
    private int lastPx = -1, lastPy = -1;
    private final Deque<int[]> undo = new ArrayDeque<>();
    private final Deque<int[]> redo = new ArrayDeque<>();
    private String status = "";
    private long statusUntil;

    private int canvasX, canvasY, zoom;

    // image converter state
    private java.awt.image.BufferedImage imported;
    private String importedName;
    private boolean importFill;
    private boolean importNoBg;
    private ImageConverter.Colors importColors = ImageConverter.Colors.ALL;
    /** True while a picture is being placed: the right panel shows its controls. */
    private boolean placing;
    /** Recolour mode: hue / saturation / brightness applied over a copy taken when it opened. */
    private boolean recolouring;
    private int[] recolourBase;
    private float hueShift, satMul = 1f, briMul = 1f;
    private int[] placeBase;
    private float cropL, cropR, cropT, cropB, imgScale = 1f, opacity = 1f;
    private int offX, offY;
    private boolean movingImg;
    private double moveStartX, moveStartY;
    private int moveOffX0, moveOffY0;

    /** A slider bar drawn this frame; set() gets 0..1. */
    private record SliderHit(int x, int y, int w, int h, java.util.function.DoubleConsumer set) {
        boolean in(double mx, double my) { return mx >= x - 2 && mx < x + w + 2 && my >= y - 2 && my < y + h + 2; }
        void apply(double mx) { set.accept(Math.max(0, Math.min(1, (mx - x - 1) / Math.max(1.0, w - 2)))); }
    }
    private final List<SliderHit> sliders = new ArrayList<>();
    private SliderHit activeSlider;
    private final java.util.Set<Integer> packPalette = new java.util.LinkedHashSet<>();
    private final List<Hit> hits = new ArrayList<>();
    private String tooltip;

    private interface Click { void on(); }
    private record Hit(int x, int y, int w, int h, Click action) {
        boolean in(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }

    /**
     * @param png the texture to start from, or null for a blank 16x16
     * @param animationMeta the .png.mcmeta that came with it (kept so animations still work), or null
     */
    public PixelEditorScreen(MixerScreen parent, String key, String fromName, byte[] png, byte[] animationMeta) {
        super(Component.literal("pack.cod draw"));
        this.parent = parent;
        this.key = key;
        this.fromName = fromName;
        this.originalPng = png;
        this.animationMeta = animationMeta;
        NativeImage start = load(png);
        this.texW = start.getWidth();
        this.texH = start.getHeight();
        this.image = start;
        for (int y = 0; y < texH; y++)
            for (int x = 0; x < texW; x++) {
                int c = safeGet(start, x, y);
                if (((c >>> 24) & 0xFF) > 0) packPalette.add(c | 0xFF000000);
            }
    }

    private static NativeImage load(byte[] png) {
        if (png != null) {
            try (NativeImage read = NativeImage.read(new ByteArrayInputStream(png))) {
                // Copy into a fresh RGBA image so every pack's PNG flavour behaves the same.
                NativeImage out = new NativeImage(read.getWidth(), read.getHeight(), true);
                for (int y = 0; y < read.getHeight(); y++)
                    for (int x = 0; x < read.getWidth(); x++)
                        out.setPixel(x, y, safeGet(read, x, y));
                return out;
            } catch (Exception ignored) {
            }
        }
        return new NativeImage(16, 16, true);
    }

    private static int safeGet(NativeImage img, int x, int y) {
        try {
            return img.getPixel(x, y);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    protected void init() {
        if (texture == null) {
            texture = new DynamicTexture(() -> "packcod:editor", image);
            texId = Identifier.fromNamespaceAndPath("packcod", "editor/" + System.nanoTime());
            minecraft.getTextureManager().register(texId, texture);
        }
    }

    private NativeImage pixels() {
        return texture.getPixels();
    }

    // ------------------------------------------------------------------ editing

    private int[] snapshot() {
        NativeImage p = pixels();
        int[] a = new int[texW * texH];
        for (int y = 0; y < texH; y++) for (int x = 0; x < texW; x++) a[y * texW + x] = p.getPixel(x, y);
        return a;
    }

    private void restore(int[] a) {
        NativeImage p = pixels();
        for (int y = 0; y < texH; y++) for (int x = 0; x < texW; x++) p.setPixel(x, y, a[y * texW + x]);
        texture.upload();
    }

    private void pushUndo() {
        undo.push(snapshot());
        if (undo.size() > 60) undo.removeLast();
        redo.clear();
    }

    private void doUndo() {
        if (undo.isEmpty()) return;
        redo.push(snapshot());
        restore(undo.pop());
    }

    private void doRedo() {
        if (redo.isEmpty()) return;
        undo.push(snapshot());
        restore(redo.pop());
    }

    private void set(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= texW || y >= texH) return;
        pixels().setPixel(x, y, argb);
        if (mirror) {
            int mx = texW - 1 - x;
            if (mx != x) pixels().setPixel(mx, y, argb);
        }
    }

    /** Paints a straight run from the last point so fast drags don't leave gaps. */
    private void paintTo(int px, int py) {
        int c = tool == Tool.ERASER ? 0 : color;
        if (lastPx < 0) {
            set(px, py, c);
        } else {
            int dx = Math.abs(px - lastPx), dy = Math.abs(py - lastPy);
            int steps = Math.max(dx, dy);
            for (int i = 0; i <= steps; i++) {
                int x = lastPx + (steps == 0 ? 0 : Math.round((px - lastPx) * (float) i / steps));
                int y = lastPy + (steps == 0 ? 0 : Math.round((py - lastPy) * (float) i / steps));
                set(x, y, c);
            }
        }
        lastPx = px;
        lastPy = py;
        texture.upload();
    }

    private void flood(int sx, int sy) {
        NativeImage p = pixels();
        int target = p.getPixel(sx, sy);
        if (target == color) return;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{sx, sy});
        boolean[] seen = new boolean[texW * texH];
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int x = c[0], y = c[1];
            if (x < 0 || y < 0 || x >= texW || y >= texH || seen[y * texW + x]) continue;
            seen[y * texW + x] = true;
            if (p.getPixel(x, y) != target) continue;
            set(x, y, color);
            q.add(new int[]{x + 1, y});
            q.add(new int[]{x - 1, y});
            q.add(new int[]{x, y + 1});
            q.add(new int[]{x, y - 1});
        }
        texture.upload();
    }

    private void clearAll() {
        pushUndo();
        NativeImage p = pixels();
        for (int y = 0; y < texH; y++) for (int x = 0; x < texW; x++) p.setPixel(x, y, 0);
        texture.upload();
    }

    private void startOver() {
        pushUndo();
        NativeImage fresh = load(originalPng);
        NativeImage p = pixels();
        for (int y = 0; y < texH; y++) for (int x = 0; x < texW; x++)
            p.setPixel(x, y, x < fresh.getWidth() && y < fresh.getHeight() ? fresh.getPixel(x, y) : 0);
        fresh.close();
        texture.upload();
    }

    // ------------------------------------------------------------------ recolour

    private void startRecolour() {
        if (placing) return;
        pushUndo();
        recolourBase = snapshot();
        hueShift = 0;
        satMul = briMul = 1f;
        recolouring = true;
    }

    /** Pixels are ARGB; shifts hue and scales saturation and brightness of each pixel. */
    private void applyRecolour() {
        if (recolourBase == null) return;
        NativeImage p = pixels();
        float[] hsb = new float[3];
        for (int i = 0; i < recolourBase.length; i++) {
            int c = recolourBase[i];
            int a = (c >>> 24) & 0xFF;
            if (a == 0) {
                p.setPixel(i % texW, i / texW, c);
                continue;
            }
            int r = (c >>> 16) & 0xFF, gr = (c >>> 8) & 0xFF, b = c & 0xFF;
            java.awt.Color.RGBtoHSB(r, gr, b, hsb);
            float h = hsb[0] + hueShift / 360f;
            h -= (float) Math.floor(h);
            float sat = Math.max(0, Math.min(1, hsb[1] * satMul));
            float bri = Math.max(0, Math.min(1, hsb[2] * briMul));
            int rgb = java.awt.Color.HSBtoRGB(h, sat, bri);
            p.setPixel(i % texW, i / texW, (a << 24) | (rgb & 0xFFFFFF));
        }
        texture.upload();
    }

    private void finishRecolour() {
        recolouring = false;
        recolourBase = null;
        flash("Recoloured. Undo takes it back.");
    }

    private void cancelRecolour() {
        if (recolourBase != null) restore(recolourBase);
        recolouring = false;
        recolourBase = null;
    }

    private void drawRecolourPanel(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        Theme.label(g, font, "Recolour", x + 5, y + 5);
        g.drawString(font, fit(font, "changes the whole texture", w - 10), x + 5, y + 16, FAINT, false);
        int ix = x + 5, iw = w - 10, sy = y + 32;
        slider(g, ix, sy, iw, 34, "Hue " + Math.round(hueShift), (hueShift + 180) / 360f, 0xFFE8A33D,
                d -> { hueShift = (float) (d * 360 - 180); applyRecolour(); });
        sy += 15;
        slider(g, ix, sy, iw, 34, "Sat " + Math.round(satMul * 100) + "%", satMul / 2f, 0xFF8DBF6A,
                d -> { satMul = (float) (d * 2); applyRecolour(); });
        sy += 15;
        slider(g, ix, sy, iw, 34, "Light " + Math.round(briMul * 100) + "%", briMul / 2f, 0xFFBFBFBF,
                d -> { briMul = (float) (d * 2); applyRecolour(); });
        sy += 20;
        // quick looks
        String[] names = {"Red", "Green", "Blue", "Purple", "Gold", "Grey"};
        float[] hues = {0, 120, 220, 280, 45, 0};
        int half = iw / 2;
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            int bx = ix + (i % 2) * half, by = sy + (i / 2) * 15;
            button(g, bx, by, half - 1, 13, names[i], mx, my, false, () -> {
                if (k == 5) { satMul = 0; hueShift = 0; }
                else { hueShift = relativeHue(hues[k]); satMul = Math.max(satMul, 1f); }
                applyRecolour();
            });
        }
        sy += 3 * 15 + 6;
        button(g, ix, sy, iw, 13, "Reset sliders", mx, my, false, () -> {
            hueShift = 0;
            satMul = briMul = 1f;
            applyRecolour();
        });
        sy += 20;
        button(g, ix, sy, half - 1, 15, "Done", mx, my, true, this::finishRecolour);
        button(g, ix + half + 1, sy, iw - half - 1, 15, "Cancel", mx, my, false, this::cancelRecolour);
    }

    /** Hue shift that moves the texture's main colour to the target hue. */
    private float relativeHue(float target) {
        double sx = 0, sy = 0;
        float[] hsb = new float[3];
        for (int c : recolourBase) {
            if (((c >>> 24) & 0xFF) < 128) continue;
            java.awt.Color.RGBtoHSB((c >>> 16) & 0xFF, (c >>> 8) & 0xFF, c & 0xFF, hsb);
            double wgt = hsb[1] * hsb[2];
            sx += Math.cos(hsb[0] * Math.PI * 2) * wgt;
            sy += Math.sin(hsb[0] * Math.PI * 2) * wgt;
        }
        float main = (float) Math.toDegrees(Math.atan2(sy, sx));
        float d = target - main;
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    // ------------------------------------------------------------------ image converter

    /** Opens the system file picker (off the render thread) for a picture to convert. */
    private void pickImage() {
        if (recolouring) finishRecolour();
        Thread t = new Thread(() -> {
            String path = null;
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.PointerBuffer filters = stack.mallocPointer(5);
                for (String f : new String[]{"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"}) filters.put(stack.UTF8(f));
                filters.flip();
                path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                        "Pick a picture to turn into pixel art", null, filters, "Images", false);
            } catch (Throwable e) {
                final String msg = "Couldn't open the file picker. Drag the picture onto this window instead.";
                minecraft.execute(() -> flash(msg));
                return;
            }
            if (path != null) {
                final java.nio.file.Path p = java.nio.file.Path.of(path);
                minecraft.execute(() -> loadImport(p));
            }
        }, "packcod-file-picker");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onFilesDrop(java.util.List<java.nio.file.Path> paths) {
        if (!paths.isEmpty()) loadImport(paths.get(0));
    }

    private void loadImport(java.nio.file.Path p) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(p.toFile());
            if (img == null) {
                flash("That file isn't a picture I can read (try PNG or JPG)");
                return;
            }
            imported = img;
            importedName = p.getFileName().toString();
            cropL = cropR = cropT = cropB = 0;
            imgScale = 1f;
            opacity = 1f;
            offX = offY = 0;
            pushUndo();
            placeBase = snapshot();
            placing = true;
            applyImport();
        } catch (Exception e) {
            flash("Couldn't read that picture: " + e.getMessage());
        }
    }

    /** Re-runs the conversion with the current options and puts it on the canvas (undoable). */
    private void applyImport() {
        if (imported == null || placeBase == null) return;
        var pl = new ImageConverter.Placement(importFill, importNoBg, importColors, cropL, cropR, cropT, cropB,
                imgScale, offX, offY, opacity);
        int[] px = ImageConverter.over(placeBase, ImageConverter.convert(imported, texW, texH, pl, packPalette));
        NativeImage p = pixels();
        for (int y = 0; y < texH; y++) for (int x = 0; x < texW; x++) p.setPixel(x, y, px[y * texW + x]);
        texture.upload();
    }

    private void finishPlacing() {
        placing = false;
        imported = null;
        placeBase = null;
        flash("Picture placed. Keep drawing over it, or SAVE.");
    }

    private void cancelPlacing() {
        if (placeBase != null) restore(placeBase);
        placing = false;
        imported = null;
        placeBase = null;
    }

    /** Right-hand panel while a picture is being placed. */
    private void drawImagePanel(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        Theme.label(g, font, "Picture", x + 5, y + 5);
        g.drawString(font, fit(font, importedName, w - 10), x + 5, y + 16, DIM, false);
        int iw = w - 10, ix = x + 5;
        int sy = y + 30;
        int half = iw / 2;
        toggle(g, ix, sy, half, 13, "Fit", !importFill, mx, my, () -> { importFill = false; applyImport(); });
        toggle(g, ix + half, sy, iw - half, 13, "Fill", importFill, mx, my, () -> { importFill = true; applyImport(); });
        sy += 18;

        slider(g, ix, sy, iw, 34, "Size " + Math.round(imgScale * 100) + "%", (float) (Math.log(imgScale / 0.1) / Math.log(40)),
                0xFFE8A33D, d -> { imgScale = (float) (0.1 * Math.pow(40, d)); applyImport(); });
        sy += 14;
        slider(g, ix, sy, iw, 34, "Opac " + Math.round(opacity * 100) + "%", opacity, 0xFFBFBFBF,
                d -> { opacity = (float) d; applyImport(); });
        sy += 17;

        Theme.label(g, font, "Crop", ix, sy);
        sy += 11;
        slider(g, ix, sy, iw, 34, "Left", cropL / 0.45f, 0xFF8DBF6A, d -> { cropL = (float) d * 0.45f; applyImport(); });
        sy += 13;
        slider(g, ix, sy, iw, 34, "Right", cropR / 0.45f, 0xFF8DBF6A, d -> { cropR = (float) d * 0.45f; applyImport(); });
        sy += 13;
        slider(g, ix, sy, iw, 34, "Top", cropT / 0.45f, 0xFF8DBF6A, d -> { cropT = (float) d * 0.45f; applyImport(); });
        sy += 13;
        slider(g, ix, sy, iw, 34, "Bottom", cropB / 0.45f, 0xFF8DBF6A, d -> { cropB = (float) d * 0.45f; applyImport(); });
        sy += 16;

        Theme.label(g, font, "Move", ix, sy);
        g.drawString(font, offX + ", " + offY, ix + 40, sy, DIM, false);
        sy += 11;
        int aw = 16;
        button(g, ix, sy, aw, 13, "<", mx, my, false, () -> { offX--; applyImport(); });
        button(g, ix + aw + 2, sy, aw, 13, ">", mx, my, false, () -> { offX++; applyImport(); });
        button(g, ix + 2 * (aw + 2), sy, aw, 13, "^", mx, my, false, () -> { offY--; applyImport(); });
        button(g, ix + 3 * (aw + 2), sy, aw, 13, "v", mx, my, false, () -> { offY++; applyImport(); });
        button(g, ix + 4 * (aw + 2), sy, iw - 4 * (aw + 2), 13, "Centre", mx, my, false, () -> { offX = offY = 0; applyImport(); });
        sy += 15;
        g.drawString(font, fit(font, "or drag it on the canvas", iw), ix, sy, FAINT, false);
        sy += 13;

        button(g, ix, sy, iw, 13, fit(font, importColors.label, iw - 4), mx, my, false,
                () -> { importColors = importColors.next(); applyImport(); });
        if (in(mx, my, ix, sy, iw, 13)) tooltip = "Match pack snaps to the colours the original texture uses";
        sy += 14;
        toggle(g, ix, sy, iw, 13, "No background", importNoBg, mx, my, () -> { importNoBg = !importNoBg; applyImport(); });
        sy += 18;

        button(g, ix, sy, half - 1, 15, "Done", mx, my, true, this::finishPlacing);
        button(g, ix + half + 1, sy, iw - half - 1, 15, "Cancel", mx, my, false, this::cancelPlacing);
    }

    /** Labelled slider; the label takes labelW pixels on the left. */
    private void slider(GuiGraphics g, int x, int y, int w, int labelW, String label, float v01, int tint,
                        java.util.function.DoubleConsumer set) {
        v01 = Math.max(0, Math.min(1, v01));
        g.drawString(font, fit(font, label, labelW + 30), x, y + 1, DIM, false);
        int lw = Math.max(labelW, Math.min(w / 2, font.width(label) + 4));
        int bx = x + lw, bw = w - lw;
        slot(g, bx, y, bw, 9);
        g.fill(bx + 1, y + 1, bx + 1 + Math.round((bw - 2) * v01), y + 8, tint);
        int kx = bx + Math.round((bw - 2) * v01);
        g.fill(kx, y - 1, kx + 2, y + 10, TEXT);
        sliders.add(new SliderHit(bx, y, bw, 9, set));
    }

    /** Writes the drawing into resourcepacks/pack.cod edits/ and tells the mixer to use it. */
    private void save() {
        if (recolouring) finishRecolour();
        try {
            Path pack = minecraft.getResourcePackDirectory().resolve(EDITS_PACK);
            Path meta = pack.resolve("pack.mcmeta");
            if (!Files.exists(meta)) {
                Files.createDirectories(pack);
                Files.writeString(meta, "{\n  \"pack\": {\n    \"description\": \"Drawn in pack.cod\",\n"
                        + "    \"pack_format\": " + PackExporter.PACK_FORMAT + ",\n"
                        + "    \"min_format\": " + PackExporter.PACK_FORMAT + ",\n"
                        + "    \"max_format\": " + PackExporter.PACK_FORMAT + "\n  }\n}\n", StandardCharsets.UTF_8);
            }
            Path file = pack.resolve("assets/minecraft/" + key);
            Files.createDirectories(file.getParent());
            pixels().writeToFile(file);
            if (animationMeta != null) Files.write(file.resolveSibling(file.getFileName() + ".mcmeta"), animationMeta);
            parent.onDrawingSaved(key);
            flash("Saved to \"" + EDITS_PACK + "\" and switched this item to it");
        } catch (Exception e) {
            flash("Couldn't save: " + e.getMessage());
        }
    }

    private void flash(String s) {
        status = s;
        statusUntil = System.currentTimeMillis() + 5000;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        sliders.clear();
        tooltip = null;
        g.fill(0, 0, width, height, BG);

        // top bar
        g.fill(0, 0, width, 20, PANEL);
        g.fill(0, 19, width, 20, LINE);
        button(g, 4, 3, 36, 14, "← Back", mx, my, false, this::onClose);
        String title = "Draw: " + Assets.pretty(key) + "  " + texW + "x" + texH + "  from " + fromName;
        int saveW = font.width("SAVE") + 16;
        int right = width - 4 - saveW - 4 - 30 - 2 - 30;
        g.drawString(font, fit(font, title, right - 50), 46, 6, TEXT, false);
        int bx = width - 4 - saveW;
        button(g, bx, 3, saveW, 14, "SAVE", mx, my, true, this::save);
        bx -= 34;
        button(g, bx, 3, 30, 14, "Redo", mx, my, false, this::doRedo);
        bx -= 32;
        button(g, bx, 3, 30, 14, "Undo", mx, my, false, this::doUndo);

        int bodyY = 24, bodyH = height - bodyY - 16;
        int toolsW = 64, sideW = Math.min(150, Math.max(110, width / 4));

        // tools
        panel(g, 4, bodyY, toolsW, bodyH);
        int ty = bodyY + 4;
        int bw = toolsW - 8;
        for (Tool t : Tool.values()) {
            toggle(g, 8, ty, bw, 13, t.label, tool == t, mx, my, () -> tool = t);
            ty += 14;
        }
        ty += 3;
        toggle(g, 8, ty, bw, 13, "Mirror", mirror, mx, my, () -> mirror = !mirror);
        if (in(mx, my, 8, ty, bw, 13)) tooltip = "Draw both sides at once";
        ty += 14;
        toggle(g, 8, ty, bw, 13, "Grid", grid, mx, my, () -> grid = !grid);
        ty += 17;
        button(g, 8, ty, bw, 13, "Clear", mx, my, false, this::clearAll);
        ty += 14;
        button(g, 8, ty, bw, 13, "Start over", mx, my, false, this::startOver);
        if (in(mx, my, 8, ty, bw, 13)) tooltip = "Back to how it looked when you opened it";

        // image converter
        ty += 18;
        g.fill(8, ty - 4, 8 + bw, ty - 3, LINE);
        button(g, 8, ty, bw, 13, "Import img", mx, my, true, this::pickImage);
        if (in(mx, my, 8, ty, bw, 13)) tooltip = "Turn a picture into pixel art (or drag one onto this window)";
        ty += 14;
        button(g, 8, ty, bw, 13, "Recolour", mx, my, false, this::startRecolour);
        if (in(mx, my, 8, ty, bw, 13)) tooltip = "Change the hue, saturation and brightness of the whole texture";
        ty += 14;


        // colour side
        int sx = width - 4 - sideW;
        panel(g, sx, bodyY, sideW, bodyH);
        if (placing) drawImagePanel(g, sx, bodyY, sideW, bodyH, mx, my);
        else if (recolouring) drawRecolourPanel(g, sx, bodyY, sideW, bodyH, mx, my);
        else drawColorSide(g, sx, bodyY, sideW, bodyH, mx, my);

        // canvas
        int ax = 4 + toolsW + 6, aw = sx - 6 - ax, ah = bodyH;
        zoom = Math.max(1, Math.min((aw - 8) / texW, (ah - 8) / texH));
        int cw = texW * zoom, ch = texH * zoom;
        canvasX = ax + (aw - cw) / 2;
        canvasY = bodyY + (ah - ch) / 2;
        outline(g, canvasX - 1, canvasY - 1, cw + 2, ch + 2, LINE);
        int check = Math.max(4, zoom);
        for (int y = 0; y < ch; y += check) {
            for (int x = 0; x < cw; x += check) {
                boolean dark = ((x / check) + (y / check)) % 2 == 0;
                g.fill(canvasX + x, canvasY + y, canvasX + Math.min(cw, x + check), canvasY + Math.min(ch, y + check),
                        dark ? 0xFF2A2A2A : 0xFF3A3A3A);
            }
        }
        g.blit(RenderPipelines.GUI_TEXTURED, texId, canvasX, canvasY, 0f, 0f, cw, ch, texW, texH, texW, texH);
        if (grid && zoom >= 4) {
            for (int x = 1; x < texW; x++) g.fill(canvasX + x * zoom, canvasY, canvasX + x * zoom + 1, canvasY + ch, 0x22FFFFFF);
            for (int y = 1; y < texH; y++) g.fill(canvasX, canvasY + y * zoom, canvasX + cw, canvasY + y * zoom + 1, 0x22FFFFFF);
        }
        int hx = (mx - canvasX) / Math.max(1, zoom), hy = (my - canvasY) / Math.max(1, zoom);
        if (mx >= canvasX && my >= canvasY && hx < texW && hy < texH) {
            outline(g, canvasX + hx * zoom, canvasY + hy * zoom, zoom, zoom, 0xCCFFFFFF);
            if (mirror) outline(g, canvasX + (texW - 1 - hx) * zoom, canvasY + hy * zoom, zoom, zoom, 0x66FFFFFF);
            tooltip = hx + ", " + hy;
        }

        // bottom line
        String hint = System.currentTimeMillis() < statusUntil ? status
                : "Left click draws  •  right click picks a colour  •  saved drawings go to the \"" + EDITS_PACK + "\" pack";
        g.drawString(font, fit(font, hint, width - 8), 4, height - 12, System.currentTimeMillis() < statusUntil ? GOOD : FAINT, false);
    }

    private void drawColorSide(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        Theme.label(g, font, "Colour", x + 5, y + 5);
        // current colour over a checker so see-through shows
        int sw = w - 10;
        g.fill(x + 5, y + 16, x + 5 + sw, y + 34, 0xFF3A3A3A);
        g.fill(x + 5, y + 16, x + 5 + sw / 2, y + 25, 0xFF2A2A2A);
        g.fill(x + 5 + sw / 2, y + 25, x + 5 + sw, y + 34, 0xFF2A2A2A);
        g.fill(x + 5, y + 16, x + 5 + sw, y + 34, color);
        outline(g, x + 5, y + 16, sw, 18, LINE);
        String hex = String.format("#%06X  %d%%", color & 0xFFFFFF, Math.round(((color >>> 24) & 0xFF) / 2.55f));
        g.drawString(font, hex, x + 5, y + 37, DIM, false);

        // R G B A sliders
        String[] names = {"R", "G", "B", "A"};
        int[] shifts = {16, 8, 0, 24};
        int[] tints = {0xFFD9614C, 0xFF8DBF6A, 0xFF5B7DB1, 0xFFBFBFBF};
        int sy = y + 49;
        for (int i = 0; i < 4; i++) {
            int v = (color >>> shifts[i]) & 0xFF;
            final int shift = shifts[i];
            slider(g, x + 5, sy, w - 10, 10, names[i], v / 255f, tints[i],
                    d -> color = (color & ~(0xFF << shift)) | ((int) Math.round(d * 255) << shift));
            sy += 13;
        }

        // palette
        sy += 3;
        Theme.label(g, font, "Palette", x + 5, sy);
        sy += 11;
        int cell = Math.max(8, (w - 10) / 8);
        for (int i = 0; i < PALETTE.length; i++) {
            int cx = x + 5 + (i % 8) * cell, cy = sy + (i / 8) * cell;
            swatch(g, cx, cy, cell - 1, PALETTE[i], mx, my);
        }
        sy += ((PALETTE.length + 7) / 8) * cell + 4;

        // colours already in the texture
        Theme.label(g, font, "In this texture", x + 5, sy);
        sy += 11;
        Set<Integer> used = new LinkedHashSet<>();
        NativeImage p = pixels();
        for (int py = 0; py < texH && used.size() < 32; py++)
            for (int px = 0; px < texW && used.size() < 32; px++) {
                int c = p.getPixel(px, py);
                if (((c >>> 24) & 0xFF) > 0) used.add(c);
            }
        int i = 0;
        for (int c : used) {
            int cx = x + 5 + (i % 8) * cell, cy = sy + (i / 8) * cell;
            if (cy + cell > y + h - 40) break;
            swatch(g, cx, cy, cell - 1, c, mx, my);
            i++;
        }

        // actual-size previews
        int py = y + h - 36;
        Theme.label(g, font, "Actual size", x + 5, py);
        int frame = Math.min(texW, texH);
        g.blit(RenderPipelines.GUI_TEXTURED, texId, x + 5, py + 11, 0f, 0f, 16, 16, texW, frame, texW, texH);
        g.blit(RenderPipelines.GUI_TEXTURED, texId, x + 25, py + 11, 0f, 0f, 22, 22, texW, frame, texW, texH);
    }

    private void swatch(GuiGraphics g, int x, int y, int s, int c, int mx, int my) {
        g.fill(x, y, x + s, y + s, c);
        if (c == color) outline(g, x - 1, y - 1, s + 2, s + 2, ACCENT);
        else if (in(mx, my, x, y, s, s)) outline(g, x, y, s, s, TEXT);
        hits.add(new Hit(x, y, s, s, () -> color = c));
    }

    private void toggle(GuiGraphics g, int x, int y, int w, int h, String label, boolean on, int mx, int my, Click a) {
        boolean hov = in(mx, my, x, y, w, h);
        if (on) g.fill(x, y, x + w, y + h, ACCENT);
        else raised(g, x, y, w, h, hov, false, true);
        g.drawString(font, label, x + 5, y + (h - 8) / 2, on ? ON_ACCENT : TEXT, false);
        hits.add(new Hit(x, y, w, h, a));
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String text, int mx, int my, boolean primary, Click a) {
        boolean hov = in(mx, my, x, y, w, h);
        raised(g, x, y, w, h, hov, primary, true);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, primary ? ON_ACCENT : TEXT, false);
        hits.add(new Hit(x, y, w, h, a));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        if (tooltip != null) {
            g.nextStratum();
            int w = font.width(tooltip) + 8;
            int x = Math.max(2, Math.min(mx + 10, width - w - 2));
            int y = Math.max(2, my - 14);
            g.fill(x, y, x + w, y + 13, TIP);
            g.drawString(font, tooltip, x + 4, y + 3, TEXT, false);
        }
    }

    // ------------------------------------------------------------------ input

    private int[] pixelAt(double mx, double my) {
        if (zoom <= 0 || mx < canvasX || my < canvasY) return null;
        int px = (int) ((mx - canvasX) / zoom), py = (int) ((my - canvasY) / zoom);
        return px < texW && py < texH ? new int[]{px, py} : null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (SliderHit sh : sliders) {
            if (sh.in(mx, my)) {
                activeSlider = sh;
                sh.apply(mx);
                return true;
            }
        }
        int[] p = pixelAt(mx, my);
        if (p != null && placing) {
            // while placing a picture, dragging on the canvas moves it
            movingImg = true;
            moveStartX = mx;
            moveStartY = my;
            moveOffX0 = offX;
            moveOffY0 = offY;
            return true;
        }
        if (p != null && recolouring) {
            flash("Finish recolouring first (Done or Cancel)");
            return true;
        }
        if (p != null) {
            if (event.button() == 1 || tool == Tool.PICK) {
                int c = pixels().getPixel(p[0], p[1]);
                if (((c >>> 24) & 0xFF) > 0) color = c;
                if (tool == Tool.PICK) tool = Tool.PENCIL;
                return true;
            }
            pushUndo();
            if (tool == Tool.FILL) {
                flood(p[0], p[1]);
            } else {
                painting = true;
                lastPx = -1;
                paintTo(p[0], p[1]);
            }
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).in(mx, my)) {
                hits.get(i).action().on();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (activeSlider != null) {
            activeSlider.apply(event.x());
            return true;
        }
        if (movingImg) {
            int nx = moveOffX0 + (int) Math.round((event.x() - moveStartX) / Math.max(1, zoom));
            int ny = moveOffY0 + (int) Math.round((event.y() - moveStartY) / Math.max(1, zoom));
            if (nx != offX || ny != offY) {
                offX = nx;
                offY = ny;
                applyImport();
            }
            return true;
        }
        if (painting) {
            int[] p = pixelAt(event.x(), event.y());
            if (p != null) paintTo(p[0], p[1]);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragSlider = -1;
        activeSlider = null;
        movingImg = false;
        painting = false;
        lastPx = lastPy = -1;
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        if (texId != null) minecraft.getTextureManager().release(texId);
        texId = null;
        minecraft.setScreen(parent);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
