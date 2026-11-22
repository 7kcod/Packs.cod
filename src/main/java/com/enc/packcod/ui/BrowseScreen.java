package com.enc.packcod.ui;

import com.enc.packcod.pack.PacksMc;
import com.enc.packcod.pack.Settings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static com.enc.packcod.ui.Theme.*;

/**
 * Browse: PacksMC's 50 most downloaded packs.
 *
 * "Get" opens the pack's PacksMC page, which is the only place PacksMC lets packs be downloaded.
 * pack.cod keeps an eye on your Downloads folder, and anything that lands there shows up at the
 * bottom with an Install button that puts it straight into your resourcepacks folder.
 */
final class BrowseScreen extends Screen {

    private static final int ROW = 22;

    private final MixerScreen parent;
    private final Path resourcepacks;
    private List<PacksMc.Pack> packs = PacksMc.bundled();
    private String source = "Most downloaded on PacksMC  •  list from " + PacksMc.bundledDate();
    private List<String> installedNorms = List.of();
    private volatile List<PacksMc.Found> found = List.of();
    private long lastScan;
    private boolean scanning;
    private boolean installedAny;
    private final java.util.Set<String> opened = new java.util.HashSet<>();

    private String query = "";
    private int resFilter; // 0 all, 16, 32, 64 (64 and up)
    private int scroll;
    private String status = "";
    private int statusColor = DIM;
    private long statusUntil;
    private String tooltip;
    private EditBox search;
    private boolean keyOpen;
    private int dialogHitsFrom;
    private EditBox keyBox;

    private interface Click { void on(); }
    private record Hit(int x, int y, int w, int h, Click action) {
        boolean in(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }
    private final List<Hit> hits = new ArrayList<>();

    BrowseScreen(MixerScreen parent, Path resourcepacks) {
        super(Component.literal("Browse"));
        this.parent = parent;
        this.resourcepacks = resourcepacks;
    }

    @Override
    protected void init() {
        installedNorms = PacksMc.installedNorms(resourcepacks);
        search = new EditBox(font, 60, 6, Math.min(140, width / 4), 10, Component.literal("Search"));
        search.setBordered(false);
        search.setHint(Component.literal("search packs"));
        search.setValue(query);
        search.setResponder(s -> { query = s.trim().toLowerCase(Locale.ROOT); scroll = 0; });
        addRenderableWidget(search);
        keyBox = new EditBox(font, width / 2 - 90, height / 2 + 4, 180, 10, Component.literal("API key"));
        keyBox.setBordered(false);
        keyBox.setMaxLength(120);
        keyBox.setHint(Component.literal("pmc_..."));
        keyBox.setValue(Settings.get().packsmcKey == null ? "" : Settings.get().packsmcKey);
        keyBox.visible = keyOpen;
        addRenderableWidget(keyBox);
        if (Settings.get().packsmcKey != null && !Settings.get().packsmcKey.isBlank()) refresh();
    }

    private void refresh() {
        String key = Settings.get().packsmcKey;
        if (key == null || key.isBlank()) {
            keyOpen = true;
            keyBox.visible = true;
            setFocused(keyBox);
            return;
        }
        flash("Getting the live list from PacksMC...", DIM);
        CompletableFuture.supplyAsync(() -> {
            try {
                return PacksMc.fetchLive(key);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }).whenComplete((list, err) -> minecraft.execute(() -> {
            if (err != null || list == null || list.isEmpty()) {
                String m = err == null ? "empty list" : err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
                flash("Couldn't refresh (" + m + "). Showing the built-in list.", BAD);
            } else {
                packs = list;
                source = "Most downloaded on PacksMC  •  live";
                flash("Live list loaded", GOOD);
            }
        }));
    }

    private void flash(String s, int c) {
        status = s;
        statusColor = c;
        statusUntil = System.currentTimeMillis() + 7000;
    }

    private void open(PacksMc.Pack p) {
        try {
            Util.getPlatform().openUri(java.net.URI.create(p.url()));
            opened.add(p.slug());
            flash("Opened " + p.name() + " on PacksMC. Download it there and it'll show up below.", GOOD);
        } catch (Exception e) {
            flash("Couldn't open your browser: " + e.getMessage(), BAD);
        }
    }

    private void install(PacksMc.Found f) {
        try {
            PacksMc.install(f, resourcepacks);
            installedAny = true;
            installedNorms = PacksMc.installedNorms(resourcepacks);
            found = new ArrayList<>(found);
            found.remove(f);
            flash("Installed " + f.name() + ". It's ready to mix.", GOOD);
        } catch (Exception e) {
            flash("Couldn't install " + f.name() + ": " + e.getMessage(), BAD);
        }
    }

    private List<PacksMc.Pack> shown() {
        List<PacksMc.Pack> out = new ArrayList<>();
        for (PacksMc.Pack p : packs) {
            if (!query.isEmpty() && !(p.name() + " " + p.author() + " " + String.join(" ", p.tags()))
                    .toLowerCase(Locale.ROOT).contains(query)) continue;
            int r = 0;
            try {
                r = p.res().isEmpty() ? 0 : Integer.parseInt(p.res().replace("x", ""));
            } catch (NumberFormatException ignored) {
            }
            if (resFilter != 0 && (r == 0 || (resFilter >= 64 ? r < 64 : r != resFilter))) continue;
            out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        tooltip = null;
        long now = System.currentTimeMillis();
        if (!scanning && now - lastScan > 2000) {
            scanning = true;
            lastScan = now;
            CompletableFuture.runAsync(() -> {
                found = PacksMc.scanDownloads(resourcepacks);
                scanning = false;
            });
        }
        g.fill(0, 0, width, height, BG);

        // top bar
        g.fill(0, 0, width, 20, PANEL);
        g.fill(0, 19, width, 20, LINE);
        button(g, 4, 3, 40, 14, "← Back", mx, my, false, true, this::onClose);
        slot(g, search.getX() - 4, 2, search.getWidth() + 8, 15);
        int fx = search.getX() + search.getWidth() + 10;
        String[] fl = {"All", "16x", "32x", "64x+"};
        int[] fv = {0, 16, 32, 64};
        for (int i = 0; i < fl.length; i++) {
            int w = font.width(fl[i]) + 8;
            boolean on = resFilter == fv[i];
            boolean hov = in(mx, my, fx, 3, w, 14);
            if (on) g.fill(fx, 3, fx + w, 17, ACCENT);
            else slot(g, fx, 3, w, 14);
            g.drawString(font, fl[i], fx + 4, 6, on ? ON_ACCENT : hov ? TEXT : DIM, false);
            final int v = fv[i];
            hits.add(new Hit(fx, 3, w, 14, () -> { resFilter = v; scroll = 0; }));
            fx += w + 2;
        }
        String rl = "↻ Refresh";
        int rw = font.width(rl) + 10;
        button(g, width - 4 - rw, 3, rw, 14, rl, mx, my, false, true, this::refresh);
        if (in(mx, my, width - 4 - rw, 3, rw, 14)) tooltip = "Get the live list (needs a free PacksMC API key)";

        // found-in-Downloads tray at the bottom
        List<PacksMc.Found> fnd = found;
        int trayRows = Math.min(3, fnd.size());
        int trayH = 30 + Math.max(1, trayRows) * 16;
        int listTop = 24, listBottom = height - trayH - 6;

        // list
        panel(g, 4, listTop, width - 8, listBottom - listTop);
        g.drawString(font, fit(font, source, width - 20), 10, listTop + 5, DIM, false);
        List<PacksMc.Pack> list = shown();
        int top = listTop + 17;
        int rows = Math.max(1, (listBottom - 3 - top) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        if (list.isEmpty()) g.drawString(font, "No packs match", 10, top + 6, FAINT, false);
        for (int i = 0; i < rows && i + scroll < list.size(); i++) {
            PacksMc.Pack p = list.get(i + scroll);
            int rank = packs.indexOf(p) + 1;
            drawRow(g, p, rank, 8, top + i * ROW, width - 16, ROW - 2, mx, my);
        }
        if (list.size() > rows) {
            int h = listBottom - 3 - top;
            int kh = Math.max(10, h * rows / list.size());
            int ky = top + (h - kh) * scroll / Math.max(1, list.size() - rows);
            g.fill(width - 8, top, width - 6, top + h, SLOT);
            g.fill(width - 8, ky, width - 6, ky + kh, FAINT);
        }

        // tray
        int ty = height - trayH - 2;
        panel(g, 4, ty, width - 8, trayH);
        Theme.label(g, font, "In your Downloads", 10, ty + 6);
        String hint = fnd.isEmpty() ? "watching " + PacksMc.downloadsDir() + " for pack zips"
                : fnd.size() + " pack" + (fnd.size() == 1 ? "" : "s") + " ready to install";
        g.drawString(font, fit(font, hint, width - 140), 10 + font.width("IN YOUR DOWNLOADS") + 26, ty + 6, FAINT, false);
        if (fnd.size() > 1) {
            int aw = font.width("Install all") + 12;
            button(g, width - 10 - aw, ty + 3, aw, 14, "Install all", mx, my, true, true,
                    () -> { for (PacksMc.Found f : new ArrayList<>(found)) install(f); });
        }
        if (fnd.isEmpty()) {
            g.drawString(font, fit(font, "Click Get on a pack, download it on PacksMC, and it pops up here.", width - 24),
                    10, ty + 20, DIM, false);
        }
        for (int i = 0; i < trayRows; i++) {
            PacksMc.Found f = fnd.get(i);
            int ry = ty + 19 + i * 16;
            boolean fresh = System.currentTimeMillis() - f.modified() < 10 * 60_000;
            if (fresh) g.fill(10, ry + 4, 13, ry + 7, GOOD);
            String sz = f.size() >= 1048576 ? String.format("%.1f MB", f.size() / 1048576.0) : (f.size() / 1024) + " KB";
            int iw = font.width("Install") + 12;
            g.drawString(font, fit(font, f.name(), width - 60 - iw - font.width(sz)), 16, ry + 2, TEXT, false);
            g.drawString(font, sz, width - 14 - iw - 6 - font.width(sz), ry + 2, FAINT, false);
            button(g, width - 10 - iw, ry, iw, 13, "Install", mx, my, true, true, () -> install(f));
        }

        // status line over the tray title when there is one
        if (System.currentTimeMillis() < statusUntil) {
            int sw = font.width(status) + 10;
            int sx = (width - sw) / 2;
            g.fill(sx, listBottom - 14, sx + sw, listBottom - 2, TIP);
            g.drawString(font, status, sx + 5, listBottom - 12, statusColor, false);
        }

        if (keyOpen) drawKeyDialog(g, mx, my);
    }

    private void drawRow(GuiGraphics g, PacksMc.Pack p, int rank, int x, int y, int w, int h, int mx, int my) {
        boolean hov = in(mx, my, x, y, w, h);
        if (hov) g.fill(x, y, x + w, y + h, HOVER_FILL);
        String rk = "#" + rank;
        g.drawString(font, rk, x + 4, y + (h - 8) / 2, rank <= 3 ? ACCENT : FAINT, false);
        // a tile with the pack's initials in a colour from its name
        int tx = x + 26;
        int col = 0xFF000000 | (java.awt.Color.HSBtoRGB((p.name().hashCode() & 0xFFFF) / 65535f, 0.45f, 0.55f) & 0xFFFFFF);
        g.fill(tx, y + 1, tx + h - 2, y + h - 1, col);
        String ini = initials(p.name());
        g.drawString(font, ini, tx + (h - 2 - font.width(ini)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, true);

        int nx = tx + h + 4;
        boolean inst = PacksMc.installed(p, installedNorms);
        int bw = font.width(inst ? "Installed" : "Get") + 14;
        int bx = x + w - 4 - bw;
        String dl = compact(p.downloads()) + " downloads";
        int dlx = bx - 8 - font.width(dl);
        String res = p.res().isEmpty() ? "" : p.res();
        int nameRoom = dlx - nx - 8 - (res.isEmpty() ? 0 : font.width(res) + 8);
        g.drawString(font, fit(font, p.name(), nameRoom), nx, y + 2, TEXT, false);
        String sub = "by " + p.author() + (p.tags().isEmpty() ? "" : "  •  " + String.join(", ", p.tags()));
        g.drawString(font, fit(font, sub, dlx - nx - 6), nx, y + 11, FAINT, false);
        if (!res.isEmpty()) {
            int rx = nx + Math.min(font.width(p.name()), nameRoom) + 5;
            g.fill(rx, y + 1, rx + font.width(res) + 4, y + 10, ACCENT_DARK);
            g.drawString(font, res, rx + 2, y + 2, TEXT, false);
        }
        g.drawString(font, dl, dlx, y + (h - 8) / 2, DIM, false);
        if (inst) {
            raised(g, bx, y + 2, bw, h - 4, false, false, false);
            g.drawString(font, "Installed", bx + 7, y + (h - 8) / 2, GOOD, false);
            hits.add(new Hit(bx, y + 2, bw, h - 4, () -> open(p)));
            if (in(mx, my, bx, y + 2, bw, h - 4)) tooltip = "Already in your resourcepacks  •  click to open its page anyway";
        } else {
            button(g, bx, y + 2, bw, h - 4, "Get", mx, my, true, true, () -> open(p));
            if (in(mx, my, bx, y + 2, bw, h - 4)) tooltip = opened.contains(p.slug())
                    ? "Opened  •  once it's downloaded it shows up at the bottom"
                    : "Opens " + p.name() + " on packsmc.com to download";
        }
        hits.add(new Hit(x, y, bx - x - 4, h, () -> open(p)));
    }

    private void drawKeyDialog(GuiGraphics g, int mx, int my) {
        g.nextStratum();
        g.fill(0, 0, width, height, 0xA0000000);
        dialogHitsFrom = hits.size();
        int w = 230, h = 92, x = (width - w) / 2, y = height / 2 - 40;
        panel(g, x, y, w, h);
        Theme.label(g, font, "Live list", x + 8, y + 6);
        g.drawString(font, fit(font, "The live list uses PacksMC's API, which needs", w - 16), x + 8, y + 18, DIM, false);
        g.drawString(font, fit(font, "a free key: packsmc.com/docs. Paste it here.", w - 16), x + 8, y + 28, DIM, false);
        slot(g, keyBox.getX() - 4, keyBox.getY() - 4, keyBox.getWidth() + 8, 16);
        int by = y + h - 20;
        button(g, x + 8, by, 60, 14, "Get a key", mx, my, false, true, () -> {
            try {
                Util.getPlatform().openUri(java.net.URI.create("https://packsmc.com/docs"));
            } catch (Exception ignored) {
            }
        });
        button(g, x + w - 8 - 50, by, 50, 14, "Save", mx, my, true, true, () -> {
            Settings.get().packsmcKey = keyBox.getValue().trim();
            Settings.get().save();
            keyOpen = false;
            keyBox.visible = false;
            refresh();
        });
        button(g, x + w - 8 - 50 - 54, by, 50, 14, "Cancel", mx, my, false, true, () -> {
            keyOpen = false;
            keyBox.visible = false;
        });
    }

    private static String initials(String name) {
        StringBuilder b = new StringBuilder();
        for (String part : name.replaceAll("[^A-Za-z0-9 ]", " ").trim().split("\\s+")) {
            if (!part.isEmpty()) b.append(Character.toUpperCase(part.charAt(0)));
            if (b.length() == 2) break;
        }
        return b.isEmpty() ? "?" : b.toString();
    }

    private static String compact(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1e6);
        if (n >= 1000) return String.format("%.1fk", n / 1e3);
        return String.valueOf(n);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String text, int mx, int my,
                        boolean primary, boolean enabled, Click action) {
        boolean hov = enabled && in(mx, my, x, y, w, h);
        raised(g, x, y, w, h, hov, primary, enabled);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, !enabled ? FAINT : primary ? ON_ACCENT : TEXT, false);
        if (enabled) hits.add(new Hit(x, y, w, h, action));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        if (tooltip != null && !keyOpen) {
            g.nextStratum();
            int w = font.width(tooltip) + 8;
            int x = Math.max(2, Math.min(mx + 10, width - w - 2));
            int y = Math.max(2, my - 14);
            g.fill(x, y, x + w, y + 13, TIP);
            g.drawString(font, tooltip, x + 4, y + 3, TEXT, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (keyOpen) {
            if (keyBox.mouseClicked(event, doubleClick)) {
                setFocused(keyBox);
                return true;
            }
            // only the dialog's buttons work while it's open
            for (int i = hits.size() - 1; i >= dialogHitsFrom; i--) {
                Hit h = hits.get(i);
                if (h.in(event.x(), event.y())) {
                    h.action().on();
                    return true;
                }
            }
            return true;
        }
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
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll += sy > 0 ? -2 : sy < 0 ? 2 : 0;
        return true;
    }

    @Override
    public void onClose() {
        if (keyOpen) {
            keyOpen = false;
            keyBox.visible = false;
            return;
        }
        if (installedAny) parent.rescan();
        minecraft.setScreen(parent);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
