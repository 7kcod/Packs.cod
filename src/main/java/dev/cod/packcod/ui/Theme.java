package dev.cod.packcod.ui;

import dev.cod.packcod.pack.Settings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Colours and the few drawing primitives every screen is built from.
 *
 * The colours come from the chosen palette (plus an optional accent override) and the
 * shapes from the chosen style, both picked on the Look screen. Everything reads these
 * fields when it draws, so a change shows up straight away.
 */
final class Theme {

    /** One colour set. on = text drawn on top of the accent colour. */
    record Palette(String name, int bg, int panel, int panelHi, int line, int slot, int slotShade, int slotLight,
                   int text, int dim, int faint, int accent, int accentDark, int on) {}

    static final List<Palette> PALETTES = List.of(
            new Palette("Cod", 0xFF161412, 0xFF221F1B, 0xFF2B2722, 0xFF3A342D, 0xFF100E0C, 0xFF080706, 0xFF3D372F,
                    0xFFE9E1D2, 0xFF8D8373, 0xFF5C5449, 0xFFE8A33D, 0xFF7A531A, 0xFF1A1208),
            new Palette("Deep Ocean", 0xFF0D1620, 0xFF15212E, 0xFF1C2B3B, 0xFF2A3D52, 0xFF0A1119, 0xFF05090D, 0xFF2E445B,
                    0xFFDCE8F2, 0xFF7E93A8, 0xFF4E6075, 0xFF4FC3F7, 0xFF1F6A8A, 0xFF061520),
            new Palette("Nether", 0xFF1A0E0E, 0xFF261414, 0xFF311A19, 0xFF4A2522, 0xFF120909, 0xFF080404, 0xFF4E2A26,
                    0xFFF2DED8, 0xFFA8807A, 0xFF6E504B, 0xFFFF5A36, 0xFF8A2A17, 0xFF200805),
            new Palette("The End", 0xFF120F1C, 0xFF1C1829, 0xFF252036, 0xFF372F4F, 0xFF0D0B15, 0xFF06050A, 0xFF3B3354,
                    0xFFEDE6FA, 0xFF9B8FB8, 0xFF615878, 0xFFC08CFF, 0xFF5C3A8F, 0xFF170C26),
            new Palette("Emerald", 0xFF0E1712, 0xFF15221A, 0xFF1B2C22, 0xFF2A4233, 0xFF0A110D, 0xFF050906, 0xFF2E4838,
                    0xFFDFF2E5, 0xFF7FA38B, 0xFF50695A, 0xFF41D97A, 0xFF1C7340, 0xFF04170B),
            new Palette("Snow", 0xFFE9EDF1, 0xFFF7F9FB, 0xFFE3E9EF, 0xFFC7D0DA, 0xFFDDE3EA, 0xFFB9C3CE, 0xFFFFFFFF,
                    0xFF1E2833, 0xFF5D6B7A, 0xFF97A3B0, 0xFF2F7DE1, 0xFF1A4F94, 0xFFFFFFFF),
            new Palette("Midnight", 0xFF000000, 0xFF0B0B0D, 0xFF141418, 0xFF26262C, 0xFF050506, 0xFF000000, 0xFF2A2A30,
                    0xFFF5F5F5, 0xFF8A8A92, 0xFF55555C, 0xFF00E5C3, 0xFF007866, 0xFF001A16),
            new Palette("Cherry", 0xFF1C1216, 0xFF281A20, 0xFF33212A, 0xFF4B3240, 0xFF140C10, 0xFF090507, 0xFF503646,
                    0xFFF8E6EE, 0xFFB08A9C, 0xFF755B67, 0xFFFF8FB8, 0xFF94405F, 0xFF2A0915)
    );

    /** Accent colours you can put on top of any palette (0 = the palette's own). */
    static final int[] ACCENTS = {0, 0xFFE8A33D, 0xFFFFD84A, 0xFF8DBF6A, 0xFF41D97A, 0xFF4FC3F7, 0xFF5B7DF5,
            0xFFC08CFF, 0xFFFF8FB8, 0xFFFF5A36, 0xFFF2F2F2};

    /** How things are shaped. Each one gives buttons, slots and panels a different look. */
    enum Style {
        CLASSIC("Classic", "Key-cap buttons, soft edges"),
        PIXEL("Pixel", "Chunky bevels like Minecraft's own menus"),
        FLAT("Flat", "No bevels, clean blocks of colour"),
        OUTLINE("Outline", "Thin lines, almost no fill"),
        GLOW("Glow", "Rounded corners and an accent glow");

        final String label, blurb;

        Style(String label, String blurb) {
            this.label = label;
            this.blurb = blurb;
        }
    }

    static int BG, PANEL, PANEL_HI, LINE, SLOT, SLOT_SHADE, SLOT_LIGHT, TEXT, DIM, FAINT, ACCENT, ACCENT_DARK;
    /** Text on an accent fill. */
    static int ON_ACCENT;
    /** Row hover fill, tooltip background, hover outline. */
    static int HOVER_FILL, TIP, HOVER;
    static final int GOOD = 0xFF8DBF6A;
    static final int BAD = 0xFFD9614C;
    static Style style = Style.CLASSIC;
    static Palette palette = PALETTES.get(0);

    static {
        apply();
    }

    /** Loads the saved palette, accent and style. */
    static void apply() {
        Settings s = Settings.get();
        Palette p = PALETTES.get(0);
        for (Palette q : PALETTES) if (q.name().equals(s.palette)) p = q;
        Style st = Style.CLASSIC;
        for (Style q : Style.values()) if (q.name().equals(s.style)) st = q;
        set(p, s.accent, st);
    }

    static void set(Palette p, int accent, Style st) {
        palette = p;
        style = st;
        BG = p.bg();
        PANEL = p.panel();
        PANEL_HI = p.panelHi();
        LINE = p.line();
        SLOT = p.slot();
        SLOT_SHADE = p.slotShade();
        SLOT_LIGHT = p.slotLight();
        TEXT = p.text();
        DIM = p.dim();
        FAINT = p.faint();
        if (accent != 0) {
            ACCENT = accent;
            ACCENT_DARK = mix(accent, p.bg(), 0.55f);
            ON_ACCENT = luma(accent) > 0.55f ? 0xFF14110D : 0xFFFFFFFF;
        } else {
            ACCENT = p.accent();
            ACCENT_DARK = p.accentDark();
            ON_ACCENT = p.on();
        }
        HOVER_FILL = mix(p.panel(), p.text(), 0.06f);
        TIP = (0xF0 << 24) | (p.slot() & 0xFFFFFF);
        HOVER = (0xAA << 24) | (p.text() & 0xFFFFFF);
    }

    static boolean light() {
        return luma(BG) > 0.5f;
    }

    // ------------------------------------------------------------------ primitives

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        switch (style) {
            case PIXEL -> {
                g.fill(x, y, x + w, y + h, PANEL);
                outline(g, x, y, w, h, 0xFF000000);
                g.fill(x + 1, y + 1, x + w - 1, y + 3, PANEL_HI);
                g.fill(x + 1, y + 1, x + 3, y + h - 1, PANEL_HI);
                g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, SLOT_SHADE);
                g.fill(x + w - 3, y + 1, x + w - 1, y + h - 1, SLOT_SHADE);
            }
            case FLAT -> g.fill(x, y, x + w, y + h, PANEL);
            case OUTLINE -> {
                g.fill(x, y, x + w, y + h, mix(BG, PANEL, 0.4f));
                outline(g, x, y, w, h, LINE);
            }
            case GLOW -> {
                rounded(g, x, y, w, h, PANEL, 3);
                g.fill(x + 3, y, x + w - 3, y + 1, mix(PANEL, ACCENT, 0.35f));
            }
            default -> {
                g.fill(x, y, x + w, y + h, PANEL);
                outline(g, x, y, w, h, LINE);
            }
        }
    }

    static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Sunken box, like an inventory slot. */
    static void slot(GuiGraphics g, int x, int y, int w, int h) {
        switch (style) {
            case PIXEL -> {
                g.fill(x, y, x + w, y + h, SLOT);
                g.fill(x, y, x + w, y + 1, 0xFF000000);
                g.fill(x, y, x + 1, y + h, 0xFF000000);
                g.fill(x + 1, y + h - 1, x + w, y + h, SLOT_LIGHT);
                g.fill(x + w - 1, y + 1, x + w, y + h, SLOT_LIGHT);
            }
            case FLAT -> g.fill(x, y, x + w, y + h, SLOT);
            case OUTLINE -> outline(g, x, y, w, h, LINE);
            case GLOW -> rounded(g, x, y, w, h, SLOT, 2);
            default -> {
                g.fill(x, y, x + w, y + h, SLOT);
                g.fill(x, y, x + w - 1, y + 1, SLOT_SHADE);
                g.fill(x, y + 1, x + 1, y + h - 1, SLOT_SHADE);
                g.fill(x + 1, y + h - 1, x + w, y + h, SLOT_LIGHT);
                g.fill(x + w - 1, y, x + w, y + h - 1, SLOT_LIGHT);
            }
        }
    }

    /** A button face in the current style. */
    static void raised(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean primary, boolean enabled) {
        int face = !enabled ? PANEL : primary ? (hover ? mix(ACCENT, 0xFFFFFFFF, 0.18f) : ACCENT)
                : (hover ? mix(PANEL_HI, TEXT, 0.10f) : PANEL_HI);
        switch (style) {
            case PIXEL -> {
                g.fill(x, y, x + w, y + h, 0xFF000000);
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
                if (enabled) {
                    int hi = primary ? mix(ACCENT, 0xFFFFFFFF, 0.45f) : mix(face, 0xFFFFFFFF, 0.22f);
                    int lo = primary ? ACCENT_DARK : mix(face, 0xFF000000, 0.45f);
                    g.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
                    g.fill(x + 1, y + 2, x + 2, y + h - 2, hi);
                    g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
                    g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, lo);
                }
                if (hover) outline(g, x, y, w, h, 0xFFFFFFFF);
            }
            case FLAT -> {
                g.fill(x, y, x + w, y + h, face);
                if (hover && !primary) g.fill(x, y + h - 1, x + w, y + h, ACCENT);
            }
            case OUTLINE -> {
                if (primary && enabled) g.fill(x, y, x + w, y + h, hover ? face : mix(ACCENT, BG, 0.15f));
                else if (hover) g.fill(x, y, x + w, y + h, HOVER_FILL);
                outline(g, x, y, w, h, !enabled ? LINE : primary || hover ? ACCENT : mix(LINE, TEXT, 0.3f));
            }
            case GLOW -> {
                if (enabled && (hover || primary)) rounded(g, x - 1, y - 1, w + 2, h + 2, (0x60 << 24) | (ACCENT & 0xFFFFFF), 3);
                rounded(g, x, y, w, h, face, 3);
                if (enabled) g.fill(x + 2, y + 1, x + w - 2, y + 2, mix(face, 0xFFFFFFFF, 0.25f));
            }
            default -> {
                int top = !enabled ? LINE : primary ? mix(ACCENT, 0xFFFFFFFF, 0.4f) : mix(PANEL_HI, TEXT, 0.12f);
                int bottom = !enabled ? SLOT_SHADE : primary ? ACCENT_DARK : SLOT_SHADE;
                g.fill(x, y, x + w, y + h, face);
                g.fill(x, y, x + w, y + 1, top);
                g.fill(x, y + h - 2, x + w, y + h, bottom);
            }
        }
    }

    /** Small caps style label. Glow and Flat use the accent colour for it. */
    static void label(GuiGraphics g, Font font, String text, int x, int y) {
        int col = style == Style.GLOW || style == Style.FLAT ? ACCENT : DIM;
        int cx = x;
        for (char c : text.toUpperCase().toCharArray()) {
            String s = String.valueOf(c);
            g.drawString(font, s, cx, y, col, false);
            cx += font.width(s) + 1;
        }
        if (style == Style.PIXEL) g.fill(x, y + 9, cx - 1, y + 10, ACCENT_DARK);
    }

    static String fit(Font font, String s, int width) {
        if (font.width(s) <= width) return s;
        String dots = "..";
        return font.plainSubstrByWidth(s, Math.max(0, width - font.width(dots))) + dots;
    }

    /** A box with its corners cut by r pixels. */
    static void rounded(GuiGraphics g, int x, int y, int w, int h, int color, int r) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int cut = Math.max(0, r - 1 - i);
            g.fill(x + cut, y + i, x + w - cut, y + i + 1, color);
            g.fill(x + cut, y + h - 1 - i, x + w - cut, y + h - i, color);
        }
    }

    static int mix(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    static float luma(int c) {
        return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
    }

    private Theme() {
    }
}
