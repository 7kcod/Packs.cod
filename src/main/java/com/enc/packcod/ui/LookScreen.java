package com.enc.packcod.ui;

import com.enc.packcod.pack.Settings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

import static com.enc.packcod.ui.Theme.*;

/**
 * Look: pick a colour palette, an accent colour and a style for the whole UI.
 * Every click applies at once, so this screen is its own preview.
 */
final class LookScreen extends Screen {

    private static final Identifier COD = Identifier.withDefaultNamespace("textures/item/cod.png");

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private String tooltip;
    private boolean demoToggle = true;

    private interface Click { void on(); }
    private record Hit(int x, int y, int w, int h, Click action) {
        boolean in(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }

    LookScreen(Screen parent) {
        super(Component.literal("Look"));
        this.parent = parent;
    }

    private void choose(Palette p, Integer accent, Style st) {
        Settings s = Settings.get();
        if (p != null) s.palette = p.name();
        if (accent != null) s.accent = accent;
        if (st != null) s.style = st.name();
        s.save();
        Theme.apply();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        tooltip = null;
        g.fill(0, 0, width, height, BG);

        // top bar
        g.fill(0, 0, width, 20, PANEL);
        g.fill(0, 19, width, 20, LINE);
        button(g, 4, 3, 40, 14, "← Back", mx, my, false, this::onClose);
        g.drawString(font, "Look", 52, 6, TEXT, false);
        g.drawString(font, "colours and style for pack.cod", 52 + font.width("Look") + 8, 6, FAINT, false);
        int rw = font.width("Reset") + 12;
        button(g, width - 4 - rw, 3, rw, 14, "Reset", mx, my, false,
                () -> choose(PALETTES.get(0), 0, Style.CLASSIC));

        int top = 26, bottom = height - 6;
        int leftW = Math.min(250, (width - 16) * 58 / 100);
        int lx = 6;

        // ---------------- palettes
        panel(g, lx, top, leftW, bottom - top);
        Theme.label(g, font, "Colours", lx + 7, top + 6);
        int pc = leftW >= 200 ? 3 : 2;
        int cardW = (leftW - 12 - (pc - 1) * 4) / pc, cardH = 21;
        int cy = top + 18;
        for (int i = 0; i < PALETTES.size(); i++) {
            Palette p = PALETTES.get(i);
            int x = lx + 6 + (i % pc) * (cardW + 4);
            int y = cy + (i / pc) * (cardH + 3);
            paletteCard(g, p, x, y, cardW, cardH, mx, my);
        }
        cy += ((PALETTES.size() + pc - 1) / pc) * (cardH + 3) + 5;

        // ---------------- accent
        Theme.label(g, font, "Accent", lx + 7, cy);
        cy += 12;
        int sw = Math.max(10, Math.min(15, (leftW - 12) / ACCENTS.length - 3));
        int ax = lx + 6;
        int cur = Settings.get().accent;
        for (int c : ACCENTS) {
            int shown = c == 0 ? palette.accent() : c;
            g.fill(ax, cy, ax + sw, cy + sw, shown);
            if (c == 0) g.drawString(font, "A", ax + (sw - font.width("A")) / 2, cy + (sw - 8) / 2,
                    luma(shown) > 0.55f ? 0xFF000000 : 0xFFFFFFFF, false);
            if (c == cur) outline(g, ax - 2, cy - 2, sw + 4, sw + 4, TEXT);
            boolean h = mx >= ax && my >= cy && mx < ax + sw && my < cy + sw;
            if (h) {
                outline(g, ax - 1, cy - 1, sw + 2, sw + 2, HOVER);
                tooltip = c == 0 ? "The palette's own accent" : String.format("#%06X", c & 0xFFFFFF);
            }
            final int pick = c;
            hits.add(new Hit(ax, cy, sw, sw, () -> choose(null, pick, null)));
            ax += sw + 3;
        }
        cy += sw + 8;

        // ---------------- styles
        Theme.label(g, font, "Style", lx + 7, cy);
        cy += 12;
        Style[] styles = Style.values();
        // cards in a grid so every style fits even on small windows
        int scols = leftW >= 200 ? 3 : 2;
        int srows = (styles.length + scols - 1) / scols;
        int scw = (leftW - 12 - (scols - 1) * 4) / scols;
        int sch = Math.max(16, Math.min(30, (bottom - 4 - cy) / srows - 3));
        for (int i = 0; i < styles.length; i++) {
            int x = lx + 6 + (i % scols) * (scw + 4);
            int y = cy + (i / scols) * (sch + 3);
            styleCard(g, styles[i], x, y, scw, sch, mx, my);
        }

        // ---------------- live preview
        int px = lx + leftW + 6, pw = width - px - 6;
        if (pw > 120) preview(g, px, top, pw, bottom - top, mx, my);
    }

    private void paletteCard(GuiGraphics g, Palette p, int x, int y, int w, int h, int mx, int my) {
        boolean on = p == palette;
        boolean hov = mx >= x && my >= y && mx < x + w && my < y + h;
        g.fill(x, y, x + w, y + h, p.bg());
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, p.panel());
        g.drawString(font, fit(font, p.name(), w - 8), x + 5, y + 3, p.text(), false);
        // little stripe of the palette's colours, accent widest
        int bx = x + 5;
        g.fill(bx, y + 13, bx + 14, y + 17, p.accent());
        bx += 15;
        for (int c : new int[]{p.text(), p.dim(), p.line()}) {
            g.fill(bx, y + 13, bx + 6, y + 17, c);
            bx += 7;
        }
        outline(g, x, y, w, h, on ? ACCENT : hov ? HOVER : p.line());
        if (on) outline(g, x - 1, y - 1, w + 2, h + 2, ACCENT);
        hits.add(new Hit(x, y, w, h, () -> choose(p, null, null)));
    }

    /** One style card: a sample button and slot drawn in that style, then its name. */
    private void styleCard(GuiGraphics g, Style st, int x, int y, int w, int h, int mx, int my) {
        boolean on = st == style;
        boolean hov = mx >= x && my >= y && mx < x + w && my < y + h;
        g.fill(x, y, x + w, y + h, on ? PANEL_HI : hov ? HOVER_FILL : mix(PANEL, BG, 0.4f));
        outline(g, x, y, w, h, on ? ACCENT : hov ? HOVER : LINE);

        Style keep = style;
        style = st;
        int bh = Math.max(8, Math.min(12, h - 6));
        int by = y + (h - bh) / 2;
        raised(g, x + 4, by, 14, bh, false, true, true);
        slot(g, x + 21, by, bh, bh);
        if (bh >= 10) g.blit(RenderPipelines.GUI_TEXTURED, COD, x + 22, by + 1, 0f, 0f, bh - 2, bh - 2, 16, 16, 16, 16);
        style = keep;

        int tx = x + 24 + bh;
        g.drawString(font, fit(font, st.label, x + w - tx - 2), tx, y + (h - 8) / 2, on || hov ? TEXT : mix(DIM, TEXT, 0.6f), false);
        if (hov) tooltip = st.label + ": " + st.blurb;
        hits.add(new Hit(x, y, w, h, () -> choose(null, null, st)));
    }

    /** A small mock of the main screen in the current look. */
    private void preview(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        panel(g, x, y, w, h);
        Theme.label(g, font, "Preview", x + 7, y + 6);
        int iy = y + 20;
        // fake list rows
        String[] rows = {"Swords", "Hearts", "Crosshair", "Totem"};
        for (int i = 0; i < rows.length; i++) {
            int ry = iy + i * 11;
            boolean on = i == 0;
            if (on) {
                g.fill(x + 4, ry, x + w - 4, ry + 11, PANEL_HI);
                g.fill(x + 4, ry, x + 6, ry + 11, ACCENT);
            }
            g.drawString(font, rows[i], x + 10, ry + 2, on ? TEXT : DIM, false);
            String n = String.valueOf(7 + i * 5);
            g.drawString(font, n, x + w - 8 - font.width(n), ry + 2, FAINT, false);
        }
        iy += rows.length * 11 + 6;

        // slots
        int cell = 20;
        int cols = Math.max(1, Math.min(6, (w - 12) / cell));
        for (int i = 0; i < cols; i++) {
            int sx = x + 6 + i * cell;
            slot(g, sx, iy, cell - 2, cell - 2);
            g.blit(RenderPipelines.GUI_TEXTURED, COD, sx + 1, iy + 1, 0f, 0f, 16, 16, 16, 16, 16, 16);
            if (i == 1) outline(g, sx - 1, iy - 1, cell, cell, ACCENT);
        }
        iy += cell + 6;

        // buttons
        int bw = (w - 16) / 3;
        button(g, x + 6, iy, bw, 16, "EXPORT", mx, my, true, () -> {});
        button(g, x + 8 + bw, iy, bw, 16, "Mixes", mx, my, false, () -> {});
        raised(g, x + 10 + 2 * bw, iy, bw, 16, false, false, false);
        g.drawString(font, "Off", x + 10 + 2 * bw + (bw - font.width("Off")) / 2, iy + 4, FAINT, false);
        iy += 22;

        // toggle and search box
        String t = (demoToggle ? "● " : "○ ") + "Live";
        button(g, x + 6, iy, font.width(t) + 12, 14, t, mx, my, false, () -> demoToggle = !demoToggle);
        int sx = x + 14 + font.width(t) + 12;
        slot(g, sx, iy, x + w - 6 - sx, 14);
        g.drawString(font, "search", sx + 4, iy + 3, FAINT, false);
        iy += 22;

        if (iy + 22 < y + h) {
            g.drawString(font, "Good: saved", x + 6, iy, GOOD, false);
            g.drawString(font, "Bad: missing pack", x + 6, iy + 11, BAD, false);
        }
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String text, int mx, int my, boolean primary, Click a) {
        boolean hov = mx >= x && my >= y && mx < x + w && my < y + h;
        raised(g, x, y, w, h, hov, primary, true);
        g.drawString(font, fit(font, text, w - 4), x + (w - Math.min(w - 4, font.width(text))) / 2, y + (h - 8) / 2,
                primary ? ON_ACCENT : TEXT, false);
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).in(event.x(), event.y())) {
                hits.get(i).action().on();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
