package dev.cod.packcod.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** The player's choices. Autosaved to config/packcod/project.json so nothing is lost between sessions. */
public final class MixProject {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String name = "My Mix";
    public String description = "Made with pack.cod";
    /** Colour of the pack's name line in the resource pack list (RGB). */
    public int nameColor = 0xE8A33D;
    /** Picture file to use as pack.png, or null for the automatic one. */
    public String iconPath;
    /** asset key (e.g. textures/block/stone.png) -> source id. Missing means "Default". */
    public Map<String, String> picks = new TreeMap<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("packcod").resolve("project.json");
    }

    public static MixProject load() {
        Path f = file();
        if (Files.isRegularFile(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                MixProject p = GSON.fromJson(r, MixProject.class);
                if (p != null) {
                    if (p.picks == null) p.picks = new TreeMap<>();
                    else p.picks = new TreeMap<>(p.picks);
                    if (p.name == null) p.name = "My Mix";
                    if (p.description == null) p.description = "Made with pack.cod";
                    return p;
                }
            } catch (Exception ignored) {
            }
        }
        return new MixProject();
    }

    public void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException ignored) {
        }
    }

    /** Drops picks that point at packs that were removed from the folder. */
    public void prune(PackLibrary lib) {
        picks.entrySet().removeIf(e -> {
            AssetSource s = lib.source(e.getValue());
            return s == null || s.isVanilla() || (!s.has(e.getKey()) && !lib.itemModelHolders(e.getKey()).contains(s));
        });
    }

    public String pickFor(String rel) {
        return picks.getOrDefault(rel, VanillaSource.ID);
    }

    public void set(String rel, AssetSource source) {
        if (source == null || source.isVanilla()) picks.remove(rel);
        else picks.put(rel, source.id());
    }

    // ---------- saved mixes ----------

    public static Path mixesDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("packcod").resolve("mixes");
    }

    private static String safe(String name) {
        String s = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "Mix" : s;
    }

    public void copyFrom(MixProject o) {
        name = o.name == null ? "My Mix" : o.name;
        description = o.description == null ? "Made with pack.cod" : o.description;
        nameColor = o.nameColor;
        iconPath = o.iconPath;
        picks = new TreeMap<>(o.picks == null ? Map.of() : o.picks);
    }

    /** Saves a copy of this mix under its name. */
    public void saveAs(String n) {
        try {
            Files.createDirectories(mixesDir());
            try (Writer w = Files.newBufferedWriter(mixesDir().resolve(safe(n) + ".json"), StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException ignored) {
        }
    }

    public static List<String> savedNames() {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(mixesDir())) return out;
        try (Stream<Path> st = Files.list(mixesDir())) {
            st.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .forEach(f -> {
                        String fn = f.getFileName().toString();
                        out.add(fn.substring(0, fn.length() - 5));
                    });
        } catch (IOException ignored) {
        }
        return out;
    }

    public static MixProject loadSaved(String n) {
        try (Reader r = Files.newBufferedReader(mixesDir().resolve(safe(n) + ".json"), StandardCharsets.UTF_8)) {
            MixProject p = GSON.fromJson(r, MixProject.class);
            if (p != null) {
                if (p.picks == null) p.picks = new TreeMap<>();
                else p.picks = new TreeMap<>(p.picks);
                if (p.name == null) p.name = n;
                if (p.description == null) p.description = "Made with pack.cod";
                return p;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void deleteSaved(String n) {
        try {
            Files.deleteIfExists(mixesDir().resolve(safe(n) + ".json"));
        } catch (IOException ignored) {
        }
    }

    // ---------- mix codes ----------

    public static final String CODE_PREFIX = "pcod1:";

    private static final class Code {
        String n;
        int c;
        Map<String, String> p;
    }

    /** A short text that rebuilds this mix on another computer that has the same packs. */
    public String toCode() {
        Code c = new Code();
        c.n = name;
        c.c = nameColor;
        c.p = picks;
        byte[] raw = new Gson().toJson(c).getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /** Reads a mix code, or returns null if it isn't one. */
    public static MixProject fromCode(String code) {
        if (code == null) return null;
        code = code.trim();
        int at = code.indexOf(CODE_PREFIX);
        if (at < 0) return null;
        code = code.substring(at + CODE_PREFIX.length()).split("\\s")[0];
        try {
            byte[] data = Base64.getUrlDecoder().decode(code);
            Inflater inf = new Inflater();
            inf.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int guard = 0;
            while (!inf.finished() && guard++ < 10000) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                out.write(buf, 0, n);
            }
            inf.end();
            Code c = new Gson().fromJson(out.toString(StandardCharsets.UTF_8), Code.class);
            if (c == null) return null;
            MixProject p = new MixProject();
            if (c.n != null) p.name = c.n;
            p.nameColor = c.c;
            if (c.p != null) p.picks = new TreeMap<>(c.p);
            return p;
        } catch (Exception e) {
            return null;
        }
    }
}
