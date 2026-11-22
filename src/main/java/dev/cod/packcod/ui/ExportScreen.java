package dev.cod.packcod.ui;

import dev.cod.packcod.PackCodClient;
import dev.cod.packcod.pack.MixProject;
import dev.cod.packcod.pack.PackExporter;
import dev.cod.packcod.pack.PackLibrary;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.cod.packcod.ui.Theme.*;

/**
 * Last step before exporting: name the pack, pick the colour its name shows in, and give it an
 * icon (your own picture, or the automatic one). The preview shows it the way the game's
 * resource pack list will: icon, coloured name, and "Made with pack.cod" underneath.
 */
final class ExportScreen extends Screen {

    /** Minecraft's 16 text colours, then a few extra. */
    private static final int[] COLORS = {
            0xFFFFFF, 0xAAAAAA, 0x555555, 0x000000, 0xFF5555, 0xAA0000, 0xFFAA00, 0xFFFF55,
            0x55FF55, 0x00AA00, 0x55FFFF, 0x00AAAA, 0x5555FF, 0x0000AA, 0xFF55FF, 0xAA00AA,
            0xE8A33D, 0x7EC8E3, 0xB565D8, 0xF5B7C6, 0x8DBF6A, 0xD08C60, 0xE84393, 0x3A7BD5
    };

    private final MixerScreen parent;
    private final PackLibrary lib;
    private final MixProject project;
    private EditBox nameBox;
    private Identifier iconId;
    private String status = "";
    private int statusColor = DIM;
    private final List<Hit> hits = new ArrayList<>();
    /** Worked out in the background: size line and, if textures mix resolutions, a warning. */
    private volatile String sizeLine = "Working out the size...";
    private volatile String sizeWarn;

    private interface Click { void on(); }
    private record Hit(int x, int y, int w, int h, Click action) {
        boolean in(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }

    private static final int PH = 240;

    private int panelY() {
        return Math.max(4, (height - PH) / 2 - 22);
    }

    ExportScreen(MixerScreen parent, PackLibrary lib, MixProject project) {
        super(Component.literal("Export"));
        this.parent = parent;
        this.lib = lib;
        this.project = project;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        nameBox = new EditBox(font, cx - 96, panelY() + 114, 192, 10, Component.literal("Pack name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(48);
        nameBox.setValue(project.name);
        nameBox.setResponder(s -> project.name = s);
        addRenderableWidget(nameBox);
        refreshIcon();
        java.util.Map<String, String> picks = new java.util.TreeMap<>(project.picks);
        java.util.concurrent.CompletableFuture.runAsync(() -> measure(picks));
    }

    /** Adds up what will go in the zip and checks the block and item textures are all one size. */
    private void measure(java.util.Map<String, String> picks) {
        long bytes = 0;
        int files = 0;
        java.util.TreeMap<Integer, Integer> res = new java.util.TreeMap<>();
        try {
            for (var e : picks.entrySet()) {
                var src = lib.source(e.getValue());
                if (src == null) continue;
                byte[] d = src.read(e.getKey());
                if (d == null) continue;
                bytes += d.length;
                files++;
                byte[] meta = src.read(e.getKey() + ".mcmeta");
                if (meta != null) { bytes += meta.length; files++; }
                String k = e.getKey();
                if ((k.startsWith("textures/block/") || k.startsWith("textures/item/")) && k.endsWith(".png")) {
                    int w = PackLibrary.pngWidth(d);
                    if (w > 0) res.merge(w, 1, Integer::sum);
                }
            }
        } catch (Exception ignored) {
        }
        String size = bytes >= 1024 * 1024 ? String.format("%.1f MB", bytes / 1048576.0)
                : Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        sizeLine = "About " + size + "  •  " + files + " files (plus models it needs)";
        if (res.size() > 1) {
            StringBuilder sb = new StringBuilder("Mixed texture sizes: ");
            int i = 0;
            for (var r : res.entrySet()) {
                if (i++ > 0) sb.append(", ");
                sb.append(r.getKey()).append("x (").append(r.getValue()).append(")");
            }
            sizeWarn = sb.toString();
        } else {
            sizeWarn = null;
        }
    }

    private void refreshIcon() {
        releaseIcon();
        try {
            byte[] png = PackExporter.iconFor(lib, project);
            if (png == null) return;
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            iconId = Identifier.fromNamespaceAndPath("packcod", "export_icon/" + System.nanoTime());
            minecraft.getTextureManager().register(iconId, new DynamicTexture(() -> "packcod:export_icon", img));
        } catch (Exception ignored) {
            iconId = null;
        }
    }

    private void releaseIcon() {
        if (iconId != null) minecraft.getTextureManager().release(iconId);
        iconId = null;
    }

    private void pickIcon() {
        Thread t = new Thread(() -> {
            String path;
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.PointerBuffer filters = stack.mallocPointer(4);
                for (String f : new String[]{"*.png", "*.jpg", "*.jpeg", "*.gif"}) filters.put(stack.UTF8(f));
                filters.flip();
                path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Pick a pack icon", null, filters, "Images", false);
            } catch (Throwable e) {
                minecraft.execute(() -> flash("Couldn't open the file picker. Drag the picture onto this window instead.", BAD));
                return;
            }
            if (path != null) {
                final String p = path;
                minecraft.execute(() -> setIcon(Path.of(p)));
            }
        }, "packcod-icon-picker");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (!paths.isEmpty()) setIcon(paths.get(0));
    }

    private void setIcon(Path p) {
        if (PackExporter.customIcon(p.toString()) == null) {
            flash("That file isn't a picture I can read (try PNG or JPG)", BAD);
            return;
        }
        project.iconPath = p.toString();
        project.save();
        refreshIcon();
        flash("Icon set: " + p.getFileName(), GOOD);
    }

    private void export() {
        project.save();
        try {
            PackExporter.Result r = PackExporter.export(lib, project, minecraft.getResourcePackDirectory());
            Util.getPlatform().openPath(r.file().getParent());
            parent.exported(r);
            onClose();
        } catch (Exception e) {
            flash("Export failed: " + e.getMessage(), BAD);
        }
    }

    private void flash(String s, int c) {
        status = s;
        statusColor = c;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        g.fill(0, 0, width, height, BG);
        int pw = 236, ph = PH;
        int px = (width - pw) / 2, py = panelY();
        panel(g, px, py, pw, ph);
        Theme.label(g, font, "Export pack", px + 8, py + 7);

        // preview: how it looks in the game's resource pack list
        int ry = py + 22;
        g.fill(px + 8, ry, px + pw - 8, ry + 40, 0xFF000000);
        outline(g, px + 8, ry, pw - 16, 40, LINE);
        if (iconId != null) g.blit(RenderPipelines.GUI_TEXTURED, iconId, px + 12, ry + 4, 0f, 0f, 32, 32, 128, 128, 128, 128);
        String name = project.name == null || project.name.isBlank() ? "My Mix" : project.name;
        g.drawString(font, fit(font, PackExporter.safeName(name), pw - 70), px + 50, ry + 6, 0xFFFFFFFF, false);
        g.drawString(font, fit(font, name, pw - 70), px + 50, ry + 18, 0xFF000000 | project.nameColor, false);
        g.drawString(font, "Made with pack.cod", px + 50, ry + 28, 0xFFAAAAAA, false);

        // name
        int ny = nameBox.getY() - 4;
        Theme.label(g, font, "Name", px + 8, ny - 10);
        slot(g, nameBox.getX() - 4, ny, nameBox.getWidth() + 8, 16);

        // colour
        int cy = ny + 22;
        Theme.label(g, font, "Name colour", px + 8, cy);
        cy += 11;
        int cell = 12;
        int perRow = (pw - 16) / cell;
        for (int i = 0; i < COLORS.length; i++) {
            int c = COLORS[i];
            int x = px + 8 + (i % perRow) * cell, y = cy + (i / perRow) * cell;
            g.fill(x, y, x + cell - 2, y + cell - 2, 0xFF000000 | c);
            if ((project.nameColor & 0xFFFFFF) == c) outline(g, x - 1, y - 1, cell, cell, ACCENT);
            hits.add(new Hit(x, y, cell - 2, cell - 2, () -> project.nameColor = c));
        }
        cy += ((COLORS.length + perRow - 1) / perRow) * cell + 6;

        // icon
        Theme.label(g, font, "Icon", px + 8, cy);
        cy += 11;
        int bw = (pw - 20) / 2;
        button(g, px + 8, cy, bw, 15, "Upload icon", mx, my, false, this::pickIcon);
        button(g, px + 12 + bw, cy, bw, 15, "Auto icon", mx, my, false, () -> {
            project.iconPath = null;
            project.save();
            refreshIcon();
        });
        g.drawString(font, fit(font, "or drop a picture onto this window", pw - 16), px + 8, cy + 20, FAINT, false);

        // what's going in the zip
        g.fill(px + 8, py + ph - 22, px + pw - 8, py + ph - 21, LINE);
        g.drawString(font, fit(font, sizeLine, pw - 16), px + 8, py + ph - 19, DIM, false);
        String warn = sizeWarn;
        if (warn != null) {
            g.drawString(font, fit(font, warn, pw - 16), px + 8, py + ph - 10, BAD, false);
            if (mx >= px + 8 && my >= py + ph - 10 && mx < px + pw - 8 && my < py + ph - 1) {
                g.nextStratum();
                String tip = "Fine to export. Some things will just look sharper than others in game.";
                int tw = font.width(tip) + 8, tx = Math.max(2, Math.min(mx + 10, width - tw - 2));
                g.fill(tx, my - 14, tx + tw, my - 1, TIP);
                g.drawString(font, tip, tx + 4, my - 11, TEXT, false);
            }
        }

        // actions
        int ay = py + ph + 6;
        button(g, px, ay, 70, 18, "← Back", mx, my, false, this::onClose);
        button(g, px + pw - 90, ay, 90, 18, "EXPORT", mx, my, true, this::export);
        if (!status.isEmpty()) g.drawString(font, fit(font, status, pw), px, ay + 24, statusColor, false);
        g.drawString(font, "v" + PackCodClient.VERSION, width - 4 - font.width("v" + PackCodClient.VERSION), height - 12, FAINT, false);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String text, int mx, int my, boolean primary, Click a) {
        boolean hov = mx >= x && my >= y && mx < x + w && my < y + h;
        raised(g, x, y, w, h, hov, primary, true);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, primary ? ON_ACCENT : TEXT, false);
        hits.add(new Hit(x, y, w, h, a));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).in(event.x(), event.y())) {
                setFocused(null);
                hits.get(i).action().on();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        releaseIcon();
        minecraft.setScreen(parent);
    }
}
