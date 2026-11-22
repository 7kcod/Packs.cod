package dev.cod.packcod.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A resource pack from the resourcepacks folder, either a .zip or an unzipped folder.
 * Understands overlay folders (pack.mcmeta "overlays"), so packs that keep their
 * 1.21.11 files in e.g. "overlay_1_21_11/" show what the game would actually use.
 */
public final class FilePackSource implements AssetSource {

    private static final String ASSETS = "assets/minecraft/";

    private final String id;
    private final String displayName;
    private final int color;
    private final Path folder;
    private final ZipFile zip;
    /** Roots to look in, highest priority first: active overlays (last listed wins), then the base. */
    private final List<String> roots;
    private final Set<String> entries;

    private FilePackSource(String id, String displayName, int color, Path folder, ZipFile zip, List<String> roots, Set<String> entries) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.folder = folder;
        this.zip = zip;
        this.roots = roots;
        this.entries = Collections.unmodifiableSet(entries);
    }

    /** Returns null when the path isn't a pack or has nothing mixable. */
    public static FilePackSource open(Path path, int color) {
        String fileName = path.getFileName().toString();
        try {
            if (Files.isDirectory(path)) return openFolder(path, fileName, color);
            if (fileName.toLowerCase().endsWith(".zip")) return openZip(path, fileName, color);
        } catch (IOException ignored) {
        }
        return null;
    }

    private static FilePackSource openFolder(Path dir, String fileName, int color) throws IOException {
        byte[] meta = Files.isRegularFile(dir.resolve("pack.mcmeta")) ? Files.readAllBytes(dir.resolve("pack.mcmeta")) : null;
        List<String> roots = roots("", meta);
        Set<String> found = new TreeSet<>();
        for (String r : roots) {
            Path base = dir.resolve(r + ASSETS);
            if (!Files.isDirectory(base)) continue;
            try (Stream<Path> walk = Files.walk(base)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    String key = base.relativize(p).toString().replace('\\', '/');
                    if (Assets.isMixable(key)) found.add(key);
                });
            }
        }
        if (found.isEmpty()) return null;
        return new FilePackSource(fileName, clean(fileName), color, dir, null, roots, found);
    }

    private static FilePackSource openZip(Path file, String fileName, int color) throws IOException {
        ZipFile zip = new ZipFile(file.toFile());
        // Find where pack.mcmeta sits; some people zip the folder instead of its contents.
        String prefix = null;
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            String name = en.nextElement().getName();
            if (name.endsWith("pack.mcmeta")) {
                String p = name.substring(0, name.length() - "pack.mcmeta".length());
                if (prefix == null || p.length() < prefix.length()) prefix = p;
            }
        }
        if (prefix == null) prefix = "";
        byte[] meta = null;
        ZipEntry me = zip.getEntry(prefix + "pack.mcmeta");
        if (me != null) {
            try (InputStream in = zip.getInputStream(me)) {
                meta = in.readAllBytes();
            }
        }
        List<String> roots = roots(prefix, meta);
        Set<String> found = new TreeSet<>();
        en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) continue;
            String name = e.getName();
            for (String r : roots) {
                String base = r + ASSETS;
                if (name.startsWith(base)) {
                    String key = name.substring(base.length());
                    if (Assets.isMixable(key)) found.add(key);
                    break;
                }
            }
        }
        if (found.isEmpty()) {
            zip.close();
            return null;
        }
        return new FilePackSource(fileName, clean(fileName.substring(0, fileName.length() - 4)), color, null, zip, roots, found);
    }

    /** Overlay folders that apply to 1.21.11, highest priority first, then the pack's base folder. */
    private static List<String> roots(String prefix, byte[] meta) {
        List<String> active = new ArrayList<>();
        if (meta != null) {
            try {
                JsonObject root = JsonParser.parseString(new String(meta, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("overlays")) {
                    JsonArray list = root.getAsJsonObject("overlays").getAsJsonArray("entries");
                    for (JsonElement el : list) {
                        JsonObject o = el.getAsJsonObject();
                        if (o.has("directory") && appliesToCurrent(o)) active.add(prefix + o.get("directory").getAsString() + "/");
                    }
                }
            } catch (Exception ignored) {
            }
        }
        Collections.reverse(active); // later entries override earlier ones
        active.add(prefix);
        return List.copyOf(active);
    }

    private static boolean appliesToCurrent(JsonObject o) {
        int f = PackExporter.PACK_FORMAT;
        int[] range = null;
        if (o.has("min_format") || o.has("max_format")) {
            range = new int[]{major(o.get("min_format"), 0), major(o.get("max_format"), Integer.MAX_VALUE)};
        } else if (o.has("formats")) {
            JsonElement fm = o.get("formats");
            if (fm.isJsonPrimitive()) {
                range = new int[]{fm.getAsInt(), fm.getAsInt()};
            } else if (fm.isJsonArray()) {
                JsonArray a = fm.getAsJsonArray();
                range = new int[]{a.get(0).getAsInt(), a.get(a.size() - 1).getAsInt()};
            } else if (fm.isJsonObject()) {
                JsonObject r = fm.getAsJsonObject();
                range = new int[]{r.get("min_inclusive").getAsInt(), r.get("max_inclusive").getAsInt()};
            }
        }
        return range != null && f >= range[0] && f <= range[1];
    }

    /** 75 or [75, 0] -> 75 */
    private static int major(JsonElement e, int fallback) {
        if (e == null || e.isJsonNull()) return fallback;
        if (e.isJsonArray()) return e.getAsJsonArray().get(0).getAsInt();
        return e.getAsInt();
    }

    /** Strips formatting codes like §a that pack makers put in file names. */
    private static String clean(String s) {
        return s.replaceAll("§.", "").replace('_', ' ').trim();
    }

    @Override public String id() { return id; }
    @Override public String displayName() { return displayName; }
    @Override public int color() { return color; }
    @Override public boolean isVanilla() { return false; }
    @Override public Set<String> entries() { return entries; }

    @Override
    public byte[] read(String key) {
        try {
            for (String r : roots) {
                if (zip != null) {
                    ZipEntry e = zip.getEntry(r + ASSETS + key);
                    if (e == null) continue;
                    try (InputStream in = zip.getInputStream(e)) {
                        return in.readAllBytes();
                    }
                }
                Path p = folder.resolve(r + ASSETS).resolve(key);
                if (Files.isRegularFile(p)) return Files.readAllBytes(p);
            }
        } catch (IOException | IllegalStateException e) {
            return null;
        }
        return null;
    }

    @Override
    public void close() {
        if (zip != null) {
            try {
                zip.close();
            } catch (IOException ignored) {
            }
        }
    }
}
