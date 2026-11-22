package dev.cod.packcod.ui;

import dev.cod.packcod.pack.AssetSource;
import dev.cod.packcod.pack.Assets;
import dev.cod.packcod.pack.Categories;
import dev.cod.packcod.pack.PackLibrary;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a texture the way you'd actually meet it: as a block, in the world, in your hand,
 * or worn by the player. Everything is drawn from the chosen pack's files with vanilla as
 * fallback, so it works for packs that aren't enabled.
 */
final class Preview {

    enum Mode {
        FLAT("Flat"), BLOCK("Block"), WORLD("World"), INGAME("In game"), PLAYER("On player"), MOB("3D");
        final String label;
        Mode(String label) { this.label = label; }
    }

    private static final float COS30 = 0.8660254f;
    private static final int SKY_TOP = 0xFF7FA8E0;
    private static final int SKY_BOTTOM = 0xFFB9D2F0;
    private static final int GRASS_TINT = 0xFF79C05A;
    private static final int FOLIAGE_TINT = 0xFF59AE30;

    private final TextureCache cache;
    private PackLibrary lib;
    private java.util.List<java.nio.file.Path> backgrounds = java.util.List.of();

    void setBackgrounds(java.util.List<java.nio.file.Path> bgs) {
        this.backgrounds = bgs;
    }

    boolean hasBackgrounds() {
        return !backgrounds.isEmpty();
    }

    Preview(TextureCache cache) {
        this.cache = cache;
    }

    void setLibrary(PackLibrary lib) {
        this.lib = lib;
        displayKeys.clear();
        fixedScales.clear();
    }

    private final java.util.Map<String, String> displayKeys = new java.util.HashMap<>();

    /**
     * The texture the game would really show for this item in this pack. Many packs (totems
     * especially) point the item at their own texture through items/X.json or models/item/X.json
     * instead of replacing textures/item/X.png.
     */
    String displayKey(AssetSource src, String key) {
        if (src == null || src.isVanilla() || lib == null || !key.startsWith("textures/item/")) return key;
        return displayKeys.computeIfAbsent(src.id() + "|" + key, k -> resolveItemTexture(src, key));
    }

    private String resolveItemTexture(AssetSource src, String key) {
        String name = key.substring("textures/item/".length(), key.length() - 4);
        String model = "models/item/" + name + ".json";
        try {
            byte[] def = src.read("items/" + name + ".json");
            if (def != null) {
                com.google.gson.JsonElement m = findModel(com.google.gson.JsonParser.parseString(new String(def, java.nio.charset.StandardCharsets.UTF_8)));
                if (m != null) model = modelPath(m.getAsString());
            }
            for (int i = 0; i < 10 && model != null; i++) {
                byte[] bytes = src.read(model);
                if (bytes == null) break; // only follow the pack's own models
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("textures") && o.get("textures").isJsonObject()) {
                    com.google.gson.JsonObject tx = o.getAsJsonObject("textures");
                    String first = tx.has("layer0") ? tx.get("layer0").getAsString() : null;
                    if (first == null) for (var en : tx.entrySet()) {
                        if (en.getValue().isJsonPrimitive() && !en.getValue().getAsString().startsWith("#")) {
                            first = en.getValue().getAsString();
                            break;
                        }
                    }
                    if (first != null) {
                        if (first.contains(":")) {
                            if (!first.startsWith("minecraft:")) break;
                            first = first.substring("minecraft:".length());
                        }
                        String tex = "textures/" + first + ".png";
                        if (src.has(tex)) return tex;
                    }
                }
                model = o.has("parent") ? modelPath(o.get("parent").getAsString()) : null;
            }
        } catch (Exception ignored) {
        }
        return key;
    }

    /** Thumb for drawing: follows the pack's item model to its real texture, vanilla as fallback. */
    TextureCache.Thumb thumbFor(AssetSource src, String key) {
        return thumb(src, key);
    }

    /** Which views make sense for this file. */
    static List<Mode> modesFor(String key) {
        List<Mode> m = new ArrayList<>();
        if (!Assets.isTexture(key)) return m;
        // Flat always comes first and is what things open on.
        m.add(Mode.FLAT);
        if (isBreaking(key)) {
            m.add(Mode.BLOCK);
            m.add(Mode.INGAME);
        } else if (key.startsWith("textures/block/")) {
            m.add(Mode.BLOCK);
            m.add(Mode.WORLD);
            m.add(Mode.INGAME);
        } else if (key.startsWith("textures/item/") && armorMaterial(key) != null) {
            m.add(Mode.PLAYER);
        } else if (key.startsWith("textures/gui/sprites/hud/")) {
            m.add(Mode.INGAME);
        } else if (key.startsWith("textures/item/")) {
            m.add(Mode.INGAME);
        } else if (key.startsWith("textures/entity/equipment/humanoid")) {
            m.add(Mode.PLAYER);
        } else if (key.startsWith("textures/particle/")) {
            m.add(Mode.INGAME);
        } else if (mobKind(key) != null) {
            m.add(Mode.MOB);
        }
        return m;
    }

    /** Draws key from src in a size x size box. */
    void draw(GuiGraphics g, Mode mode, String key, AssetSource src, int x, int y, int size) {
        draw(g, mode, key, src, x, y, size, size);
    }

    /** In game uses the whole w x h box (it should be screen-shaped, 16:9); other views stay square. */
    void draw(GuiGraphics g, Mode mode, String key, AssetSource src, int x, int y, int w, int h) {
        if (mode == Mode.INGAME) {
            inGame(g, key, src, x, y, w, h);
            return;
        }
        if (mode == Mode.FLAT && dev.cod.packcod.pack.Categories.isSky(key)) {
            sky(g, src, x, y, w, h);
            return;
        }
        int size = Math.min(w, h);
        x += (w - size) / 2;
        y += (h - size) / 2;
        switch (mode) {
            case BLOCK -> icon3d(g, key, src, x, y, size);
            case WORLD -> world(g, key, src, x, y, size);
            case PLAYER -> player(g, key, src, x, y, size);
            case MOB -> mob(g, key, src, x, y, size);
            default -> {
                TextureCache.Thumb t = thumb(src, isBreaking(key) ? stageKey(key) : key);
                if (t == null) break;
                if (dev.cod.packcod.pack.Categories.isShield(key)) {
                    // just the shield's front face (12x22 at 1,1 on its 64x64 sheet)
                    float k = t.width() / 64f;
                    int dh = size, dw = Math.round(size * 12 / 22f);
                    region(g, t, x + (size - dw) / 2, y, dw, dh, 1 * k, 1 * k, 12 * k, 22 * k);
                } else {
                    flat(g, t, x, y, size, size);
                }
            }
        }
    }

    // ------------------------------------------------------------------ block + world

    /** Isometric cube whose top vertex is at (cx, cy); s = edge length on screen. */
    private void cube(GuiGraphics g, String key, AssetSource src, float cx, float cy, float s) {
        String[] faces = blockFaces(key, src);
        TextureCache.Thumb top = thumb(src, faces[0]);
        TextureCache.Thumb side = thumb(src, faces[1]);
        if (side == null) side = top;
        if (top == null) top = side;
        if (top == null) return;
        int topTint = tintFor(faces[0]);
        int sideTint = faces[1].contains("grass_block") ? 0xFFFFFFFF : tintFor(faces[1]);
        float k = s / 16f;
        // top
        face(g, top, new Matrix3x2f(COS30 * k, 0.5f * k, -COS30 * k, 0.5f * k, cx, cy), topTint, 0);
        // left
        face(g, side, new Matrix3x2f(COS30 * k, 0.5f * k, 0, k, cx - COS30 * s, cy + 0.5f * s), sideTint, 0x55000000);
        // right
        face(g, side, new Matrix3x2f(COS30 * k, -0.5f * k, 0, k, cx, cy + s), sideTint, 0x30000000);
    }

    private void face(GuiGraphics g, TextureCache.Thumb t, Matrix3x2f m, int tint, int shade) {
        g.pose().pushMatrix();
        g.pose().mul(m);
        // grow each face a hair so neighbouring faces overlap instead of leaving a 1px seam
        float pxPerUnit = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
        float p = Math.min(1.2f, 0.55f / Math.max(0.01f, pxPerUnit));
        g.pose().translate(-p, -p);
        g.pose().scale((16 + 2 * p) / 16f, (16 + 2 * p) / 16f);
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), 0, 0, 0f, t.frameV(), 16, 16,
                t.width(), t.frameHeight(), t.width(), t.height(), tint);
        if (shade != 0) g.fill(0, 0, 16, 16, shade);
        g.pose().popMatrix();
    }

    /** A little patch of ground made of the block, with one more on top, under a sky. */
    private void world(GuiGraphics g, String key, AssetSource src, int x, int y, int size) {
        sky(g, x, y, size, size);
        g.enableScissor(x, y, x + size, y + size);
        int n = 4;
        float s = size / 5.2f;
        float cx = x + size / 2f;
        float cy = y + size * 0.30f;
        for (int sum = 0; sum <= 2 * (n - 1); sum++) {
            for (int i = 0; i < n; i++) {
                int j = sum - i;
                if (j < 0 || j >= n) continue;
                float px = cx + (i - j) * COS30 * s;
                float py = cy + (i + j) * 0.5f * s;
                if (isCross(key)) {
                    cube(g, "textures/block/grass_block_top.png", src, px, py, s);
                    cross(g, key, src, px, py - s, s);
                } else {
                    cube(g, key, src, px, py, s);
                }
            }
            // one block sitting on the patch, drawn once the tiles behind it are down
            if (sum == 2 && !isCross(key)) cube(g, key, src, cx, cy + s - s, s);
        }
        g.disableScissor();
    }

    /** Fire, flowers, saplings, grass and the like: two crossed planes, like the game. */
    static boolean isCross(String key) {
        if (!key.startsWith("textures/block/")) return false;
        String n = key.substring(key.lastIndexOf('/') + 1);
        if (n.contains("_block") || n.contains("stem") || n.contains("leaves")) return false;
        for (String p : new String[]{"fire", "sapling", "tulip", "poppy", "dandelion", "orchid", "allium", "bluet",
                "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose", "torchflower", "short_grass",
                "tall_grass", "fern", "dead_bush", "bush", "cobweb", "sugar_cane", "_stage", "mushroom", "roots",
                "sprouts", "coral", "kelp", "seagrass", "propagule", "pitcher", "sunflower", "lilac", "rose_bush",
                "peony", "torch", "lantern", "sweet_berry", "nether_wart", "glow_lichen", "vine", "eyeblossom",
                "cactus_flower", "leaf_litter", "wildflowers", "amethyst_cluster", "amethyst_bud"}) {
            if (n.contains(p)) return true;
        }
        return false;
    }

    /** The block the way it stands in the world: a cube, or crossed planes for plants and fire. */
    void icon3d(GuiGraphics g, String key, AssetSource src, int x, int y, int size) {
        if (isBreaking(key)) crackedCube(g, key, src, x + size / 2f, y + size * 0.08f, size * 0.46f);
        else if (isCross(key)) cross(g, key, src, x + size / 2f, y + size * 0.08f, size * 0.46f);
        else cube(g, key, src, x + size / 2f, y + size * 0.08f, size * 0.46f);
    }

    /** Two upright planes through the middle of a block space whose top vertex is at (cx, cy). */
    private void cross(GuiGraphics g, String key, AssetSource src, float cx, float cy, float s) {
        TextureCache.Thumb t = thumb(src, key);
        if (t == null) return;
        int tint = tintFor(key);
        float k = s / 16f;
        // plane along x through the middle (z = 0.5), then the plane along z (x = 0.5) in front of it
        face(g, t, new Matrix3x2f(COS30 * k, 0.5f * k, 0, k, cx - 0.5f * COS30 * s, cy + 0.25f * s), tint, 0x18000000);
        face(g, t, new Matrix3x2f(COS30 * k, -0.5f * k, 0, k, cx - 0.5f * COS30 * s, cy + 0.75f * s), tint, 0);
    }

    /** top texture, side texture */
    private String[] blockFaces(String key, AssetSource src) {
        String name = key.substring("textures/block/".length(), key.length() - 4);
        String base = name.replaceAll("_(top|side|bottom|front|end)$", "");
        String top = firstExisting(src, "textures/block/" + base + "_top.png", "textures/block/" + base + "_end.png", key);
        String side = firstExisting(src, "textures/block/" + base + "_side.png", "textures/block/" + base + ".png", key);
        if (name.endsWith("_top") || name.endsWith("_end")) top = key;
        if (name.endsWith("_side")) side = key;
        return new String[]{top, side};
    }

    private String firstExisting(AssetSource src, String... keys) {
        for (String k : keys) if (src.has(k) || lib.vanilla().has(k)) return k;
        return keys[keys.length - 1];
    }

    private static int tintFor(String key) {
        String n = key.substring(key.lastIndexOf('/') + 1);
        if (n.startsWith("grass_block_top") || n.equals("short_grass.png") || n.equals("tall_grass_top.png")
                || n.equals("tall_grass_bottom.png") || n.equals("fern.png") || n.startsWith("large_fern")) return GRASS_TINT;
        if (n.contains("leaves") && !n.contains("cherry") && !n.contains("azalea") && !n.contains("pale")) return FOLIAGE_TINT;
        if (n.equals("vine.png")) return FOLIAGE_TINT;
        return 0xFFFFFFFF;
    }

    // ------------------------------------------------------------------ in game

    /**
     * First-person view, laid out like the real game: sky with blocky clouds, flat grass to
     * the horizon, a thin crosshair, the held item big at the right edge with its thickness
     * showing, and the HUD (hearts, hunger, XP bar, hotbar, off-hand slot) using the pack's own
     * GUI sprites where it has them.
     */
    private void inGame(GuiGraphics g, String key, AssetSource src, int x, int y, int W, int H) {
        g.enableScissor(x, y, x + W, y + H);
        // Real in-game view behind everything when we have one (each pack gets one of them).
        if (!backgrounds.isEmpty()) {
            java.nio.file.Path bg = backgrounds.get(Math.floorMod(src.id().hashCode(), backgrounds.size()));
            TextureCache.Thumb t = cache.custom("bg|" + bg, () -> {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(bg)) {
                    return NativeImage.read(in);
                } catch (Exception e) {
                    return null;
                }
            });
            if (t != null) {
                // cover-fit: fill the box, crop the overflow evenly
                float sc = Math.max(W / (float) t.width(), H / (float) t.height());
                int uw = Math.round(W / sc), vh = Math.round(H / sc);
                g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, (t.width() - uw) / 2f, (t.height() - vh) / 2f,
                        W, H, uw, vh, t.width(), t.height());
                drawHeldAndHud(g, key, src, x, y, W, H);
                g.disableScissor();
                return;
            }
        }
        int horizon = y + H * 54 / 100;
        g.fillGradient(x, y, x + W, horizon, 0xFF7EA6F8, 0xFFB4CDF5);

        // clouds: flat blocky shapes, like the game's
        float cw = W / 64f, ch = H / 36f;
        int[][] clouds = {{4, 3, 10, 2}, {8, 5, 14, 2}, {12, 2, 6, 1}, {30, 4, 12, 2}, {34, 6, 8, 2}, {48, 3, 9, 2}, {52, 5, 6, 1}};
        for (int[] cl : clouds) {
            g.fill(x + Math.round(cl[0] * cw), y + Math.round(cl[1] * ch), x + Math.round((cl[0] + cl[2]) * cw),
                    y + Math.round((cl[1] + cl[3]) * ch), 0xCCF2F6FF);
        }

        // ground: small tiles so it reads as a field going off into the distance
        TextureCache.Thumb grass = thumb(src, "textures/block/grass_block_top.png");
        int t = Math.max(3, H / 12);
        if (grass != null) {
            for (int gy = horizon; gy < y + H; gy += t) {
                for (int gx = x; gx < x + W; gx += t) {
                    g.blit(RenderPipelines.GUI_TEXTURED, grass.id(), gx, gy, 0f, 0f, t, t,
                            grass.width(), grass.frameHeight(), grass.width(), grass.height(), GRASS_TINT);
                }
            }
        } else {
            g.fill(x, horizon, x + W, y + H, 0xFF5E9C3C);
        }
        g.fillGradient(x, horizon, x + W, horizon + H / 10, 0x66B4CDF5, 0x00000000);
        drawHeldAndHud(g, key, src, x, y, W, H);
        g.disableScissor();
    }

    /** Crosshair, the held item and the HUD: everything in front of the world. */
    private void drawHeldAndHud(GuiGraphics g, String key, AssetSource src, int x, int y, int W, int H) {

        // When looking at HUD art (hearts, crosshair...) nothing is held and the HUD is drawn bigger.
        boolean hudFocus = key.startsWith("textures/gui/");
        int bw = W * (hudFocus ? 56 : 30) / 100;
        float k = bw / 182f;

        // crosshair: the pack's own sprite (15x15 in the game)
        int cx = x + W / 2, cy = y + H / 2;
        String crossKey = key.startsWith("textures/gui/sprites/hud/crosshair") ? key : "textures/gui/sprites/hud/crosshair.png";
        TextureCache.Thumb cross = thumb(src, crossKey);
        if (cross != null) {
            int cs = Math.max(5, Math.round(15 * k));
            flat(g, cross, cx - cs / 2, cy - cs / 2, cs, cs);
        } else {
            int arm = Math.max(2, H / 40);
            g.fill(cx - arm, cy, cx + arm + 1, cy + 1, 0xCC202020);
            g.fill(cx, cy - arm, cx + 1, cy + arm + 1, 0xCC202020);
        }

        // End crystals are shown placed in the world, on obsidian, the way you'd see them in a fight.
        if ("end_crystal".equals(dev.cod.packcod.pack.Categories.setOf(key))) {
            placedCrystal(g, src, x, y, W, H);
        } else if (isBreaking(key)) {
            breakingScene(g, key, src, x, y, W, H);
        } else if (key.equals(TOTEM) || key.startsWith("textures/particle/glitter")) {
            totemPop(g, src, x, y, W, H);
        } else if (key.startsWith("textures/particle/")) {
            TextureCache.Thumb sword = heldRender(src, "textures/item/diamond_sword.png");
            if (sword != null) g.blit(RenderPipelines.GUI_TEXTURED, sword.id(), x, y, 0f, 0f, W, H,
                    sword.width(), sword.height(), sword.width(), sword.height());
            particleScene(g, key, src, x, y, W, H);
        } else if (!hudFocus) {
            TextureCache.Thumb held = heldRender(src, key);
            if (held != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, held.id(), x, y, 0f, 0f, W, H,
                        held.width(), held.height(), held.width(), held.height());
            }
        }

        // HUD, scaled from the game's 182px-wide hotbar
        int bh = Math.round(22 * k);
        int bx = x + (W - bw) / 2, by = y + H - bh - Math.max(1, Math.round(k));
        TextureCache.Thumb bar = thumb(src, "textures/gui/sprites/hud/hotbar.png");
        TextureCache.Thumb sel = thumb(src, "textures/gui/sprites/hud/hotbar_selection.png");
        if (bar != null) {
            flat(g, bar, bx, by, bw, bh);
            // empty off-hand slot: the same slot graphic as the hotbar, so it matches
            float bk = bar.width() / 182f;
            int os = Math.round(22 * k);
            region(g, bar, bx - Math.round(29 * k), by, os, os, 0, 0, 22 * bk, 22 * bk);
        } else {
            g.fill(bx, by, bx + bw, by + bh, 0x88000000);
        }
        int slot = Math.round(16 * k);
        TextureCache.Thumb icon = hudFocus ? null
                : thumb(src, key.startsWith("textures/particle/glitter") ? TOTEM
                : key.startsWith("textures/particle/") ? "textures/item/diamond_sword.png"
                : isBreaking(key) ? "textures/item/diamond_pickaxe.png" : key);
        if (icon != null) flat(g, icon, bx + Math.round(3 * k), by + Math.round(3 * k), slot, slot);
        if (sel != null) flat(g, sel, bx - Math.round(k), by - Math.round(k), Math.round(24 * k), Math.round(23 * k));

        // XP bar
        TextureCache.Thumb xp = thumb(src, "textures/gui/sprites/hud/experience_bar_background.png");
        int xpy = by - Math.round(7 * k);
        if (xp != null) flat(g, xp, bx, xpy, bw, Math.max(1, Math.round(5 * k)));
        // hearts (left) and hunger (right)
        TextureCache.Thumb heartBg = thumb(src, "textures/gui/sprites/hud/heart/container.png");
        // if a particular heart is being looked at (poisoned, frozen, hardcore...), fill the row with it
        boolean heartKey = key.startsWith("textures/gui/sprites/hud/heart/") && !key.contains("container");
        TextureCache.Thumb heart = thumb(src, heartKey ? key : "textures/gui/sprites/hud/heart/full.png");
        TextureCache.Thumb foodBg = thumb(src, "textures/gui/sprites/hud/food_empty.png");
        TextureCache.Thumb food = thumb(src, "textures/gui/sprites/hud/food_full.png");
        int hs = Math.max(2, Math.round(9 * k));
        int step = Math.max(2, Math.round(8 * k));
        int hy = xpy - hs - Math.round(k);
        for (int i = 0; i < 10; i++) {
            int hx = bx + i * step;
            if (heartBg != null) flat(g, heartBg, hx, hy, hs, hs);
            if (heart != null) flat(g, heart, hx, hy, hs, hs);
            int fx = bx + bw - hs - i * step;
            if (foodBg != null) flat(g, foodBg, fx, hy, hs, hs);
            if (food != null) flat(g, food, fx, hy, hs, hs);
        }
    }

    // ------------------------------------------------------------------ player

    /** Front view of the player wearing the armor set (or holding the item). */
    private void player(GuiGraphics g, String key, AssetSource src, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xFF2A2622);
        g.fill(x, y + size * 7 / 8, x + size, y + size, 0xFF3A3129);
        float u = size / 36f;
        int ox = Math.round(x + size / 2f - 8 * u);
        int oy = Math.round(y + size / 2f - 16 * u - u);

        TextureCache.Thumb skin = thumb(src, "textures/entity/player/wide/steve.png");
        if (skin != null) {
            float k = skin.width() / 64f;
            part(g, skin, ox, oy, u, 4, 0, 8, 8, 8, 8, k);     // head
            part(g, skin, ox, oy, u, 4, 8, 8, 12, 20, 20, k);  // body
            part(g, skin, ox, oy, u, 0, 8, 4, 12, 44, 20, k);  // right arm (screen left)
            part(g, skin, ox, oy, u, 12, 8, 4, 12, 36, 52, k); // left arm
            part(g, skin, ox, oy, u, 4, 20, 4, 12, 4, 20, k);  // right leg
            part(g, skin, ox, oy, u, 8, 20, 4, 12, 20, 52, k); // left leg
        }

        String mat = armorMaterial(key);
        if (mat != null) {
            String eq = mat.equals("golden") ? "gold" : mat;
            TextureCache.Thumb legs = thumb(src, "textures/entity/equipment/humanoid_leggings/" + eq + ".png");
            TextureCache.Thumb body = thumb(src, "textures/entity/equipment/humanoid/" + eq + ".png");
            if (legs != null) {
                float k = legs.width() / 64f;
                part(g, legs, ox, oy, u, 4, 8, 8, 12, 20, 20, k);
                part(g, legs, ox, oy, u, 4, 20, 4, 12, 4, 20, k);
                part(g, legs, ox, oy, u, 8, 20, 4, 12, 4, 20, k);
            }
            if (body != null) {
                float k = body.width() / 64f;
                part(g, body, ox, oy, u, 3.5f, -0.5f, 9, 9, 8, 8, k); // helmet, a touch bigger than the head
                part(g, body, ox, oy, u, 4, 8, 8, 12, 20, 20, k);
                part(g, body, ox, oy, u, 0, 8, 4, 12, 44, 20, k);
                part(g, body, ox, oy, u, 12, 8, 4, 12, 44, 20, k);
                part(g, body, ox, oy, u, 4, 20, 4, 12, 4, 20, k);
                part(g, body, ox, oy, u, 8, 20, 4, 12, 4, 20, k);
            }
        } else if (key.startsWith("textures/item/")) {
            TextureCache.Thumb t = thumb(src, key);
            if (t != null) {
                g.pose().pushMatrix();
                g.pose().translate(ox + 1 * u, oy + 18 * u);
                g.pose().rotate((float) Math.toRadians(-45));
                int s = Math.round(12 * u);
                flat(g, t, -s / 2, -s, s, s);
                g.pose().popMatrix();
            }
        }
    }

    /** "textures/item/diamond_chestplate.png" -> "diamond"; equipment textures -> their material. */
    static String armorMaterial(String key) {
        String n = key.substring(key.lastIndexOf('/') + 1).replace(".png", "");
        if (Categories.isEquipment(key)) return n;
        for (String piece : new String[]{"_helmet", "_chestplate", "_leggings", "_boots"}) {
            if (n.endsWith(piece)) return n.substring(0, n.length() - piece.length());
        }
        return null;
    }

    private void part(GuiGraphics g, TextureCache.Thumb t, int ox, int oy, float u,
                      float px, float py, float pw, float ph, float tu, float tv, float k) {
        int dx = Math.round(ox + px * u), dy = Math.round(oy + py * u);
        int dw = Math.round((px + pw) * u) - Math.round(px * u);
        int dh = Math.round((py + ph) * u) - Math.round(py * u);
        float sw = pw > 8.5f && ph > 8.5f && pw == ph ? 8 : pw; // helmet drawn larger than its 8x8 source
        float sh = pw > 8.5f && ph > 8.5f && pw == ph ? 8 : ph;
        region(g, t, dx, dy, dw, dh, tu * k, tv * k, sw * k, sh * k);
    }

    // ------------------------------------------------------------------ first-person render

    private TextureCache.Thumb heldRender(AssetSource src, String key) {
        return cache.custom("fp|" + src.id() + "|" + key, () -> {
            if (key.startsWith("textures/block/") && !isCross(key)) {
                String[] faces = blockFaces(key, src);
                NativeImage top = image(src, faces[0]);
                NativeImage side = image(src, faces[1]);
                if (top == null && side == null) return null;
                if (top == null) top = side;
                if (side == null) side = top;
                try {
                    return FirstPersonRenderer.block(top, side, tintFor(faces[0]),
                            faces[1].contains("grass_block") ? 0xFFFFFFFF : tintFor(faces[1]));
                } finally {
                    top.close();
                    if (side != top) side.close();
                }
            }
            NativeImage sprite = image(src, key);
            if (sprite == null) return null;
            try {
                return FirstPersonRenderer.item(sprite, displayFor(src, key));
            } finally {
                sprite.close();
            }
        });
    }

    /** The pack's file, or vanilla's, decoded to RGBA. */
    private NativeImage image(AssetSource src, String key) {
        String k = displayKey(src, key);
        byte[] png = src != null && src.has(k) ? src.read(k) : null;
        if (png == null && src != null && src.has(key)) png = src.read(key);
        if (png == null) png = lib.vanilla().read(key);
        if (png == null) return null;
        try {
            return NativeImage.read(NativeImage.Format.RGBA, new java.io.ByteArrayInputStream(png));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The item's "firstperson_righthand" display transform: from the pack's own item model (packs
     * often resize or move swords), following parents, falling back to vanilla's.
     */
    private FirstPersonRenderer.Display displayFor(AssetSource src, String key) {
        com.google.gson.JsonObject d = displayJson(src, key, "firstperson_righthand");
        if (d == null) return FirstPersonRenderer.Display.HANDHELD;
        float[] t = vec(d, "translation", 0), r = vec(d, "rotation", 0), sc = vec(d, "scale", 1);
        return new FirstPersonRenderer.Display(t[0], t[1], t[2], r[0], r[1], r[2], sc[0], sc[1], sc[2]);
    }

    private final java.util.Map<String, Float> fixedScales = new java.util.HashMap<>();

    /** The item's "fixed" display scale (what the totem pop uses; "pop reducer" packs shrink it). */
    float fixedScale(AssetSource src, String key) {
        return fixedScales.computeIfAbsent((src == null ? "" : src.id()) + "|" + key, k -> {
            com.google.gson.JsonObject d = displayJson(src, key, "fixed");
            if (d == null) return 1f;
            float s = vec(d, "scale", 1)[0];
            return Math.max(0.05f, Math.min(2f, s));
        });
    }

    /** One display context ("fixed", "firstperson_righthand"...) from the item's model chain, or null. */
    private com.google.gson.JsonObject displayJson(AssetSource src, String key, String context) {
        if (!key.startsWith("textures/item/")) return null;
        String name = key.substring("textures/item/".length(), key.length() - 4);
        String model = "models/item/" + name + ".json";
        try {
            byte[] def = readJson(src, "items/" + name + ".json");
            if (def != null) {
                com.google.gson.JsonElement m = findModel(com.google.gson.JsonParser.parseString(new String(def, java.nio.charset.StandardCharsets.UTF_8)));
                if (m != null) model = modelPath(m.getAsString());
            }
            for (int i = 0; i < 10 && model != null; i++) {
                byte[] bytes = readJson(src, model);
                if (bytes == null) break;
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("display") && o.getAsJsonObject("display").has(context)) {
                    return o.getAsJsonObject("display").getAsJsonObject(context);
                }
                model = o.has("parent") ? modelPath(o.get("parent").getAsString()) : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private byte[] readJson(AssetSource src, String path) {
        byte[] b = src != null ? src.read(path) : null;
        return b != null ? b : lib.vanilla().read(path);
    }

    /** First string "model" value in an item definition (handles nested/select/condition defs). */
    private static com.google.gson.JsonElement findModel(com.google.gson.JsonElement e) {
        if (e.isJsonObject()) {
            com.google.gson.JsonObject o = e.getAsJsonObject();
            if (o.has("model") && o.get("model").isJsonPrimitive()) return o.get("model");
            for (var en : o.entrySet()) {
                com.google.gson.JsonElement f = findModel(en.getValue());
                if (f != null) return f;
            }
        } else if (e.isJsonArray()) {
            for (com.google.gson.JsonElement x : e.getAsJsonArray()) {
                com.google.gson.JsonElement f = findModel(x);
                if (f != null) return f;
            }
        }
        return null;
    }

    /** "minecraft:item/handheld" -> "models/item/handheld.json"; other namespaces aren't followed. */
    private static String modelPath(String id) {
        if (id.contains(":")) {
            if (!id.startsWith("minecraft:")) return null;
            id = id.substring("minecraft:".length());
        }
        return "models/" + id + ".json";
    }

    private static float[] vec(com.google.gson.JsonObject d, String k, float def) {
        float[] v = {def, def, def};
        if (d.has(k)) {
            var a = d.getAsJsonArray(k);
            for (int i = 0; i < 3 && i < a.size(); i++) v[i] = a.get(i).getAsFloat();
        }
        return v;
    }

    // ------------------------------------------------------------------ sky

    /** A pack's sky: its custom sky image if it has one, else its sun, moon and clouds over a sky. */
    private void sky(GuiGraphics g, AssetSource src, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
        String custom = null;
        if (src != null && !src.isVanilla()) {
            for (String k : src.entries()) {
                if (k.startsWith("optifine/sky/") && k.endsWith(".png") && (custom == null || k.compareTo(custom) < 0)) custom = k;
            }
        }
        TextureCache.Thumb t = custom != null ? cache.get(src, custom) : null;
        if (t != null) {
            g.fill(x, y, x + w, y + h, 0xFF000000);
            float sc = Math.min(w / (float) t.width(), h / (float) t.height());
            int dw = Math.round(t.width() * sc), dh = Math.round(t.height() * sc);
            g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x + (w - dw) / 2, y + (h - dh) / 2, 0f, 0f, dw, dh,
                    t.width(), t.height(), t.width(), t.height());
        } else {
            g.fillGradient(x, y, x + w, y + h, 0xFF5D8FE8, 0xFFB4CDF5);
            TextureCache.Thumb sun = thumb(src, "textures/environment/sun.png");
            int s = Math.max(8, Math.min(w, h) / 3);
            if (sun != null) flat(g, sun, x + w / 8, y + h / 8, s, s);
            TextureCache.Thumb moon = thumb(src, "textures/environment/moon_phases.png");
            if (moon != null) {
                int mw = moon.width() / 4, mh = moon.height() / 2;
                region(g, moon, x + w - w / 8 - s, y + h / 8, s, s, 0, 0, mw, mh);
            }
            TextureCache.Thumb clouds = thumb(src, "textures/environment/clouds.png");
            if (clouds != null) {
                int ch = Math.max(4, h / 5);
                region(g, clouds, x, y + h - ch - h / 6, w, ch, 0, 0, clouds.width(), clouds.height() / 6f);
            }
        }
        g.disableScissor();
    }

    // ------------------------------------------------------------------ end crystal

    /** A placed end crystal, big and centred, spinning and bobbing like in the world. */
    private void placedCrystal(GuiGraphics g, AssetSource src, int x, int y, int W, int H) {
        float s = H * 0.46f;
        float cx = x + W * 0.5f, cy = y + H * 0.50f + s * 0.95f;

        TextureCache.Thumb t = thumb(src, "textures/entity/end_crystal/end_crystal.png");
        if (t == null) return;
        double time = System.currentTimeMillis() / 1000.0;
        float bob = (float) Math.sin(time * 2.0) * s * 0.12f;
        float k = t.width() / 64f;
        float centerY = cy - s * 0.95f + bob;
        // inner core, then two glass frames turning in opposite directions
        crystalCube(g, t, k, 32, 0, cx, centerY, s * 0.42f, 0f, 0xFFFFFFFF);
        crystalCube(g, t, k, 0, 0, cx, centerY, s * 0.62f, (float) (time * 1.4), 0xFFFFFFFF);
        crystalCube(g, t, k, 0, 0, cx, centerY, s * 0.80f, (float) (-time * 1.0), 0xFFFFFFFF);
    }

    /** One 8x8x8 crystal cube from the entity texture (uv u0,v0 in 64x32 space), centred at (cx, cy), turned by rot. */
    private void crystalCube(GuiGraphics g, TextureCache.Thumb t, float k, int u0, int v0, float cx, float cy,
                             float size, float rot, int tint) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate(rot);
        float s = size / 1.7f;              // edge length so the whole iso cube is about 'size' tall
        float u = s / 16f;
        float ox = 0, oy = -s;              // top vertex so the cube is centred on (0,0)
        faceRegion(g, t, new Matrix3x2f(COS30 * u, 0.5f * u, -COS30 * u, 0.5f * u, ox, oy),
                (u0 + 8) * k, v0 * k, 8 * k, 8 * k, tint, 0);
        faceRegion(g, t, new Matrix3x2f(COS30 * u, 0.5f * u, 0, u, ox - COS30 * s, oy + 0.5f * s),
                (u0 + 8) * k, (v0 + 8) * k, 8 * k, 8 * k, tint, 0x40000000);
        faceRegion(g, t, new Matrix3x2f(COS30 * u, -0.5f * u, 0, u, ox, oy + s),
                (u0 + 16) * k, (v0 + 8) * k, 8 * k, 8 * k, tint, 0x20000000);
        g.pose().popMatrix();
    }

    private void faceRegion(GuiGraphics g, TextureCache.Thumb t, Matrix3x2f m, float u, float v, float uw, float vh,
                            int tint, int shade) {
        g.pose().pushMatrix();
        g.pose().mul(m);
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), 0, 0, u, v, 16, 16, Math.round(uw), Math.round(vh),
                t.width(), t.height(), tint);
        if (shade != 0) g.fill(0, 0, 16, 16, shade);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ totem pop

    static final String TOTEM = "textures/item/totem_of_undying.png";

    /**
     * The totem pop: the totem flies up in front of the camera spinning, hangs there, then
     * shrinks away while green and yellow glitter bursts out. Loops every 2.4 seconds.
     * Packs that shrink the pop (their item model's "fixed" scale) show it smaller, like in game.
     */
    private void totemPop(GuiGraphics g, AssetSource src, int x, int y, int W, int H) {
        double now = System.currentTimeMillis() / 1000.0;
        float p = (float) ((now % 2.4) / 2.4);
        float cx = x + W / 2f, cy = y + H / 2f;

        // glitter first, so the totem sits in front of most of it
        List<String> frames = particleFrames("textures/particle/glitter_0.png");
        if (!frames.isEmpty()) {
            int n = 48;
            for (int i = 0; i < n; i++) {
                float seed = rnd(i * 7 + 1);
                float life = 0.55f + rnd(i * 7 + 2) * 0.45f;
                float start = rnd(i * 7 + 3) * 0.25f;
                float tp = (p - start) / life;
                if (tp < 0 || tp > 1) continue;
                double ang = seed * Math.PI * 2;
                float speed = (0.25f + rnd(i * 7 + 4) * 0.35f) * H;
                float px = cx + (float) Math.cos(ang) * speed * tp;
                float py = cy + (float) Math.sin(ang) * speed * tp * 0.7f - H * 0.1f * tp + H * 0.25f * tp * tp;
                int col = rnd(i * 7 + 5) < 0.55f ? 0xFF66E04A : 0xFFE8E040;
                int alpha = Math.round(255 * Math.min(1f, (1 - tp) * 2.2f));
                int fi = Math.min(frames.size() - 1, (int) (tp * frames.size()));
                TextureCache.Thumb t = thumb(src, frames.get(frames.size() - 1 - fi));
                if (t == null) continue;
                int ps = Math.max(2, Math.round(H / 26f));
                g.blit(RenderPipelines.GUI_TEXTURED, t.id(), Math.round(px - ps / 2f), Math.round(py - ps / 2f), 0f, t.frameV(),
                        ps, ps, t.width(), t.frameHeight(), t.width(), t.height(), (alpha << 24) | (col & 0xFFFFFF));
            }
        }

        TextureCache.Thumb totem = thumb(src, TOTEM);
        if (totem == null) return;
        // grow in fast, hold, then shrink up and away (roughly the game's curve)
        float sc;
        if (p < 0.18f) sc = ease(p / 0.18f) * 1.1f;
        else if (p < 0.62f) sc = 1.1f - 0.1f * ease((p - 0.18f) / 0.44f);
        else sc = 1f - ease((p - 0.62f) / 0.38f);
        if (sc <= 0.01f) return;
        float spin = (float) (Math.PI * 2 * 2.5 * (1 - Math.pow(1 - Math.min(1, p / 0.62f), 3)));
        float base = H * 0.62f * fixedScale(src, TOTEM);
        float sw = base * sc * Math.max(0.08f, Math.abs((float) Math.cos(spin)));
        float sh = base * sc;
        float rise = p > 0.62f ? -H * 0.15f * ease((p - 0.62f) / 0.38f) : H * 0.18f * (1 - ease(Math.min(1, p / 0.18f)));
        int dx = Math.round(cx - sw / 2), dy = Math.round(cy - sh / 2 + rise);
        g.blit(RenderPipelines.GUI_TEXTURED, totem.id(), dx, dy, 0f, totem.frameV(), Math.max(1, Math.round(sw)),
                Math.max(1, Math.round(sh)), totem.width(), totem.frameHeight(), totem.width(), totem.height());
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t);
    }

    /** Repeatable 0..1 noise so particles land in the same places every loop. */
    private static float rnd(int i) {
        int h = i * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    // ------------------------------------------------------------------ particles

    /** All frames of a particle in order (sweep_0..7, glitter_0..7), or just the file itself. */
    List<String> particleFrames(String key) {
        String set = Categories.setOf(key);
        if (lib == null || set == null || !set.startsWith("particle:")) return List.of(key);
        List<String> f = new ArrayList<>(lib.setMembers(set));
        f.sort(java.util.Comparator.comparingInt(Preview::frameNumber));
        return f;
    }

    private static int frameNumber(String k) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("_(\\d+)\\.png$").matcher(k);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** For grid icons: the frame of a particle to show right now (cycles while animating). */
    String particleFrameNow(String key) {
        if (!TextureCache.animate || !key.startsWith("textures/particle/")) return key;
        List<String> f = particleFrames(key);
        if (f.size() < 2) return key;
        return f.get((int) ((System.currentTimeMillis() / 90) % f.size()));
    }

    /** How the game tints these particles. */
    private static int particleTint(String key) {
        String n = key.substring(key.lastIndexOf('/') + 1);
        if (n.startsWith("enchanted_hit")) return 0xFF4DCCFF;
        if (n.startsWith("critical_hit") || n.startsWith("crit")) return 0xFFD8D8D8;
        return 0xFFFFFFFF;
    }

    /** A hit in front of you: the effect big where the target would be, with a spray of small ones. */
    private void particleScene(GuiGraphics g, String key, AssetSource src, int x, int y, int W, int H) {
        List<String> frames = particleFrames(key);
        int tint = particleTint(key);
        long ms = System.currentTimeMillis();
        float cx = x + W * 0.5f, cy = y + H * 0.48f;
        boolean sweep = key.contains("sweep");
        if (sweep) {
            String f = frames.get((int) ((ms / 70) % frames.size()));
            TextureCache.Thumb t = thumb(src, f);
            if (t != null) {
                int s = Math.round(H * 0.55f);
                g.blit(RenderPipelines.GUI_TEXTURED, t.id(), Math.round(cx - s / 2f), Math.round(cy - s / 2f), 0f, t.frameV(),
                        s, s, t.width(), t.frameHeight(), t.width(), t.height(), tint);
            }
            return;
        }
        float p = (ms % 1200) / 1200f;
        int n = 22;
        for (int i = 0; i < n; i++) {
            float tp = (p + rnd(i * 5 + 11)) % 1f;
            double ang = rnd(i * 5 + 12) * Math.PI * 2;
            float dist = (0.04f + rnd(i * 5 + 13) * 0.22f) * H * (0.3f + tp);
            float px = cx + (float) Math.cos(ang) * dist;
            float py = cy + (float) Math.sin(ang) * dist * 0.8f + tp * tp * H * 0.12f;
            int fi = Math.min(frames.size() - 1, (int) (tp * frames.size()));
            TextureCache.Thumb t = thumb(src, frames.get(fi));
            if (t == null) continue;
            int ps = Math.max(2, Math.round(H * (0.05f + rnd(i * 5 + 14) * 0.05f)));
            int alpha = Math.round(255 * Math.min(1f, (1 - tp) * 2f));
            g.blit(RenderPipelines.GUI_TEXTURED, t.id(), Math.round(px - ps / 2f), Math.round(py - ps / 2f), 0f, t.frameV(),
                    ps, ps, t.width(), t.frameHeight(), t.width(), t.height(), (alpha << 24) | (tint & 0xFFFFFF));
        }
        // one big one in the middle so the art itself is easy to see
        TextureCache.Thumb big = thumb(src, frames.get((int) ((ms / 100) % frames.size())));
        if (big != null) {
            int s = Math.round(H * 0.2f);
            g.blit(RenderPipelines.GUI_TEXTURED, big.id(), Math.round(cx - s / 2f), Math.round(cy - s / 2f), 0f, big.frameV(),
                    s, s, big.width(), big.frameHeight(), big.width(), big.height(), tint);
        }
    }

    // ------------------------------------------------------------------ 3D mobs

    /** "humanoid", "skeleton", "creeper", "slim", or null when we don't have a model for this mob. */
    static String mobKind(String key) {
        if (!key.startsWith("textures/entity/") || !key.endsWith(".png")) return null;
        String n = key.substring(key.lastIndexOf('/') + 1);
        if (key.startsWith("textures/entity/creeper/") && n.equals("creeper.png")) return "creeper";
        if (key.startsWith("textures/entity/skeleton/") && !n.contains("overlay")) return "skeleton";
        if (key.startsWith("textures/entity/player/slim/")) return "slim";
        if (key.startsWith("textures/entity/player/wide/")) return "humanoid";
        if (key.startsWith("textures/entity/zombie/") && !n.contains("outer") && !n.contains("overlay")) return "humanoid";
        return null;
    }

    private record Box(float x0, float y0, float z0, float w, float h, float d, int u, int v, boolean top) {}

    private static List<Box> mobBoxes(String kind, boolean tall) {
        List<Box> b = new ArrayList<>();
        switch (kind) {
            case "creeper" -> {
                b.add(new Box(-4, 0, -6, 4, 6, 4, 0, 16, false));   // back legs
                b.add(new Box(0, 0, -6, 4, 6, 4, 0, 16, false));
                b.add(new Box(-4, 6, -2, 8, 12, 4, 16, 16, false)); // body
                b.add(new Box(-4, 0, 2, 4, 6, 4, 0, 16, false));    // front legs
                b.add(new Box(0, 0, 2, 4, 6, 4, 0, 16, false));
                b.add(new Box(-4, 18, -4, 8, 8, 8, 0, 0, true));    // head
            }
            case "skeleton" -> {
                b.add(new Box(-6, 12, -1, 2, 12, 2, 40, 16, true));
                b.add(new Box(-3, 0, -1, 2, 12, 2, 0, 16, false));
                b.add(new Box(-4, 12, -2, 8, 12, 4, 16, 16, false));
                b.add(new Box(1, 0, -1, 2, 12, 2, 0, 16, false));
                b.add(new Box(4, 12, -1, 2, 12, 2, 40, 16, true));
                b.add(new Box(-4, 24, -4, 8, 8, 8, 0, 0, true));
            }
            default -> {
                int aw = kind.equals("slim") ? 3 : 4;
                b.add(new Box(-4 - aw, 12, -2, aw, 12, 4, 40, 16, true));              // right arm
                b.add(new Box(-4, 0, -2, 4, 12, 4, 0, 16, false));                      // right leg
                b.add(new Box(-4, 12, -2, 8, 12, 4, 16, 16, false));                    // body
                b.add(new Box(0, 0, -2, 4, 12, 4, tall ? 16 : 0, tall ? 48 : 16, false)); // left leg
                b.add(new Box(4, 12, -2, aw, 12, 4, tall ? 32 : 40, tall ? 48 : 16, true)); // left arm
                b.add(new Box(-4, 24, -4, 8, 8, 8, 0, 0, true));                        // head
                if (tall) b.add(new Box(-4.5f, 23.5f, -4.5f, 9, 9, 9, 32, 0, true));    // hat layer
            }
        }
        return b;
    }

    /** The mob standing, seen from above and to the right, built from boxes like the game's model. */
    private void mob(GuiGraphics g, String key, AssetSource src, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xFF2A2622);
        g.fill(x, y + size * 7 / 8, x + size, y + size, 0xFF3A3129);
        TextureCache.Thumb t = thumb(src, key);
        String kind = mobKind(key);
        if (t == null || kind == null) return;
        boolean tall = t.height() >= t.width();
        float k = t.width() / 64f;
        float sc = size / (kind.equals("creeper") ? 36f : 42f);
        float ox = x + size / 2f, oy = y + size * 0.84f;
        for (Box b : mobBoxes(kind, tall)) drawBox(g, t, k, b, ox, oy, sc, b.u() == 32 && b.v() == 0 && tall);
    }

    private void drawBox(GuiGraphics g, TextureCache.Thumb t, float k, Box b, float ox, float oy, float sc, boolean overlay) {
        // texture size of the box (overlays use the normal 8x8x8 head layout even though drawn a bit larger)
        float tw = overlay ? 8 : b.w(), th = overlay ? 8 : b.h(), td = overlay ? 8 : b.d();
        float x0 = b.x0(), x1 = b.x0() + b.w(), y1 = b.y0() + b.h(), z0 = b.z0(), z1 = b.z0() + b.d();
        // front (facing you, on the left of the picture)
        float[] f = iso(x0, y1, z1, ox, oy, sc);
        boxFace(g, t, f[0], f[1], b.w() * COS30 * sc, b.w() * 0.5f * sc, 0, b.h() * sc,
                (b.u() + td) * k, (b.v() + td) * k, tw * k, th * k, 0xFFD6D6D6);
        // right side (the mob's left)
        float[] r = iso(x1, y1, z1, ox, oy, sc);
        boxFace(g, t, r[0], r[1], b.d() * COS30 * sc, -b.d() * 0.5f * sc, 0, b.h() * sc,
                (b.u() + td + tw) * k, (b.v() + td) * k, td * k, th * k, 0xFFA8A8A8);
        if (b.top()) {
            float[] tp = iso(x0, y1, z0, ox, oy, sc);
            boxFace(g, t, tp[0], tp[1], b.w() * COS30 * sc, b.w() * 0.5f * sc, -b.d() * COS30 * sc, b.d() * 0.5f * sc,
                    (b.u() + td) * k, b.v() * k, tw * k, td * k, 0xFFFFFFFF);
        }
    }

    private static float[] iso(float x, float y, float z, float ox, float oy, float sc) {
        return new float[]{ox + (x - z) * COS30 * sc, oy + (x + z) * 0.5f * sc - y * sc};
    }

    /** One textured parallelogram: origin, the u edge (ax, ay) and the v edge (bx, by), all in screen pixels. */
    private static void boxFace(GuiGraphics g, TextureCache.Thumb t, float px, float py, float ax, float ay,
                                                float bx, float by, float u, float v, float uw, float vh, int tint) {
        g.pose().pushMatrix();
        g.pose().mul(new Matrix3x2f(ax / 16f, ay / 16f, bx / 16f, by / 16f, px, py));
        float p = 0.35f;
        g.pose().translate(-p, -p);
        g.pose().scale((16 + 2 * p) / 16f, (16 + 2 * p) / 16f);
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), 0, 0, u, v, 16, 16, Math.max(1, Math.round(uw)), Math.max(1, Math.round(vh)),
                t.width(), t.height(), tint);
        g.pose().popMatrix();
    }

    /** Grid icon for mobs: the face. */
    void mobFace(GuiGraphics g, String key, AssetSource src, int x, int y, int size) {
        TextureCache.Thumb t = thumb(src, key);
        if (t == null) return;
        float k = t.width() / 64f;
        region(g, t, x, y, size, size, 8 * k, 8 * k, 8 * k, 8 * k);
    }

    // ------------------------------------------------------------------ block breaking

    static boolean isBreaking(String key) {
        return key.startsWith("textures/block/destroy_stage_");
    }

    /** Which crack to show: cycles 0..9 while animating (hover / in game), else the stage itself. */
    private static String stageKey(String key) {
        int st;
        if (TextureCache.animate) st = (int) ((System.currentTimeMillis() / 160) % 10);
        else {
            st = frameNumber(key);
            if (st == 0 && !key.endsWith("_0.png")) st = 7;
        }
        return "textures/block/destroy_stage_" + st + ".png";
    }

    /** A stone block (the pack's own stone) with the crack drawn over all three faces. */
    private void crackedCube(GuiGraphics g, String key, AssetSource src, float cx, float cy, float s) {
        cube(g, "textures/block/stone.png", src, cx, cy, s);
        TextureCache.Thumb crack = thumb(src, stageKey(key));
        if (crack == null) return;
        float k = s / 16f;
        face(g, crack, new Matrix3x2f(COS30 * k, 0.5f * k, -COS30 * k, 0.5f * k, cx, cy), 0xFFFFFFFF, 0);
        face(g, crack, new Matrix3x2f(COS30 * k, 0.5f * k, 0, k, cx - COS30 * s, cy + 0.5f * s), 0xFFFFFFFF, 0);
        face(g, crack, new Matrix3x2f(COS30 * k, -0.5f * k, 0, k, cx, cy + s), 0xFFFFFFFF, 0);
    }

    /**
     * Mining a block: the block in front of you cracks through all ten stages, then pops into
     * little bits, while you hold the pack's pickaxe.
     */
    private void breakingScene(GuiGraphics g, String key, AssetSource src, int x, int y, int W, int H) {
        long ms = System.currentTimeMillis();
        float cycle = (ms % 2600) / 2600f;           // 0..0.8 cracking, 0.8..1 broken + bits
        float s = H * 0.36f;
        float cx = x + W * 0.5f, cy = y + H * 0.5f - s * 0.95f;
        if (cycle < 0.8f) {
            int stage = Math.min(9, (int) (cycle / 0.8f * 10));
            cube(g, "textures/block/stone.png", src, cx, cy, s);
            TextureCache.Thumb crack = thumb(src, "textures/block/destroy_stage_" + stage + ".png");
            if (crack != null) {
                float k = s / 16f;
                face(g, crack, new Matrix3x2f(COS30 * k, 0.5f * k, -COS30 * k, 0.5f * k, cx, cy), 0xFFFFFFFF, 0);
                face(g, crack, new Matrix3x2f(COS30 * k, 0.5f * k, 0, k, cx - COS30 * s, cy + 0.5f * s), 0xFFFFFFFF, 0);
                face(g, crack, new Matrix3x2f(COS30 * k, -0.5f * k, 0, k, cx, cy + s), 0xFFFFFFFF, 0);
            }
        } else {
            // broken: bits of the block fly out and fall
            float tp = (cycle - 0.8f) / 0.2f;
            TextureCache.Thumb stone = thumb(src, "textures/block/stone.png");
            if (stone != null) {
                for (int i = 0; i < 26; i++) {
                    double ang = rnd(i * 3 + 41) * Math.PI * 2;
                    float sp = (0.15f + rnd(i * 3 + 42) * 0.3f) * H;
                    float px = cx + (float) Math.cos(ang) * sp * tp;
                    float py = cy + s + (float) Math.sin(ang) * sp * tp * 0.6f + tp * tp * H * 0.3f;
                    int ps = Math.max(2, Math.round(H * 0.035f));
                    float u = rnd(i * 3 + 43) * 12, v = rnd(i * 3 + 44) * 12;
                    float kk = stone.width() / 16f;
                    region(g, stone, Math.round(px), Math.round(py), ps, ps, u * kk, v * kk, 4 * kk, 4 * kk);
                }
            }
        }
        // the pickaxe, swinging a little
        TextureCache.Thumb pick = heldRender(src, "textures/item/diamond_pickaxe.png");
        if (pick != null) {
            float swing = cycle < 0.8f ? (float) Math.abs(Math.sin(ms / 1000.0 * Math.PI * 4)) : 0f;
            g.pose().pushMatrix();
            g.pose().translate(x + W * 0.75f, y + H);
            g.pose().rotate(-0.25f * swing);
            g.pose().translate(-(x + W * 0.75f), -(y + H) + swing * H * 0.04f);
            g.blit(RenderPipelines.GUI_TEXTURED, pick.id(), x, y, 0f, 0f, W, H,
                    pick.width(), pick.height(), pick.width(), pick.height());
            g.pose().popMatrix();
        }
    }

    // ------------------------------------------------------------------ primitives

    private TextureCache.Thumb thumb(AssetSource src, String key) {
        TextureCache.Thumb t = null;
        String k = displayKey(src, key);
        if (src != null && src.has(k)) t = cache.get(src, k);
        if (t == null && src != null && src.has(key)) t = cache.get(src, key);
        if (t == null && lib != null) t = cache.get(lib.vanilla(), key);
        return t;
    }

    private static void flat(GuiGraphics g, TextureCache.Thumb t, int x, int y, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, 0f, t.frameV(), w, h,
                t.width(), t.frameHeight(), t.width(), t.height());
    }

    private static void region(GuiGraphics g, TextureCache.Thumb t, int x, int y, int w, int h,
                               float u, float v, float uw, float vh) {
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, u, v, w, h,
                Math.round(uw), Math.round(vh), t.width(), t.height());
    }

    private static void sky(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, SKY_TOP, SKY_BOTTOM);
    }
}
