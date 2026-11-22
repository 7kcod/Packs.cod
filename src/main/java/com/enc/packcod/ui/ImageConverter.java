package com.enc.packcod.ui;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Turns any picture into texture-sized pixel art for people who'd rather not draw by hand.
 *
 *  - Shrinks with box filtering (averages every source pixel that lands in a target pixel),
 *    which keeps detail instead of the jagged look of plain nearest-pixel scaling.
 *  - Fit keeps the whole picture (empty space stays see-through); Fill covers the square and crops.
 *  - Soft edges become hard: pixels are either fully solid or fully see-through, like game art.
 *  - Optional background removal: colours close to the picture's corners become see-through.
 *  - Colours: keep all, reduce to 16 or 8 (median cut), or snap every pixel to the colours the
 *    pack's own texture uses so the result matches the pack's style.
 */
final class ImageConverter {

    enum Colors {
        ALL("All colours"), SIXTEEN("16 colours"), EIGHT("8 colours"), MATCH("Match pack");
        final String label;
        Colors(String l) { label = l; }
        Colors next() { return values()[(ordinal() + 1) % values().length]; }
    }

    /**
     * How the picture is placed on the texture.
     * crop*: fraction of the picture cut off each side (0..0.45). scale: 1 = the Fit/Fill size.
     * offX/offY: move in texture pixels. opacity: 0..1 for the whole picture.
     */
    record Placement(boolean fill, boolean removeBg, Colors colors,
                     float cropL, float cropR, float cropT, float cropB,
                     float scale, int offX, int offY, float opacity) {}

    /** Returns an ARGB layer, row by row, w x h, to be laid over the drawing. */
    static int[] convert(BufferedImage full, int w, int h, Placement pl, Set<Integer> packPalette) {
        // crop first
        int fw = full.getWidth(), fh = full.getHeight();
        int cx0 = Math.round(fw * pl.cropL()), cx1 = Math.round(fw * (1 - pl.cropR()));
        int cy0 = Math.round(fh * pl.cropT()), cy1 = Math.round(fh * (1 - pl.cropB()));
        cx1 = Math.max(cx0 + 1, cx1);
        cy1 = Math.max(cy0 + 1, cy1);
        int sw = cx1 - cx0, sh = cy1 - cy0;
        int[] in = full.getRGB(cx0, cy0, sw, sh, null, 0, sw);
        if (pl.removeBg()) removeBackground(in, sw, sh);

        // source pixels per texture pixel: Fit or Fill size, then the size slider
        double base = pl.fill() ? Math.min(sw / (double) w, sh / (double) h) : Math.max(sw / (double) w, sh / (double) h);
        double scale = base / Math.max(0.05, pl.scale());
        int outW = Math.max(1, (int) Math.round(sw / scale));
        int outH = Math.max(1, (int) Math.round(sh / scale));
        int left = (w - outW) / 2 + pl.offX(), top = (h - outH) / 2 + pl.offY();

        int[] out = new int[w * h];
        for (int ty = 0; ty < h; ty++) {
            int iy = ty - top;
            if (iy < 0 || iy >= outH) continue;
            for (int tx = 0; tx < w; tx++) {
                int ix = tx - left;
                if (ix < 0 || ix >= outW) continue;
                int x0 = (int) Math.floor(ix * scale), x1 = (int) Math.ceil((ix + 1) * scale);
                int y0 = (int) Math.floor(iy * scale), y1 = (int) Math.ceil((iy + 1) * scale);
                x0 = clamp(x0, 0, sw - 1); x1 = clamp(x1, x0 + 1, sw);
                y0 = clamp(y0, 0, sh - 1); y1 = clamp(y1, y0 + 1, sh);
                long a = 0, r = 0, g = 0, b = 0, n = 0;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int c = in[y * sw + x];
                        int ca = (c >>> 24) & 0xFF;
                        a += ca;
                        r += (long) ((c >> 16) & 0xFF) * ca;
                        g += (long) ((c >> 8) & 0xFF) * ca;
                        b += (long) (c & 0xFF) * ca;
                        n++;
                    }
                }
                int pa = (int) (a / Math.max(1, n));
                if (pa >= 128 && a > 0) {
                    out[ty * w + tx] = 0xFF000000 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
                }
            }
        }

        switch (pl.colors()) {
            case SIXTEEN -> quantize(out, 16);
            case EIGHT -> quantize(out, 8);
            case MATCH -> {
                if (packPalette == null || packPalette.isEmpty()) quantize(out, 16);
                else snap(out, packPalette);
            }
            default -> {
            }
        }
        int alpha = Math.round(255 * Math.max(0, Math.min(1, pl.opacity())));
        for (int i = 0; i < out.length; i++) {
            if ((out[i] >>> 24) != 0) out[i] = (alpha << 24) | (out[i] & 0xFFFFFF);
        }
        return out;
    }

    /** Lays a layer over a drawing (normal "over" blending). */
    static int[] over(int[] base, int[] layer) {
        int[] out = base.clone();
        for (int i = 0; i < out.length; i++) {
            int l = layer[i];
            int la = (l >>> 24) & 0xFF;
            if (la == 0) continue;
            if (la == 255) { out[i] = l; continue; }
            int b = base[i];
            int ba = (b >>> 24) & 0xFF;
            float fa = la / 255f, fb = ba / 255f * (1 - fa);
            float oa = fa + fb;
            int r = Math.round((((l >> 16) & 0xFF) * fa + ((b >> 16) & 0xFF) * fb) / oa);
            int g = Math.round((((l >> 8) & 0xFF) * fa + ((b >> 8) & 0xFF) * fb) / oa);
            int bl = Math.round(((l & 0xFF) * fa + (b & 0xFF) * fb) / oa);
            out[i] = Math.round(oa * 255) << 24 | r << 16 | g << 8 | bl;
        }
        return out;
    }

    /** Pixels close to the average corner colour become see-through. */
    private static void removeBackground(int[] px, int w, int h) {
        int[] corners = {px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1]};
        int r = 0, g = 0, b = 0;
        for (int c : corners) { r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF; }
        r /= 4; g /= 4; b /= 4;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int dr = ((c >> 16) & 0xFF) - r, dg = ((c >> 8) & 0xFF) - g, db = (c & 0xFF) - b;
            if (dr * dr + dg * dg + db * db < 48 * 48) px[i] = 0;
        }
    }

    /** Median cut down to n colours. */
    private static void quantize(int[] px, int n) {
        List<int[]> colors = new ArrayList<>();
        for (int c : px) if ((c >>> 24) != 0) colors.add(new int[]{(c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF});
        if (colors.size() <= n) return;
        List<List<int[]>> boxes = new ArrayList<>();
        boxes.add(colors);
        while (boxes.size() < n) {
            List<int[]> widest = null;
            int widestRange = -1, axis = 0;
            for (List<int[]> box : boxes) {
                if (box.size() < 2) continue;
                for (int ax = 0; ax < 3; ax++) {
                    int lo = 255, hi = 0;
                    for (int[] c : box) { lo = Math.min(lo, c[ax]); hi = Math.max(hi, c[ax]); }
                    if (hi - lo > widestRange) { widestRange = hi - lo; widest = box; axis = ax; }
                }
            }
            if (widest == null || widestRange <= 0) break;
            final int a = axis;
            widest.sort(Comparator.comparingInt(c -> c[a]));
            int mid = widest.size() / 2;
            boxes.remove(widest);
            boxes.add(new ArrayList<>(widest.subList(0, mid)));
            boxes.add(new ArrayList<>(widest.subList(mid, widest.size())));
        }
        List<Integer> palette = new ArrayList<>();
        for (List<int[]> box : boxes) {
            long r = 0, g = 0, b = 0;
            for (int[] c : box) { r += c[0]; g += c[1]; b += c[2]; }
            int s = Math.max(1, box.size());
            palette.add(0xFF000000 | (int) (r / s) << 16 | (int) (g / s) << 8 | (int) (b / s));
        }
        snap(px, palette);
    }

    /** Every solid pixel becomes the nearest palette colour. */
    private static void snap(int[] px, Iterable<Integer> palette) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            if ((c >>> 24) == 0) continue;
            int best = c;
            long bestD = Long.MAX_VALUE;
            for (int p : palette) {
                if ((p >>> 24) == 0) continue;
                long dr = ((c >> 16) & 0xFF) - ((p >> 16) & 0xFF);
                long dg = ((c >> 8) & 0xFF) - ((p >> 8) & 0xFF);
                long db = (c & 0xFF) - (p & 0xFF);
                long d = 3 * dr * dr + 4 * dg * dg + 2 * db * db; // weighted toward how eyes see it
                if (d < bestD) { bestD = d; best = 0xFF000000 | (p & 0xFFFFFF); }
            }
            px[i] = best;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private ImageConverter() {
    }
}
