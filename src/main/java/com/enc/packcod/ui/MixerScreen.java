package com.enc.packcod.ui;

import com.enc.packcod.PackCodClient;
import com.enc.packcod.pack.AssetSource;
import com.enc.packcod.pack.Assets;
import com.enc.packcod.pack.MixProject;
import com.enc.packcod.pack.PackExporter;
import com.enc.packcod.pack.PackLibrary;
import com.enc.packcod.pack.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static com.enc.packcod.ui.Theme.*;

/**
 * pack.cod main screen.
 *
 * Left: categories/folders and the brush pack. Middle: a grid (or board) of everything in
 * the chosen category. Click anything and the middle turns into that item's page: every
 * pack's version of it, big, side by side, with arrows to step to the next item.
 * On wide windows an inspector sits on the right; on small windows it is hidden and the
 * item page does its job.
 *
 * Drawing code records clickable rectangles each frame, so the layout lives in one place
 * and adapts to whatever size the window is.
 */
public final class MixerScreen extends Screen {

    private static final int M = 4;
    private static final int TOP_H = 20;
    private static final int BOTTOM_H = 24;
    private static final int CELL = 20;
    private static final int ROW = 11;
    private static final int OPT_ROW = 20;
    private static final int WIDE = 560;

    private enum View { GRID, BOARD, ITEM }

    private final Screen parent;
    private final MixProject project = MixProject.load();
    private final TextureCache cache = new TextureCache();
    private final Preview preview = new Preview(cache);
    private CompletableFuture<PackLibrary> loading;
    private final java.util.Map<String, String> pendingPicks = new java.util.HashMap<>();
    private PackLibrary lib;

    private String section = "cat:Swords";
    /** Fold-out groups that are open in the left list. */
    private final java.util.Set<String> expanded = new java.util.HashSet<>(java.util.Set.of("PvP", "Kits", "Kits/Sub tiers"));

    /** One line in the left list: a group (folds open) and/or a category to show. */
    private record Row(int depth, String label, String section, String group) {}
    private String query = "";
    private List<String> visible = List.of();
    private String selected;
    private AssetSource brush;
    private boolean brushOnly;
    private View view = View.GRID;
    private View backTo = View.GRID;
    private Preview.Mode previewMode = Preview.Mode.FLAT;

    /** The mix changed since it was last put in game (used by Live). */
    private boolean dirty;
    private final java.util.Random random = new java.util.Random();

    private int secScroll, srcScroll, gridScroll, itemScroll, optScroll, colScroll;
    private String status = "";
    private int statusColor = DIM;
    private long statusUntil;

    private EditBox search;
    private EditBox nameBox;

    private final List<Hit> hits = new ArrayList<>();
    private final List<Wheel> wheels = new ArrayList<>();
    private String tooltip;

    private interface Click { void on(int button, boolean shift); }
    private record Hit(int x, int y, int w, int h, Click action) {
        boolean in(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }
    private interface Scroll { void by(int rows); }
    private record Wheel(int x, int y, int w, int h, Scroll action) {
        boolean in(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }

    public MixerScreen(Screen parent) {
        super(Component.literal("pack.cod"));
        this.parent = parent;
        this.loading = CompletableFuture.supplyAsync(() -> PackLibrary.scan(Minecraft.getInstance()));
    }

    // ------------------------------------------------------------------ layout

    private boolean wide() { return width >= WIDE; }
    private int leftW() { return clamp(width * 22 / 100, 92, 136); }
    private int rightW() { return wide() ? clamp(width * 24 / 100, 124, 160) : 0; }

    @Override
    protected void init() {
        preview.setBackgrounds(Backgrounds.list());
        int sx = M + 18 + font.width("pack.cod") + font.width("v" + PackCodClient.VERSION) + 16;
        int sw = clamp(width - sx - 170, 60, 180);
        search = new EditBox(font, sx + 4, 6, sw - 8, 10, Component.literal("Search"));
        search.setBordered(false);
        search.setHint(Component.literal("search"));
        search.setValue(query);
        search.setResponder(s -> {
            query = s.trim().toLowerCase(Locale.ROOT);
            gridScroll = 0;
            if (view == View.ITEM) view = backTo;
            refilter();
        });
        addRenderableWidget(search);

        int nw = clamp(width / 4, 70, 140);
        nameBox = new EditBox(font, M + 32, height - BOTTOM_H + 8, nw, 10, Component.literal("Pack name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(48);
        nameBox.setValue(project.name);
        nameBox.setResponder(s -> project.name = s);
        addRenderableWidget(nameBox);
    }

    private void onLoaded(PackLibrary l) {
        lib = l;
        preview.setLibrary(lib);
        pendingPicks.forEach((k, id) -> {
            AssetSource s = lib.source(id);
            if (s != null) project.set(k, s);
        });
        pendingPicks.clear();
        project.prune(lib);
        if (brush == null || lib.source(brush.id()) == null) brush = lib.sources().size() > 1 ? lib.sources().get(1) : lib.vanilla();
        else brush = lib.source(brush.id());
        if (keysFor(section).isEmpty()) {
            if (!lib.categories().isEmpty()) section = "cat:" + lib.categories().keySet().iterator().next();
            else if (!lib.sections().isEmpty()) section = lib.sections().keySet().iterator().next();
        }
        refilter();
        if (lib.sources().size() == 1) flash("No packs found. Put some in your resourcepacks folder.", BAD);
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(0, "\u2B07 Browse packs", "browse", null));
        for (com.enc.packcod.pack.Categories.Node n : com.enc.packcod.pack.Categories.TREE) addRow(rows, n, 0, "");
        rows.add(new Row(0, "Folders", null, "Folders"));
        if (expanded.contains("Folders")) {
            for (String sec : lib.sections().keySet()) rows.add(new Row(1, sectionTitle(sec), sec, null));
        }
        return rows;
    }

    private void addRow(List<Row> rows, com.enc.packcod.pack.Categories.Node n, int depth, String parent) {
        String sec = n.category() == null ? null : "cat:" + n.category();
        if (!n.isGroup()) {
            if (sec != null && !keysFor(sec).isEmpty()) rows.add(new Row(depth, n.label(), sec, null));
            return;
        }
        String key = parent.isEmpty() ? n.label() : parent + "/" + n.label();
        rows.add(new Row(depth, n.label(), sec != null && !keysFor(sec).isEmpty() ? sec : null, key));
        if (expanded.contains(key)) for (var c : n.children()) addRow(rows, c, depth + 1, key);
    }

    private List<String> keysFor(String sec) {
        if (lib == null) return List.of();
        if (sec.startsWith("cat:")) return lib.categories().getOrDefault(sec.substring(4), List.of());
        return lib.sections().getOrDefault(sec, List.of());
    }

    private static String sectionTitle(String sec) {
        if (sec.startsWith("cat:Kit: ")) return sec.substring(9) + " kit";
        return sec.startsWith("cat:") ? sec.substring(4) : sec.replace('_', ' ');
    }

    private void refilter() {
        if (lib == null) return;
        // Files that belong together (a block's sides, all the hearts...) show up as one entry.
        List<String> out = new ArrayList<>();
        java.util.Map<String, Integer> setIndex = new java.util.HashMap<>();
        for (String key : keysFor(section)) {
            if (brushOnly && brush != null && !brush.isVanilla() && !packHas(brush, key)) continue;
            if (!query.isEmpty() && !key.toLowerCase(Locale.ROOT).contains(query)) continue;
            String set = com.enc.packcod.pack.Categories.setOf(key);
            if (set == null) {
                out.add(key);
            } else if (!setIndex.containsKey(set)) {
                setIndex.put(set, out.size());
                out.add(key);
            } else if (com.enc.packcod.pack.Categories.isPreferredRep(key, set)) {
                out.set(setIndex.get(set), key);
            }
        }
        visible = out;
    }

    // ------------------------------------------------------------------ actions

    private void select(String key) {
        select(key, true);
    }

    /** resetView: open on the item's natural view (armor on a player, blocks as a block...). */
    private void select(String key, boolean resetView) {
        selected = key;
        optScroll = 0;
        List<Preview.Mode> modes = Preview.modesFor(key);
        if (modes.isEmpty()) previewMode = Preview.Mode.FLAT;
        else if (resetView || !modes.contains(previewMode))
            previewMode = modes.contains(Preview.Mode.BLOCK) ? Preview.Mode.BLOCK
                    : modes.contains(Preview.Mode.MOB) ? Preview.Mode.MOB : modes.get(0);
    }

    /** Opens the pixel editor on whichever version of this texture is in use. */
    private void openEditor(String key) {
        AssetSource src = current(key);
        byte[] png = src.read(key);
        if (png == null) {
            src = lib.vanilla();
            png = src.read(key);
        }
        byte[] meta = src.read(key + ".mcmeta");
        minecraft.setScreen(new PixelEditorScreen(this, key, src.displayName(), png, meta));
    }

    private void openBrowse() {
        project.save();
        minecraft.setScreen(new BrowseScreen(this, minecraft.getResourcePackDirectory()));
    }

    /** Re-reads the resourcepacks folder (after Browse installed something). */
    void rescan() {
        if (lib != null) lib.close();
        lib = null;
        cache.clear();
        loading = CompletableFuture.supplyAsync(() -> PackLibrary.scan(Minecraft.getInstance()));
    }

    /** Called by the editor after it saves: rescan packs and switch this item to the drawing. */
    void onDrawingSaved(String key) {
        pendingPicks.put(key, PixelEditorScreen.EDITS_PACK);
        if (lib != null) lib.close();
        lib = null;
        cache.clear();
        loading = CompletableFuture.supplyAsync(() -> PackLibrary.scan(Minecraft.getInstance()));
    }

    /** Opens the item page: every pack's version of this one thing. */
    private void openItem(String key) {
        openItem(key, true);
    }

    private void openItem(String key, boolean resetView) {
        select(key, resetView);
        if (view != View.ITEM) backTo = view;
        view = View.ITEM;
        itemScroll = 0;
    }

    private void stepItem(int dir) {
        int i = visible.indexOf(selected);
        if (i < 0 || visible.isEmpty()) return;
        openItem(visible.get(Math.floorMod(i + dir, visible.size())), false);
    }

    private void pick(String key, AssetSource src) {
        dirty = true;
        String set = com.enc.packcod.pack.Categories.setOf(key);
        if (set == null) {
            project.set(key, src);
            return;
        }
        // A set (all sides of a block, every heart, the crosshair parts) always comes from one pack;
        // anything that pack doesn't change goes back to default.
        for (String k : lib.setMembers(set)) project.set(k, src.has(k) ? src : lib.vanilla());
    }

    private void play(String key, AssetSource src) {
        if (!SoundPreview.play(src.id() + "|" + key, src.read(key))) flash("Couldn't play that sound", BAD);
    }

    private boolean isPlaying(String key, AssetSource src) {
        return (src.id() + "|" + key).equals(SoundPreview.playing());
    }

    private void fillVisible(AssetSource src) {
        int n = 0;
        for (String key : visible) {
            if (src.isVanilla() || packHas(src, key)) {
                pick(key, src);
                n++;
            }
        }
        flash(src.isVanilla() ? "Reset " + n : n + " now from " + src.displayName(), GOOD);
    }

    private void fillEverything(AssetSource src) {
        if (src.isVanilla()) {
            project.picks.clear();
            dirty = true;
            flash("Everything back to default", GOOD);
            return;
        }
        for (String key : src.entries()) pick(key, src);
        flash("All " + src.entries().size() + " files from " + src.displayName(), GOOD);
    }

    /** Surprise me: a random pack for everything shown. Click again to re-roll. */
    private void randomize() {
        int n = 0;
        for (String key : visible) {
            List<AssetSource> opts = new ArrayList<>();
            for (AssetSource s : holdersFor(key)) if (!s.isVanilla()) opts.add(s);
            if (opts.isEmpty()) continue;
            pick(key, opts.get(random.nextInt(opts.size())));
            n++;
        }
        flash(n == 0 ? "No packs change anything here" : "Rolled " + n + " things. Click again to re-roll.", n == 0 ? BAD : GOOD);
    }

    /** Called by the mixes screen after it loaded or replaced the mix. */
    void mixChanged() {
        dirty = true;
        if (lib != null) {
            project.prune(lib);
            refilter();
        }
    }

    private void export() {
        if (lib == null) return;
        if (project.picks.isEmpty()) {
            flash("Nothing to export yet. Pick something first.", BAD);
            return;
        }
        project.save();
        // name, name colour and icon are set on the export screen
        minecraft.setScreen(new ExportScreen(this, lib, project));
    }

    /** Called by the export screen when the zip has been written. */
    void exported(PackExporter.Result r) {
        flash("Saved " + r.file().getFileName() + "  (" + r.assets() + " files)", GOOD);
    }

    /** Exports the mix as "pack.cod preview", switches it on and reloads, so you can walk around in it. */
    private void tryInGame() {
        if (lib == null || project.picks.isEmpty()) {
            flash("Pick something first", BAD);
            return;
        }
        if (applyMix()) {
            view = View.GRID;
            onClose();
        }
    }

    /**
     * Writes the mix as "pack.cod preview" and turns it on (an empty mix turns it off).
     * Shared by Try in game and Live.
     */
    private boolean applyMix() {
        project.save();
        try {
            var repo = minecraft.getResourcePackRepository();
            List<String> on = new ArrayList<>(repo.getSelectedIds());
            on.removeIf(s -> s.startsWith("file/pack.cod preview"));
            if (!project.picks.isEmpty()) {
                PackExporter.Result r = PackExporter.export(lib, project, minecraft.getResourcePackDirectory(), "pack.cod preview");
                repo.reload();
                on.add("file/" + r.file().getFileName().toString());
            }
            repo.setSelected(on);
            minecraft.options.updateResourcePacks(repo);
            dirty = false;
            return true;
        } catch (Exception e) {
            flash("Couldn't apply: " + e.getMessage(), BAD);
            return false;
        }
    }

    private void flash(String msg, int color) {
        status = msg;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 6000;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        // All custom UI is drawn here so it always sits underneath the text boxes.
        hits.clear();
        wheels.clear();
        tooltip = null;
        cache.beginFrame();
        if (lib == null && loading.isDone()) onLoaded(loading.join());

        g.fill(0, 0, width, height, BG);
        drawTopBar(g, mx, my);
        drawBottomBar(g, mx, my);

        int bodyY = TOP_H + M;
        int bodyH = height - BOTTOM_H - M - bodyY;
        if (lib == null) {
            String s = "Reading your resource packs...";
            g.drawString(font, s, (width - font.width(s)) / 2, height / 2 - 4, DIM, false);
            return;
        }
        int lw = leftW(), rw = rightW();
        int cx = M + lw + M;
        drawLeft(g, M, bodyY, lw, bodyH, mx, my);
        if (view == View.ITEM && selected != null) {
            // The item page gets the whole width to the right of the list.
            drawItemPage(g, cx, bodyY, width - cx - M, bodyH, mx, my);
            return;
        }
        int cw = width - cx - M - (rw > 0 ? rw + M : 0);
        if (view == View.BOARD) drawBoard(g, cx, bodyY, cw, bodyH, mx, my);
        else drawGrid(g, cx, bodyY, cw, bodyH, mx, my);
        if (rw > 0) drawInspector(g, width - M - rw, bodyY, rw, bodyH, mx, my);
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
            outline(g, x, y, w, 13, ACCENT_DARK);
            g.drawString(font, tooltip, x + 4, y + 3, TEXT, false);
        }
    }

    private void drawTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, TOP_H, PANEL);
        g.fill(0, TOP_H - 1, width, TOP_H, LINE);
        if (lib != null) {
            TextureCache.Thumb cod = cache.get(lib.vanilla(), "textures/item/cod.png");
            if (cod != null) blitThumb(g, cod, M, 2, 16);
        }
        int tx = M + 19;
        g.drawString(font, "pack", tx, 6, TEXT, false);
        g.drawString(font, ".cod", tx + font.width("pack"), 6, ACCENT, false);
        // Small version tag, kept out of the way.
        String v = "v" + PackCodClient.VERSION;
        g.drawString(font, v, tx + font.width("pack.cod") + 4, 6, FAINT, false);

        slot(g, search.getX() - 4, 2, search.getWidth() + 8, 15);

        // Look: colours and style of the whole UI
        String lk = "\u2726 Look";
        int lw = font.width(lk) + 10;
        int lx = width - M - lw;
        button(g, lx, 2, lw, 15, lk, mx, my, false, true, (b, s) -> minecraft.setScreen(new LookScreen(this)));
        if (in(mx, my, lx, 2, lw, 15)) tooltip = "Change pack.cod's colours and style";
        String bl = "Browse";
        int bw2 = font.width(bl) + 10;
        lx -= bw2 + 3;
        button(g, lx, 2, bw2, 15, bl, mx, my, true, true, (b, s) -> openBrowse());
        if (in(mx, my, lx, 2, bw2, 15)) tooltip = "The most downloaded packs on PacksMC";
        if (lib != null) {
            String right = (lib.sources().size() - 1) + " packs";
            int rx = lx - 6 - font.width(right);
            if (rx > search.getX() + search.getWidth() + 10) g.drawString(font, right, rx, 6, DIM, false);
        }
    }

    private void drawBottomBar(GuiGraphics g, int mx, int my) {
        int y = height - BOTTOM_H;
        g.fill(0, y, width, height, PANEL);
        g.fill(0, y, width, y + 1, LINE);
        Theme.label(g, font, "Name", M, y + 8);
        slot(g, nameBox.getX() - 4, y + 4, nameBox.getWidth() + 8, 15);

        boolean any = lib != null && !project.picks.isEmpty();
        int bw = font.width("EXPORT") + 16, bx = width - M - bw;
        button(g, bx, y + 4, bw, 16, "EXPORT", mx, my, true, any, (b, s) -> export());
        String tl = width < 480 ? "Try" : "Try in game";
        int tw = font.width(tl) + 12;
        bx -= tw + 4;
        button(g, bx, y + 4, tw, 16, tl, mx, my, false, any, (b, s) -> tryInGame());
        if (any && in(mx, my, bx, y + 4, tw, 16)) tooltip = "Turn the mix on now and look at it in the world";
        boolean live = Settings.get().live;
        String ll = (live ? "● " : "○ ") + "Live";
        int lw = font.width(ll) + 10;
        bx -= lw + 4;
        button(g, bx, y + 4, lw, 16, ll, mx, my, false, lib != null, (b2, s2) -> {
            Settings st = Settings.get();
            st.live = !st.live;
            st.save();
            flash(st.live ? "Live on: leaving pack.cod puts your mix in game" : "Live off", st.live ? GOOD : DIM);
        });
        if (live) g.fill(bx + 1, y + 18, bx + lw - 1, y + 19, GOOD);
        if (in(mx, my, bx, y + 4, lw, 16)) tooltip = "Live: your mix turns on by itself when you leave pack.cod";
        int mw = font.width("Mixes") + 10;
        bx -= mw + 4;
        button(g, bx, y + 4, mw, 16, "Mixes", mx, my, false, lib != null,
                (b2, s2) -> { project.save(); minecraft.setScreen(new MixesScreen(this, project, lib)); });
        if (in(mx, my, bx, y + 4, mw, 16)) tooltip = "Save, switch and share mixes";
        int cw = font.width("Credits") + 10;
        bx -= cw + 4;
        if (width >= 420) {
            button(g, bx, y + 4, cw, 16, "Credits", mx, my, false, true, (b2, s2) -> minecraft.setScreen(new CreditsScreen(this)));
        } else {
            bx += cw + 4;
        }

        int sx = nameBox.getX() + nameBox.getWidth() + 10;
        int room = bx - sx - 6;
        if (room < 20) return;
        if (System.currentTimeMillis() < statusUntil) {
            g.drawString(font, fit(font, status, room), sx, y + 8, statusColor, false);
        } else if (lib != null) {
            int n = project.picks.size();
            String s = n == 0 ? "Nothing changed yet" : n + " changed";
            g.drawString(font, fit(font, s, room), sx, y + 8, n == 0 ? FAINT : DIM, false);
        }
    }

    private void drawLeft(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        panel(g, x, y, w, h);

        List<Row> names = buildRows();
        int secTop = y + 3;
        int secH = (h - 3) * 55 / 100;
        int secRows = Math.max(1, secH / ROW);
        secScroll = clamp(secScroll, 0, Math.max(0, names.size() - secRows));
        for (int i = 0; i < secRows && i + secScroll < names.size(); i++) {
            Row r = names.get(i + secScroll);
            int ry = secTop + i * ROW;
            int ix = x + 6 + r.depth() * 8;
            boolean on = r.section() != null && r.section().equals(section);
            boolean hov = in(mx, my, x + 1, ry, w - 2, ROW);
            if (on) {
                g.fill(x + 1, ry, x + w - 1, ry + ROW, PANEL_HI);
                g.fill(x + 1, ry, x + 3, ry + ROW, ACCENT);
            }
            if (r.group() != null) {
                boolean open = expanded.contains(r.group());
                g.drawString(font, open ? "-" : "+", ix, ry + 2, ACCENT, false);
                ix += 8;
            }
            boolean browseRow = "browse".equals(r.section());
            String count = browseRow ? "top 50" : r.section() != null ? String.valueOf(keysFor(r.section()).size()) : "";
            int col = browseRow ? (hov ? TEXT : ACCENT) : on || hov ? TEXT : r.group() != null ? mix(DIM, TEXT, 0.8f) : r.depth() > 0 ? DIM : mix(DIM, TEXT, 0.5f);
            g.drawString(font, fit(font, r.label(), x + w - 8 - font.width(count) - ix), ix, ry + 2, col, false);
            if (!count.isEmpty()) g.drawString(font, count, x + w - 4 - font.width(count), ry + 2, FAINT, false);
            hits.add(new Hit(x + 1, ry, w - 2, ROW, (b, sh) -> {
                if (r.group() != null) {
                    if (!expanded.remove(r.group())) expanded.add(r.group());
                }
                if ("browse".equals(r.section())) {
                    openBrowse();
                    return;
                }
                if (r.section() != null) {
                    section = r.section();
                    gridScroll = 0;
                    if (view == View.ITEM) view = backTo;
                    refilter();
                }
            }));
        }
        wheels.add(new Wheel(x, secTop, w, secH, d -> secScroll += d));

        int head = secTop + secH + 3;
        g.fill(x + 4, head, x + w - 4, head + 1, LINE);
        Theme.label(g, font, "Brush", x + 5, head + 4);
        // resolution filter: one chip that cycles All / 16x / 32x / 64x+
        Settings st = Settings.get();
        String res = st.resFilter == 0 ? "All" : st.resFilter >= 64 ? "64x+" : st.resFilter + "x";
        int rw = font.width(res) + 8;
        int rxx = x + w - 4 - rw;
        boolean rh = in(mx, my, rxx, head + 2, rw, 11);
        if (st.resFilter != 0) g.fill(rxx, head + 2, rxx + rw, head + 13, ACCENT_DARK);
        else slot(g, rxx, head + 2, rw, 11);
        g.drawString(font, res, rxx + 4, head + 4, rh || st.resFilter != 0 ? TEXT : DIM, false);
        hits.add(new Hit(rxx, head + 2, rw, 11, (b, sh) -> {
            st.resFilter = switch (st.resFilter) { case 0 -> 16; case 16 -> 32; case 32 -> 64; default -> 0; };
            st.save();
            itemScroll = 0;
            srcScroll = 0;
        }));
        if (rh) tooltip = "Only show packs of this resolution  •  click to change";
        int srcTop = head + 14;
        int srcH = y + h - 20 - srcTop;
        int srcRows = Math.max(1, srcH / ROW);
        List<AssetSource> srcs = lib.shown(lib.sources());
        srcScroll = clamp(srcScroll, 0, Math.max(0, srcs.size() - srcRows));
        for (int i = 0; i < srcRows && i + srcScroll < srcs.size(); i++) {
            AssetSource s = srcs.get(i + srcScroll);
            int ry = srcTop + i * ROW;
            boolean on = s == brush;
            boolean hov = in(mx, my, x + 1, ry, w - 2, ROW);
            if (on) {
                g.fill(x + 1, ry, x + w - 1, ry + ROW, PANEL_HI);
                g.fill(x + 1, ry, x + 3, ry + ROW, ACCENT);
            }
            g.fill(x + 6, ry + 2, x + 13, ry + 9, s.color());
            boolean fav = st.favorites.contains(s.id());
            int starX = x + w - 11;
            boolean starHov = !s.isVanilla() && in(mx, my, starX - 1, ry, 10, ROW);
            g.drawString(font, fit(font, s.displayName(), w - 30), x + 16, ry + 2, on || hov ? TEXT : DIM, false);
            if (!s.isVanilla() && (fav || hov)) {
                g.drawString(font, fav ? "★" : "☆", starX, ry + 2, fav ? ACCENT : starHov ? TEXT : FAINT, false);
            }
            hits.add(new Hit(x + 1, ry, w - 2, ROW, (b, sh) -> {
                brush = s;
                if (brushOnly) refilter();
            }));
            if (!s.isVanilla()) hits.add(new Hit(starX - 1, ry, 10, ROW, (b, sh) -> {
                if (!st.favorites.remove(s.id())) st.favorites.add(s.id());
                st.save();
            }));
            int res2 = lib.resolution(s);
            if (starHov) tooltip = fav ? "Unstar (starred packs show first)" : "Star this pack (starred packs show first)";
            else if (hov) tooltip = s.displayName() + "  •  " + (res2 > 0 ? res2 + "x  •  " : "") + s.entries().size() + " files";
        }
        wheels.add(new Wheel(x, srcTop, w, srcH, d -> srcScroll += d));

        String fill = brush != null && brush.isVanilla() ? "Reset all" : "Use brush for all";
        button(g, x + 3, y + h - 17, w - 6, 14, fit(font, fill, w - 12), mx, my, false, brush != null,
                (b, s) -> fillEverything(brush));
    }

    /** Two-row header shared by Grid and Board: title + view switch, then the tool buttons. */
    private int drawHeader(GuiGraphics g, int x, int y, int w, int mx, int my) {
        String title = sectionTitle(section);
        String count = visible.size() + (query.isEmpty() && !brushOnly ? "" : " shown");

        // view switch, right aligned on row one
        String[] labels = {"Grid", "Board"};
        View[] views = {View.GRID, View.BOARD};
        int sw = 0;
        for (String l : labels) sw += font.width(l) + 8;
        int vx = x + w - 4 - sw;
        for (int i = 0; i < 2; i++) {
            int lw = font.width(labels[i]) + 8;
            boolean on = view == views[i];
            boolean hov = in(mx, my, vx, y + 3, lw, 13);
            if (on) g.fill(vx, y + 3, vx + lw, y + 16, ACCENT);
            else slot(g, vx, y + 3, lw, 13);
            g.drawString(font, labels[i], vx + 4, y + 6, on ? ON_ACCENT : hov ? TEXT : DIM, false);
            final View v = views[i];
            hits.add(new Hit(vx, y + 3, lw, 13, (b, sh) -> { view = v; gridScroll = 0; colScroll = 0; }));
            vx += lw;
        }
        int titleRoom = w - 14 - sw;
        String t = fit(font, title, titleRoom - font.width(count) - 6);
        g.drawString(font, t, x + 6, y + 6, TEXT, false);
        g.drawString(font, count, x + 6 + font.width(t) + 5, y + 6, FAINT, false);

        // tools on row two
        int bx = x + 5, by = y + 19;
        boolean realBrush = brush != null && !brush.isVanilla();
        if (realBrush) {
            String lbl = (brushOnly ? "■ " : "□ ") + "Only brush";
            int tw = font.width(lbl) + 8;
            button(g, bx, by, tw, 13, lbl, mx, my, false, true, (b, s) -> {
                brushOnly = !brushOnly;
                gridScroll = 0;
                refilter();
            });
            if (in(mx, my, bx, by, tw, 13)) tooltip = "Only show what " + brush.displayName() + " changes";
            bx += tw + 3;
            int fw = font.width("Fill") + 14;
            button(g, bx, by, fw, 13, "Fill", mx, my, false, !visible.isEmpty(), (b, s) -> fillVisible(brush));
            g.fill(bx + fw - 6, by + 3, bx + fw - 3, by + 10, brush.color());
            if (in(mx, my, bx, by, fw, 13)) tooltip = "Use " + brush.displayName() + " for everything shown";
            bx += fw + 3;
        }
        int rw = font.width("Reset") + 8;
        button(g, bx, by, rw, 13, "Reset", mx, my, false, !visible.isEmpty(), (b, s) -> fillVisible(lib.vanilla()));
        bx += rw + 3;
        int dw = font.width("Surprise me") + 8;
        if (bx + dw < x + w - 4) {
            button(g, bx, by, dw, 13, "Surprise me", mx, my, false, !visible.isEmpty(), (b, s) -> randomize());
            if (in(mx, my, bx, by, dw, 13)) tooltip = "A random pack for everything shown  •  click again to re-roll";
        }
        g.fill(x + 1, y + 35, x + w - 1, y + 36, LINE);
        return y + 39;
    }

    private void drawGrid(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        panel(g, x, y, w, h);
        int gy = drawHeader(g, x, y, w, mx, my);
        int gx = x + 4, gw = w - 10, gh = y + h - 3 - gy;
        int cols = Math.max(1, gw / CELL);
        int rows = Math.max(1, gh / CELL);
        int totalRows = (visible.size() + cols - 1) / cols;
        gridScroll = clamp(gridScroll, 0, Math.max(0, totalRows - rows));
        int ox = gx + (gw - cols * CELL) / 2;
        wheels.add(new Wheel(x, gy, w, gh, d -> gridScroll += d));

        if (visible.isEmpty()) {
            String s = !query.isEmpty() ? "Nothing matches \"" + query + "\"" : brushOnly ? "Brush pack changes nothing here" : "Empty";
            g.drawString(font, fit(font, s, w - 10), x + 6, gy + 10, FAINT, false);
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (r + gridScroll) * cols + c;
                if (idx >= visible.size()) break;
                drawCell(g, visible.get(idx), ox + c * CELL, gy + r * CELL, mx, my);
            }
        }
        scrollbar(g, x + w - 4, gy, gh, gridScroll, rows, totalRows);
    }

    private void drawCell(GuiGraphics g, String key, int x, int y, int mx, int my) {
        AssetSource src = current(key);
        boolean hov = in(mx, my, x, y, CELL - 1, CELL - 1);
        slot(g, x, y, CELL - 1, CELL - 1);
        TextureCache.animate = hov;
        drawIcon(g, key, src, x + 1, y + 1, 16);
        TextureCache.animate = false;
        if (!src.isVanilla()) g.fill(x + 1, y + CELL - 4, x + CELL - 2, y + CELL - 2, src.color());
        if (key.equals(selected)) outline(g, x - 1, y - 1, CELL + 1, CELL + 1, ACCENT);
        else if (hov) outline(g, x, y, CELL - 1, CELL - 1, HOVER);
        if (hov) tooltip = prettyName(key) + "  •  " + holdersFor(key).size() + " versions  •  click to see them";

        hits.add(new Hit(x, y, CELL - 1, CELL - 1, (button, shift) -> {
            if (button == 1) {
                pick(key, lib.vanilla());
            } else if (shift && brush != null) {
                if (packHas(brush, key)) pick(key, brush);
                else flash(brush.displayName() + " doesn't have " + prettyName(key), BAD);
            } else {
                openItem(key);
                if (Assets.isSound(key)) play(key, current(key));
            }
        }));
    }

    /**
     * Board: every pack against everything in the category. When there are more packs than
     * items (like 26 packs vs 7 swords) it flips, so each pack is a row and you read across.
     */
    private void drawBoard(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        panel(g, x, y, w, h);
        int top = drawHeader(g, x, y, w, mx, my);

        List<AssetSource> packs = new ArrayList<>();
        for (AssetSource s : lib.shown(lib.sources())) {
            if (s.isVanilla()) { packs.add(s); continue; }
            for (String k : visible) if (packHas(s, k)) { packs.add(s); break; }
        }
        if (visible.isEmpty()) {
            g.drawString(font, "Nothing here", x + 6, top + 10, FAINT, false);
            return;
        }
        boolean flip = packs.size() > visible.size();
        int nRows = flip ? packs.size() : visible.size();
        int nCols = flip ? visible.size() : packs.size();

        int col = CELL;
        int nameW = clamp(w * 30 / 100, 50, 110);
        int gx = x + 4;
        int headH = flip ? 20 : 14;
        int fitCols = Math.max(1, (w - 10 - nameW) / col);
        colScroll = clamp(colScroll, 0, Math.max(0, nCols - fitCols));
        int bodyTop = top + headH;
        int rows = Math.max(1, (y + h - 3 - bodyTop) / col);
        gridScroll = clamp(gridScroll, 0, Math.max(0, nRows - rows));
        wheels.add(new Wheel(x, bodyTop, w, y + h - bodyTop, d -> gridScroll += d));

        // column heads
        for (int c = 0; c < fitCols && c + colScroll < nCols; c++) {
            int cx = gx + nameW + c * col;
            if (flip) {
                String key = visible.get(c + colScroll);
                drawIcon(g, key, lib.vanilla(), cx + 1, top, 16);
                if (in(mx, my, cx, top, col - 1, 18)) tooltip = prettyName(key) + "  •  click to see all versions";
                hits.add(new Hit(cx, top, col - 1, 18, (b, sh) -> openItem(key)));
            } else {
                AssetSource s = packs.get(c + colScroll);
                g.fill(cx + 3, top + 2, cx + col - 4, top + 8, s.color());
                if (in(mx, my, cx, top, col - 1, 12)) tooltip = s.displayName() + "  •  click: make brush";
                hits.add(new Hit(cx, top, col - 1, 12, (b, sh) -> brush = s));
            }
        }
        if (colScroll > 0) button(g, gx, top + 1, 12, 12, "<", mx, my, false, true, (b, s) -> colScroll -= fitCols);
        if (colScroll + fitCols < nCols) button(g, gx + 14, top + 1, 12, 12, ">", mx, my, false, true, (b, s) -> colScroll += fitCols);

        for (int r = 0; r < rows && r + gridScroll < nRows; r++) {
            int ry = bodyTop + r * col;
            int ri = r + gridScroll;
            // row label
            if (flip) {
                AssetSource s = packs.get(ri);
                g.fill(gx, ry + 6, gx + 5, ry + 11, s.color());
                g.drawString(font, fit(font, s.displayName(), nameW - 12), gx + 8, ry + 5, s == brush ? ACCENT : DIM, false);
                hits.add(new Hit(gx, ry, nameW - 4, col - 1, (b, sh) -> brush = s));
                if (in(mx, my, gx, ry, nameW - 4, col - 1)) tooltip = s.displayName() + "  •  click: make brush";
            } else {
                String key = visible.get(ri);
                g.drawString(font, fit(font, prettyName(key), nameW - 6), gx, ry + 5,
                        key.equals(selected) ? TEXT : DIM, false);
                hits.add(new Hit(gx, ry, nameW - 4, col - 1, (b, sh) -> openItem(key)));
            }
            for (int c = 0; c < fitCols && c + colScroll < nCols; c++) {
                String key = flip ? visible.get(c + colScroll) : visible.get(ri);
                AssetSource s = flip ? packs.get(ri) : packs.get(c + colScroll);
                int cx = gx + nameW + c * col;
                if (!packHas(s, key)) {
                    g.fill(cx + 8, ry + 9, cx + 11, ry + 10, LINE);
                    continue;
                }
                slot(g, cx, ry, col - 2, col - 2);
                boolean hov = in(mx, my, cx, ry, col - 2, col - 2);
                TextureCache.animate = hov;
                drawIcon(g, key, s, cx + 1, ry + 1, col - 4);
                TextureCache.animate = false;
                boolean on = s == current(key);
                if (on) outline(g, cx - 1, ry - 1, col, col, ACCENT);
                else if (hov) outline(g, cx, ry, col - 2, col - 2, HOVER);
                if (hov) tooltip = prettyName(key) + "  •  " + s.displayName();
                hits.add(new Hit(cx, ry, col - 2, col - 2, (b, sh) -> {
                    select(key);
                    if (Assets.isSound(key) && !sh) play(key, s);
                    else pick(key, s);
                }));
            }
        }
        scrollbar(g, x + w - 4, bodyTop, y + h - 3 - bodyTop, gridScroll, rows, nRows);
    }

    /** The item page: one thing, every pack's version of it, big and side by side. */
    private void drawItemPage(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        panel(g, x, y, w, h);
        List<AssetSource> holders = holdersFor(selected);
        AssetSource cur = current(selected);

        // header row: back, prev, name, next ... view tabs
        int hx = x + 4;
        button(g, hx, y + 3, 34, 14, "← Back", mx, my, false, true, (b, s) -> view = backTo);
        hx += 38;
        int idx = visible.indexOf(selected);
        boolean canStep = idx >= 0 && visible.size() > 1;
        button(g, hx, y + 3, 14, 14, "<", mx, my, false, canStep, (b, s) -> stepItem(-1));
        hx += 16;
        int tabsW = modeTabsWidth();
        int nameRoom = w - (hx - x) - 22 - tabsW - 8;
        String nm = fit(font, prettyName(selected), Math.max(20, nameRoom));
        g.drawString(font, nm, hx + 3, y + 6, TEXT, false);
        hx += font.width(nm) + 7;
        button(g, hx, y + 3, 14, 14, ">", mx, my, false, canStep, (b, s) -> stepItem(1));
        int drawX = x + w - 4;
        if (Assets.isTexture(selected)) {
            int dw = font.width("\u270E Draw") + 10;
            drawX -= dw;
            button(g, drawX, y + 3, dw, 14, "\u270E Draw", mx, my, true, true, (b, s) -> openEditor(selected));
            if (in(mx, my, drawX, y + 3, dw, 14)) tooltip = "Draw your own version in the pixel editor";
            drawX -= 4;
        }
        if (tabsW > 0 && drawX - tabsW > hx + 18) modeTabs(g, drawX - tabsW, y + 3, mx, my);

        String sub = holders.size() + " versions" + (idx >= 0 ? "  •  " + (idx + 1) + " of " + visible.size() : "")
                + "  •  click one to use it" + (Assets.isSound(selected) ? " (click plays, shift+click uses)" : "")
                + (previewMode == Preview.Mode.INGAME && !preview.hasBackgrounds()
                    ? "  •  type /packcod in a world to use real views as backgrounds" : "");
        g.drawString(font, fit(font, sub, w - 10), x + 5, y + 21, FAINT, false);
        g.fill(x + 1, y + 32, x + w - 1, y + 33, LINE);

        // tiles
        // Size tiles so several fit across: at least 4 wide screen-shaped ones, or 6 square ones.
        boolean screenShaped = Assets.isTexture(selected) && previewMode == Preview.Mode.INGAME;
        int avail = w - 14;
        int tileW = screenShaped ? clamp(avail / 4 - 8, 56, 96) : clamp(avail / 6 - 8, 36, 56);
        int tileH = screenShaped ? Math.round(tileW * 9 / 16f) : tileW;
        int tile = tileW;
        int pitch = tileW + 8;
        int rowH = tileH + 22;
        int gx = x + 6, gy = y + 38, gw = w - 14, gh = y + h - 3 - gy;
        int cols = Math.max(1, gw / pitch);
        int rows = Math.max(1, gh / rowH);
        int totalRows = (holders.size() + cols - 1) / cols;
        itemScroll = clamp(itemScroll, 0, Math.max(0, totalRows - rows));
        wheels.add(new Wheel(x, gy, w, gh, d -> itemScroll += d));
        int ox = gx + (gw - cols * pitch) / 2 + 4;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (r + itemScroll) * cols + c;
                if (i >= holders.size()) break;
                AssetSource s = holders.get(i);
                int tx = ox + c * pitch, ty = gy + r * rowH;
                boolean on = s == cur;
                boolean hov = in(mx, my, tx, ty, tileW, tileH + 20);
                slot(g, tx, ty, tileW, tileH);
                TextureCache.animate = hov;
                if (Assets.isTexture(selected)) preview.draw(g, previewMode, selected, s, tx + 2, ty + 2, tileW - 4, tileH - 4);
                else drawIcon(g, selected, s, tx + 4, ty + 4, tile - 8);
                TextureCache.animate = false;
                if (on) outline(g, tx - 2, ty - 2, tileW + 4, tileH + 4, ACCENT);
                else if (hov) outline(g, tx - 1, ty - 1, tileW + 2, tileH + 2, HOVER);
                g.fill(tx, ty + tileH + 4, tx + 4, ty + tileH + 8, s.color());
                g.drawString(font, fit(font, s.displayName(), tileW - 8), tx + 6, ty + tileH + 3, on || hov ? TEXT : DIM, false);
                if (on) g.drawString(font, "in use", tx + 6, ty + tileH + 12, ACCENT, false);
                hits.add(new Hit(tx, ty, tileW, tileH + 20, (b, sh) -> {
                    if (Assets.isSound(selected) && !sh) play(selected, s);
                    else pick(selected, s);
                }));
                if (hov) tooltip = s.displayName();
            }
        }
        scrollbar(g, x + w - 4, gy, gh, itemScroll, rows, totalRows);
    }

    /** Right-hand panel on wide windows: quick look at the selected item. */
    private void drawInspector(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        panel(g, x, y, w, h);
        Theme.label(g, font, "Inspect", x + 5, y + 5);

        if (selected == null) {
            int ty = y + 20;
            for (String line : new String[]{
                    "Click anything to see", "every pack's version.", "",
                    "Shift+click: use the", "brush pack.", "",
                    "Right click: default.", "",
                    "Export makes a normal", "pack. Friends don't", "need the mod."}) {
                g.drawString(font, fit(font, line, w - 10), x + 6, ty, DIM, false);
                ty += 10;
            }
            return;
        }

        AssetSource cur = current(selected);
        int ps = Math.min(64, w - 20);
        int px = x + (w - ps - 4) / 2, py = y + 16;
        slot(g, px, py, ps + 4, ps + 4);
        TextureCache.animate = in(mx, my, px, py, ps + 4, ps + 4);
        if (Assets.isTexture(selected) && previewMode == Preview.Mode.INGAME) {
            int sh = ps * 9 / 16;
            preview.draw(g, previewMode, selected, cur, px + 2, py + 2 + (ps - sh) / 2, ps, sh);
        } else if (Assets.isTexture(selected)) preview.draw(g, previewMode, selected, cur, px + 2, py + 2, ps);
        else drawIcon(g, selected, cur, px + 2, py + 2, ps);
        TextureCache.animate = false;
        if (Assets.isSound(selected)) hits.add(new Hit(px, py, ps + 4, ps + 4, (b, s) -> play(selected, current(selected))));

        int ty = py + ps + 8;
        if (Preview.modesFor(selected).size() > 1 && modeTabsWidth() <= w - 8) {
            modeTabs(g, x + (w - modeTabsWidth()) / 2, ty, mx, my);
            ty += 17;
        }
        String nm = fit(font, prettyName(selected), w - 10);
        g.drawString(font, nm, x + (w - font.width(nm)) / 2, ty + 1, TEXT, false);
        ty += 12;
        List<AssetSource> holders = holdersFor(selected);
        boolean canDraw = Assets.isTexture(selected);
        int half = canDraw ? (w - 10) * 2 / 3 : w - 8;
        button(g, x + 4, ty, half, 14, fit(font, "All " + holders.size() + " versions", half - 6), mx, my, true, true,
                (b, s) -> openItem(selected, false));
        if (canDraw) button(g, x + 6 + half, ty, w - 10 - half, 14, "Draw", mx, my, false, true, (b, s) -> openEditor(selected));
        ty += 18;

        int listH = y + h - 3 - ty;
        int rows = Math.max(1, listH / OPT_ROW);
        optScroll = clamp(optScroll, 0, Math.max(0, holders.size() - rows));
        wheels.add(new Wheel(x, ty, w, listH, d -> optScroll += d));
        boolean sound = Assets.isSound(selected);
        for (int i = 0; i < rows && i + optScroll < holders.size(); i++) {
            AssetSource s = holders.get(i + optScroll);
            int ry = ty + i * OPT_ROW;
            boolean on = s == cur;
            boolean hov = in(mx, my, x + 1, ry, w - 2, OPT_ROW - 1);
            if (on || hov) g.fill(x + 1, ry, x + w - 1, ry + OPT_ROW - 1, on ? PANEL_HI : HOVER_FILL);
            if (on) g.fill(x + 1, ry, x + 3, ry + OPT_ROW - 1, ACCENT);
            slot(g, x + 5, ry + 1, 18, 18);
            final String key = selected;
            if (sound) {
                boolean playing = isPlaying(key, s);
                boolean bh = in(mx, my, x + 5, ry + 1, 18, 18);
                drawPlayGlyph(g, x + 6, ry + 2, 16, playing ? ACCENT : bh ? TEXT : DIM, playing);
                hits.add(new Hit(x + 5, ry + 1, 18, 18, (b, sh) -> {
                    if (isPlaying(key, s)) SoundPreview.stop();
                    else play(key, s);
                }));
            } else {
                drawIcon(g, key, s, x + 6, ry + 2, 16);
            }
            g.fill(x + 26, ry + 7, x + 30, ry + 11, s.color());
            g.drawString(font, fit(font, s.displayName(), w - 38), x + 33, ry + 6, on || hov ? TEXT : DIM, false);
            hits.add(new Hit(x + 25, ry, w - 26, OPT_ROW - 1, (b, sh) -> pick(key, s)));
        }
    }

    private int modeTabsWidth() {
        if (selected == null || Preview.modesFor(selected).size() < 2) return 0;
        int w = 0;
        for (Preview.Mode m : Preview.modesFor(selected)) w += font.width(m.label) + 8;
        return w;
    }

    /** Flat / Block / World / Hand / Player switch. Drives the inspector and the item page. */
    private void modeTabs(GuiGraphics g, int x, int y, int mx, int my) {
        List<Preview.Mode> modes = Preview.modesFor(selected);
        if (modes.size() < 2) return;
        for (Preview.Mode m : modes) {
            int lw = font.width(m.label) + 8;
            boolean on = m == previewMode;
            boolean hov = in(mx, my, x, y, lw, 14);
            if (on) g.fill(x, y, x + lw, y + 14, ACCENT);
            else slot(g, x, y, lw, 14);
            g.drawString(font, m.label, x + 4, y + 3, on ? ON_ACCENT : hov ? TEXT : DIM, false);
            hits.add(new Hit(x, y, lw, 14, (b, sh) -> previewMode = m));
            x += lw;
        }
    }

    /** Textures draw themselves; sounds get a speaker; models show their texture with a corner mark. */
    private void drawIcon(GuiGraphics g, String key, AssetSource src, int x, int y, int size) {
        if (key.startsWith("textures/entity/equipment/humanoid")) {
            // Worn-armor sheets are mostly empty; show the chest piece front so it reads at a glance.
            TextureCache.Thumb t = src.has(key) ? cache.get(src, key) : null;
            if (t == null) t = cache.get(lib.vanilla(), key);
            if (t != null) {
                float k = t.width() / 64f;
                boolean legs = key.contains("leggings");
                int u = Math.round((legs ? 4 : 20) * k), v = Math.round(20 * k);
                int uw = Math.round((legs ? 4 : 8) * k), vh = Math.round(12 * k);
                int dw = legs ? size / 3 : size * 2 / 3;
                g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x + (size - dw) / 2, y, (float) u, (float) v, dw, size,
                        uw, vh, t.width(), t.height());
            }
            return;
        }
        if (key.startsWith("textures/block/") && size >= 14) {
            preview.icon3d(g, key, src, x, y, size);
            return;
        }
        if (com.enc.packcod.pack.Categories.isShield(key)) {
            preview.draw(g, Preview.Mode.FLAT, key, src, x, y, size);
            return;
        }
        if (com.enc.packcod.pack.Categories.isSky(key)) {
            preview.draw(g, Preview.Mode.FLAT, key, src, x, y, size);
            return;
        }
        if (Preview.mobKind(key) != null) {
            preview.mobFace(g, key, src, x, y, size);
            return;
        }
        if (Assets.isTexture(key)) {
            TextureCache.Thumb t = preview.thumbFor(src, preview.particleFrameNow(key));
            if (t != null) blitThumb(g, t, x, y, size);
            return;
        }
        if (Assets.isSound(key)) {
            drawPlayGlyph(g, x, y, size, isPlaying(key, src) ? ACCENT : DIM, isPlaying(key, src));
            return;
        }
        String tex = Assets.iconTextureFor(key);
        TextureCache.Thumb t = null;
        if (tex != null) {
            if (src.has(tex)) t = cache.get(src, tex);
            if (t == null) t = cache.get(lib.vanilla(), tex);
        }
        if (t != null) {
            blitThumb(g, t, x, y, size);
        } else {
            String braces = "{ }";
            g.drawString(font, braces, x + (size - font.width(braces)) / 2, y + (size - 8) / 2, FAINT, false);
        }
        int m = Math.max(3, size / 5);
        g.fill(x + size - m, y, x + size, y + m, 0xCC100E0C);
        g.fill(x + size - m + 1, y + 1, x + size - 1, y + m - 1, ACCENT_DARK);
    }

    /** A speaker drawn from rectangles; three bars light up while playing. */
    private void drawPlayGlyph(GuiGraphics g, int x, int y, int size, int color, boolean playing) {
        int u = Math.max(1, size / 16);
        int cy = y + size / 2;
        g.fill(x + 3 * u, cy - 2 * u, x + 6 * u, cy + 2 * u, color);
        for (int i = 0; i < 4 * u; i++) {
            g.fill(x + 6 * u + i, cy - 2 * u - i, x + 6 * u + i + 1, cy + 2 * u + i, color);
        }
        int waves = playing ? 3 : 1;
        for (int wv = 0; wv < waves; wv++) {
            int wx = x + 11 * u + wv * 2 * u;
            int hh = (2 + wv * 2) * u;
            g.fill(wx, cy - hh, wx + u, cy + hh, color);
        }
    }

    /** Names for sets read as the thing itself: "grass block", "hearts", "crosshair". */
    private static String prettyName(String key) {
        String set = com.enc.packcod.pack.Categories.setOf(key);
        if (set == null) return Assets.pretty(key);
        if (set.equals("hearts")) return "hearts (all kinds)";
        if (set.equals("crosshair")) return "crosshair";
        if (set.equals("shield")) return "shield";
        if (set.equals("sky")) return "sky (sun, moon, clouds, custom sky)";
        if (set.equals("end_crystal")) return "end crystal";
        if (set.equals("block:destroy_stage")) return "block breaking (all 10 stages)";
        if (set.startsWith("block:")) return set.substring(6).replace('_', ' ');
        if (set.startsWith("particle:")) return set.substring(9).replace('_', ' ') + " particle";
        return set.replace('_', ' ');
    }

    private void blitThumb(GuiGraphics g, TextureCache.Thumb t, int x, int y, int size) {
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, 0f, t.frameV(), size, size,
                t.width(), t.frameHeight(), t.width(), t.height());
    }

    private void scrollbar(GuiGraphics g, int x, int y, int h, int scroll, int rows, int totalRows) {
        if (totalRows <= rows || h <= 0) return;
        int knobH = Math.max(10, h * rows / totalRows);
        int knobY = y + (h - knobH) * scroll / Math.max(1, totalRows - rows);
        g.fill(x, y, x + 2, y + h, SLOT);
        g.fill(x, knobY, x + 2, knobY + knobH, FAINT);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String text, int mx, int my,
                        boolean primary, boolean enabled, Click action) {
        boolean hov = enabled && in(mx, my, x, y, w, h);
        raised(g, x, y, w, h, hov, primary, enabled);
        int color = !enabled ? FAINT : primary ? ON_ACCENT : TEXT;
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, color, false);
        if (enabled) hits.add(new Hit(x, y, w, h, action));
    }

    private AssetSource current(String key) {
        String set = com.enc.packcod.pack.Categories.setOf(key);
        if (set != null) {
            for (String k : lib.setMembers(set)) {
                AssetSource s = lib.source(project.pickFor(k));
                if (s != null && !s.isVanilla()) return s;
            }
            return lib.vanilla();
        }
        AssetSource s = lib.source(project.pickFor(key));
        return s == null ? lib.vanilla() : s;
    }

    /** Does this pack change this thing (for sets: any part of it)? */
    private boolean packHas(AssetSource s, String key) {
        if (s.isVanilla() || s.has(key) || lib.itemModelHolders(key).contains(s)) return true;
        String set = com.enc.packcod.pack.Categories.setOf(key);
        return set != null && lib.setHolders(set).contains(s);
    }

    /** Every pack with a version of this thing, vanilla first. */
    private List<AssetSource> holdersFor(String key) {
        String set = com.enc.packcod.pack.Categories.setOf(key);
        return lib.shown(set != null ? lib.setHolders(set) : lib.allHolders(key));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.in(mx, my)) {
                setFocused(null);
                h.action().on(event.button(), event.hasShiftDown());
                return true;
            }
        }
        setFocused(null);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        for (Wheel w : wheels) {
            if (w.in(mx, my)) {
                w.action().by(sy > 0 ? -1 : sy < 0 ? 1 : 0);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void onClose() {
        if (view == View.ITEM && lib != null) {
            view = backTo; // Esc on an item page goes back to the list first
            return;
        }
        SoundPreview.stop();
        project.save();
        if (lib != null && dirty && Settings.get().live) applyMix();
        cache.clear();
        if (lib != null) lib.close();
        else loading.thenAccept(PackLibrary::close);
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
