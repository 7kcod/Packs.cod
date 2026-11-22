package com.enc.packcod.pack;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Everything found on disk: the sources and which assets each one has. */
public final class PackLibrary {

    private static final int[] COLORS = {
            0xFFC8553D, 0xFF4F8A8B, 0xFFE0B04A, 0xFF7A9E6E, 0xFF9A6C94,
            0xFF5B7DB1, 0xFFD08C60, 0xFFA8B36A, 0xFFB5606F, 0xFF6FA3C7
    };

    private final List<AssetSource> sources;
    private final Map<String, AssetSource> byId = new HashMap<>();
    private final Map<String, List<AssetSource>> holders = new HashMap<>();
    private final Map<String, List<String>> sections;
    private final Map<String, List<String>> categories;
    private final Map<String, List<AssetSource>> setHolders = new HashMap<>();
    private final Map<String, List<String>> setMembers = new HashMap<>();
    /** textures/item/X.png -> packs that change item X through items/X.json or models/item/X.json. */
    private final Map<String, List<AssetSource>> itemModelHolders = new HashMap<>();

    private PackLibrary(List<AssetSource> sources) {
        this.sources = Collections.unmodifiableList(sources);
        TreeMap<String, TreeSet<String>> secs = new TreeMap<>((a, b) -> {
            int ra = Assets.sectionRank(a), rb = Assets.sectionRank(b);
            return ra != rb ? Integer.compare(ra, rb) : a.compareTo(b);
        });
        for (AssetSource s : sources) {
            byId.put(s.id(), s);
            for (String key : s.entries()) {
                String set = Categories.setOf(key);
                if (set != null) {
                    List<AssetSource> sh = setHolders.computeIfAbsent(set, k -> new ArrayList<>());
                    if (sh.isEmpty() || sh.get(sh.size() - 1) != s) sh.add(s);
                    List<String> mem = setMembers.computeIfAbsent(set, k -> new ArrayList<>());
                    if (!mem.contains(key)) mem.add(key);
                }
                holders.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
                if (!s.isVanilla() && (key.startsWith("items/") || key.startsWith("models/item/")) && key.endsWith(".json")) {
                    String name = key.substring(key.lastIndexOf('/') + 1, key.length() - 5);
                    List<AssetSource> ih = itemModelHolders.computeIfAbsent("textures/item/" + name + ".png", k -> new ArrayList<>());
                    if (!ih.contains(s)) ih.add(s);
                }
                secs.computeIfAbsent(Assets.sectionOf(key), k -> new TreeSet<>()).add(key);
            }
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        secs.forEach((k, v) -> out.put(k, List.copyOf(v)));
        this.sections = Collections.unmodifiableMap(out);

        Map<String, List<String>> cats = new LinkedHashMap<>();
        TreeSet<String> all = new TreeSet<>(holders.keySet());
        for (Categories.Category c : Categories.ALL) {
            List<String> keys = new ArrayList<>();
            for (String k : all) if (c.test().test(k)) keys.add(k);
            if (!keys.isEmpty()) cats.put(c.name(), List.copyOf(keys));
        }
        this.categories = Collections.unmodifiableMap(cats);
    }

    /** Reads the resourcepacks folder. Safe to call off the render thread. */
    public static PackLibrary scan(Minecraft mc) {
        List<AssetSource> list = new ArrayList<>();
        list.add(new VanillaSource(mc));
        Path dir = mc.getResourcePackDirectory();
        List<Path> candidates = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase())).forEach(candidates::add);
            } catch (IOException ignored) {
            }
        }
        int c = 0;
        for (Path p : candidates) {
            // pack.cod's own "Try in game" output isn't a source to mix from
            if (p.getFileName().toString().toLowerCase().startsWith("pack.cod preview")) continue;
            FilePackSource pack = FilePackSource.open(p, COLORS[c % COLORS.length]);
            if (pack != null) {
                list.add(pack);
                c++;
            }
        }
        return new PackLibrary(list);
    }

    public List<AssetSource> sources() { return sources; }

    public AssetSource source(String id) { return byId.get(id); }

    public AssetSource vanilla() { return sources.get(0); }

    public Map<String, List<String>> sections() { return sections; }

    /** Category name to sorted keys, only categories that have something. */
    public Map<String, List<String>> categories() { return categories; }

    /** Sources that contain this asset, vanilla first. */
    public List<AssetSource> holders(String key) {
        return holders.getOrDefault(key, List.of());
    }

    /** Packs that have any file of this set (hearts, a block's sides...), vanilla first. */
    public List<AssetSource> setHolders(String set) {
        return setHolders.getOrDefault(set, List.of());
    }

    /** Packs that change this item through its model (e.g. totems pointing at their own texture). */
    public List<AssetSource> itemModelHolders(String key) {
        return itemModelHolders.getOrDefault(key, List.of());
    }

    /** Every pack with a version of this file, by the file itself or (for items) through its model. Vanilla first. */
    public List<AssetSource> allHolders(String key) {
        List<AssetSource> a = holders(key), b = itemModelHolders(key);
        if (b.isEmpty()) return a;
        List<AssetSource> out = new ArrayList<>();
        for (AssetSource s : sources) if (a.contains(s) || b.contains(s)) out.add(s);
        return out;
    }

    public List<String> setMembers(String set) {
        return setMembers.getOrDefault(set, List.of());
    }

    private final Map<String, Integer> resolutions = new HashMap<>();

    private static final String[] RES_PROBES = {
            "textures/block/stone.png", "textures/block/dirt.png", "textures/block/cobblestone.png",
            "textures/item/diamond_sword.png", "textures/block/oak_planks.png", "textures/item/iron_sword.png"
    };

    /** Width of a PNG from its header, or 0. */
    public static int pngWidth(byte[] d) {
        if (d == null || d.length < 24 || (d[0] & 0xFF) != 0x89 || d[1] != 'P') return 0;
        return ((d[16] & 0xFF) << 24) | ((d[17] & 0xFF) << 16) | ((d[18] & 0xFF) << 8) | (d[19] & 0xFF);
    }

    /** Texture resolution of a pack (16, 32, 64...), 0 if it has no block or item textures. */
    public int resolution(AssetSource s) {
        if (s == null) return 0;
        if (s.isVanilla()) return 16;
        return resolutions.computeIfAbsent(s.id(), id -> {
            for (String k : RES_PROBES) if (s.has(k)) {
                int w = pngWidth(s.read(k));
                if (w > 0) return w;
            }
            for (String k : s.entries()) {
                if ((k.startsWith("textures/block/") || k.startsWith("textures/item/")) && k.endsWith(".png")) {
                    int w = pngWidth(s.read(k));
                    if (w > 0) return w;
                }
            }
            return 0;
        });
    }

    /** Applies the resolution filter (vanilla always stays) and puts starred packs first after vanilla. */
    public List<AssetSource> shown(List<AssetSource> list) {
        Settings st = Settings.get();
        List<AssetSource> out = new ArrayList<>();
        List<AssetSource> rest = new ArrayList<>();
        for (AssetSource s : list) {
            if (s.isVanilla()) out.add(s);
            else if (st.resolutionOk(resolution(s))) (st.favorites.contains(s.id()) ? out : rest).add(s);
        }
        out.addAll(rest);
        return out;
    }

    public void close() {
        sources.forEach(AssetSource::close);
    }
}
