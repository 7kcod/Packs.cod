package dev.cod.packcod.ui;

import com.mojang.blaze3d.platform.NativeImage;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * A tiny software renderer that draws a held item exactly the way the game builds and places it
 * in first person, into a transparent image the preview then lays over the scene.
 *
 * What it copies from the game (1.21.11):
 *  - Items are an extruded sprite: every opaque texture pixel is a little box 1/16 thick
 *    (front, back, and side walls wherever the neighbour pixel is see-through).
 *  - Right-hand placement: translate(0.56, -0.52, -0.72) (ItemInHandRenderer), then the model's
 *    "firstperson_righthand" display transform (translation/16, rotation XYZ in degrees, scale),
 *    then translate(-0.5, -0.5, -0.5) like ItemRenderer.
 *  - Hand camera: perspective with a 70 degree vertical field of view at the screen's aspect.
 *  - Lighting: two directional lights plus ambient like the game's entity/item lighting.
 */
final class FirstPersonRenderer {

    static final int W = 320, H = 180;

    /** Item display transform: translation in model pixels (/16 applied here), rotation degrees, scale. */
    record Display(float tx, float ty, float tz, float rx, float ry, float rz, float sx, float sy, float sz) {
        /** minecraft:item/handheld and item/generated both use this for firstperson_righthand. */
        static final Display HANDHELD = new Display(1.13f, 3.2f, 1.13f, 0, -90, 25, 0.68f, 0.68f, 0.68f);
        /** minecraft:block/block firstperson_righthand. */
        static final Display BLOCK = new Display(0, 0, 0, 0, 45, 0, 0.40f, 0.40f, 0.40f);
    }

    private static final Vector3f LIGHT0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize();
    private static final Vector3f LIGHT1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();

    private final NativeImage out = new NativeImage(W, H, true);
    private final float[] depth = new float[W * H];
    private final Matrix4f mvp;
    private final Matrix3f normalMat;

    private FirstPersonRenderer(Display d) {
        java.util.Arrays.fill(depth, Float.MAX_VALUE);
        Matrix4f model = new Matrix4f()
                .translate(0.56f, -0.52f, -0.72f)
                .translate(d.tx() / 16f, d.ty() / 16f, d.tz() / 16f)
                .rotateXYZ((float) Math.toRadians(d.rx()), (float) Math.toRadians(d.ry()), (float) Math.toRadians(d.rz()))
                .scale(d.sx(), d.sy(), d.sz())
                .translate(-0.5f, -0.5f, -0.5f);
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70), W / (float) H, 0.05f, 100f);
        this.mvp = new Matrix4f(proj).mul(model);
        this.normalMat = new Matrix3f(model).invert().transpose();
    }

    /** Held flat item (sword, tool, food...). */
    static NativeImage item(NativeImage sprite, Display display) {
        FirstPersonRenderer r = new FirstPersonRenderer(display == null ? Display.HANDHELD : display);
        int[][] px = pixels(sprite, 64);
        int n = px.length;
        float zf = 8.5f / 16f, zb = 7.5f / 16f;
        for (int v = 0; v < n; v++) {
            for (int u = 0; u < n; u++) {
                int c = px[v][u];
                if (alpha(c) == 0) continue;
                float x0 = u / (float) n, x1 = (u + 1) / (float) n;
                float y0 = 1 - (v + 1) / (float) n, y1 = 1 - v / (float) n;
                r.quad(x0, y0, zf, x1, y0, zf, x1, y1, zf, x0, y1, zf, 0, 0, 1, c);
                r.quad(x1, y0, zb, x0, y0, zb, x0, y1, zb, x1, y1, zb, 0, 0, -1, c);
                if (u == 0 || alpha(px[v][u - 1]) == 0) r.quad(x0, y0, zb, x0, y0, zf, x0, y1, zf, x0, y1, zb, -1, 0, 0, c);
                if (u == n - 1 || alpha(px[v][u + 1]) == 0) r.quad(x1, y0, zf, x1, y0, zb, x1, y1, zb, x1, y1, zf, 1, 0, 0, c);
                if (v == 0 || alpha(px[v - 1][u]) == 0) r.quad(x0, y1, zf, x1, y1, zf, x1, y1, zb, x0, y1, zb, 0, 1, 0, c);
                if (v == n - 1 || alpha(px[v + 1][u]) == 0) r.quad(x0, y0, zb, x1, y0, zb, x1, y0, zf, x0, y0, zf, 0, -1, 0, c);
            }
        }
        return r.out;
    }

    /** Held full block: a cube with top and side textures. */
    static NativeImage block(NativeImage top, NativeImage side, int topTint, int sideTint) {
        FirstPersonRenderer r = new FirstPersonRenderer(Display.BLOCK);
        int[][] t = pixels(top, 16), s = pixels(side, 16);
        int nt = t.length, ns = s.length;
        for (int a = 0; a < nt; a++) {
            for (int b = 0; b < nt; b++) {
                int c = tint(t[b][a], topTint);
                if (alpha(c) == 0) continue;
                float x0 = a / (float) nt, x1 = (a + 1) / (float) nt, z0 = b / (float) nt, z1 = (b + 1) / (float) nt;
                r.quad(x0, 1, z0, x0, 1, z1, x1, 1, z1, x1, 1, z0, 0, 1, 0, c);   // top
                r.quad(x0, 0, z1, x0, 0, z0, x1, 0, z0, x1, 0, z1, 0, -1, 0, c);  // bottom
            }
        }
        for (int a = 0; a < ns; a++) {
            for (int b = 0; b < ns; b++) {
                int c = tint(s[b][a], sideTint);
                if (alpha(c) == 0) continue;
                float p0 = a / (float) ns, p1 = (a + 1) / (float) ns;
                float y0 = 1 - (b + 1) / (float) ns, y1 = 1 - b / (float) ns;
                r.quad(p0, y0, 1, p1, y0, 1, p1, y1, 1, p0, y1, 1, 0, 0, 1, c);      // south
                r.quad(1 - p0, y0, 0, 1 - p1, y0, 0, 1 - p1, y1, 0, 1 - p0, y1, 0, 0, 0, -1, c); // north
                r.quad(1, y0, 1 - p0, 1, y0, 1 - p1, 1, y1, 1 - p1, 1, y1, 1 - p0, 1, 0, 0, c);   // east
                r.quad(0, y0, p0, 0, y0, p1, 0, y1, p1, 0, y1, p0, -1, 0, 0, c);     // west
            }
        }
        return r.out;
    }

    // ------------------------------------------------------------------ raster

    private void quad(float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz,
                      float nx, float ny, float nz, int argb) {
        Vector3f n = normalMat.transform(new Vector3f(nx, ny, nz)).normalize();
        float shade = Math.min(1f, 0.4f + 0.6f * (Math.max(0, n.dot(LIGHT0)) + Math.max(0, n.dot(LIGHT1))));
        int col = shadeColor(argb, shade);
        float[] a = project(ax, ay, az), b = project(bx, by, bz), c = project(cx, cy, cz), d = project(dx, dy, dz);
        if (a == null || b == null || c == null || d == null) return;
        tri(a, b, c, col);
        tri(a, c, d, col);
    }

    private float[] project(float x, float y, float z) {
        Vector4f p = mvp.transform(new Vector4f(x, y, z, 1f));
        if (p.w <= 0.001f) return null;
        return new float[]{(p.x / p.w + 1) * 0.5f * W, (1 - p.y / p.w) * 0.5f * H, p.w};
    }

    private void tri(float[] a, float[] b, float[] c, int col) {
        float area = edge(a, b, c[0], c[1]);
        if (Math.abs(area) < 1e-6f) return;
        int minX = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = Math.min(W - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = Math.min(H - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        for (int y = minY; y <= maxY; y++) {
            float py = y + 0.5f;
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float w0 = edge(b, c, px, py) / area;
                float w1 = edge(c, a, px, py) / area;
                float w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float z = w0 * a[2] + w1 * b[2] + w2 * c[2];
                int i = y * W + x;
                if (z < depth[i]) {
                    depth[i] = z;
                    out.setPixel(x, y, col);
                }
            }
        }
    }

    private static float edge(float[] a, float[] b, float px, float py) {
        return (b[0] - a[0]) * (py - a[1]) - (b[1] - a[1]) * (px - a[0]);
    }

    // ------------------------------------------------------------------ pixels

    /** First animation frame as [row][col], shrunk to at most max x max for speed. */
    private static int[][] pixels(NativeImage img, int max) {
        int n0 = Math.min(img.getWidth(), img.getHeight());
        int n = Math.min(n0, max);
        int[][] p = new int[n][n];
        for (int v = 0; v < n; v++)
            for (int u = 0; u < n; u++) {
                int sx = u * n0 / n, sy = v * n0 / n;
                int c;
                try {
                    c = img.getPixel(sx, sy);
                } catch (Exception e) {
                    c = 0;
                }
                p[v][u] = c;
            }
        return p;
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int shadeColor(int argb, float s) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * s));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * s));
        int b = Math.min(255, Math.round((argb & 0xFF) * s));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int tint(int argb, int tint) {
        if (tint == 0xFFFFFFFF) return argb;
        int r = ((argb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((argb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (argb & 0xFF) * (tint & 0xFF) / 255;
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
