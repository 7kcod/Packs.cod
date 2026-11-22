package dev.cod.packcod.ui;

import dev.cod.packcod.pack.AssetSource;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Uploads pack textures to the GPU on demand so the grid can draw them.
 * A per-frame budget keeps the screen from hitching when you scroll into a big section.
 */
final class TextureCache {

    /** Set while drawing something the mouse is over: animated textures play only then. */
    static boolean animate;

    /**
     * @param frames frame order from the texture's .mcmeta (null = not animated)
     * @param ticks  how long each of those frames shows, in game ticks (1/20 s)
     */
    record Thumb(Identifier id, int width, int height, int[] frames, int[] ticks) {
        Thumb(Identifier id, int width, int height) {
            this(id, width, height, null, null);
        }

        /** Top of the frame to show right now, in texture pixels. */
        float frameV() {
            if (!animate || frames == null || frames.length < 2) return 0;
            int total = 0;
            for (int t : ticks) total += t;
            long now = (System.currentTimeMillis() / 50) % Math.max(1, total);
            for (int i = 0; i < frames.length; i++) {
                now -= ticks[i];
                if (now < 0) return frames[i] * (float) frameHeight();
            }
            return 0;
        }

        /** Animated textures are vertical strips; we only show the first square frame. */
        int frameHeight() {
            return Math.min(width, height);
        }
    }

    private static final int LOADS_PER_FRAME = 48;

    private final Map<String, Thumb> loaded = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private final List<Identifier> registered = new ArrayList<>();
    private int budget;
    private int counter;

    void beginFrame() {
        budget = LOADS_PER_FRAME;
    }

    Thumb get(AssetSource source, String key) {
        String cacheKey = source.id() + "|" + key;
        Thumb t = loaded.get(cacheKey);
        if (t != null || failed.contains(cacheKey) || budget <= 0) return t;
        budget--;
        try {
            byte[] png = source.read(key);
            if (png == null) {
                failed.add(cacheKey);
                return null;
            }
            NativeImage img = NativeImage.read(png);
            int w = img.getWidth(), h = img.getHeight();
            Identifier id = Identifier.fromNamespaceAndPath("packcod", "thumb/" + (counter++));
            DynamicTexture tex = new DynamicTexture(() -> "packcod:" + cacheKey, img);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            registered.add(id);
            t = h > w ? animated(id, w, h, source.read(key + ".mcmeta")) : new Thumb(id, w, h);
            loaded.put(cacheKey, t);
            return t;
        } catch (Exception e) {
            failed.add(cacheKey);
            return null;
        }
    }

    /** Reads the frame order and timing from a .png.mcmeta (defaults: every frame, 1 tick each). */
    private static Thumb animated(Identifier id, int w, int h, byte[] meta) {
        int count = h / Math.max(1, w);
        int frametime = 1;
        java.util.List<int[]> seq = new java.util.ArrayList<>();
        try {
            if (meta != null) {
                var anim = com.google.gson.JsonParser.parseString(new String(meta, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("animation");
                if (anim != null) {
                    if (anim.has("frametime")) frametime = Math.max(1, anim.get("frametime").getAsInt());
                    if (anim.has("frames")) {
                        for (var f : anim.getAsJsonArray("frames")) {
                            if (f.isJsonPrimitive()) seq.add(new int[]{f.getAsInt(), frametime});
                            else {
                                var o = f.getAsJsonObject();
                                seq.add(new int[]{o.get("index").getAsInt(),
                                        o.has("time") ? Math.max(1, o.get("time").getAsInt()) : frametime});
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (seq.isEmpty()) for (int i = 0; i < count; i++) seq.add(new int[]{i, frametime});
        int[] frames = new int[seq.size()], ticks = new int[seq.size()];
        for (int i = 0; i < seq.size(); i++) {
            frames[i] = Math.min(count - 1, Math.max(0, seq.get(i)[0]));
            ticks[i] = seq.get(i)[1];
        }
        return new Thumb(id, w, h, frames, ticks);
    }

    /** A generated image (e.g. a rendered held item), cached like a texture. Costs more of the frame budget. */
    Thumb custom(String cacheKey, java.util.function.Supplier<NativeImage> maker) {
        Thumb t = loaded.get(cacheKey);
        if (t != null || failed.contains(cacheKey) || budget <= 0) return t;
        budget -= 12;
        try {
            NativeImage img = maker.get();
            if (img == null) {
                failed.add(cacheKey);
                return null;
            }
            int w = img.getWidth(), h = img.getHeight();
            Identifier id = Identifier.fromNamespaceAndPath("packcod", "render/" + (counter++));
            DynamicTexture tex = new DynamicTexture(() -> "packcod:" + cacheKey, img);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            registered.add(id);
            t = new Thumb(id, w, h);
            loaded.put(cacheKey, t);
            return t;
        } catch (Exception e) {
            failed.add(cacheKey);
            return null;
        }
    }

    void clear() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (Identifier id : registered) tm.release(id);
        registered.clear();
        loaded.clear();
        failed.clear();
    }
}
