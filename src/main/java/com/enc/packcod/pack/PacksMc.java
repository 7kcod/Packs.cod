package com.enc.packcod.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The Browse tab's data: PacksMC's most downloaded packs, and the resource pack zips sitting in
 * your Downloads folder waiting to be installed.
 *
 * PacksMC only allows downloads through its own site (that's how its creators get credit and
 * earnings), so "Get" opens the pack's page and pack.cod picks the zip up from Downloads.
 * The list ships with the mod; with a PacksMC API key in settings it can be refreshed live
 * through their official API.
 */
public final class PacksMc {

    public record Pack(String slug, String name, String author, String res, List<String> tags, long downloads) {
        public String url() { return "https://packsmc.com/pack/" + slug; }
    }

    public record Found(Path file, String name, long size, long modified) {}

    private PacksMc() {
    }

    /** The list bundled with the mod. */
    public static List<Pack> bundled() {
        try (InputStream in = PacksMc.class.getResourceAsStream("/assets/packcod/browse_top50.json")) {
            if (in == null) return List.of();
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return parse(o.getAsJsonArray("packs"), false);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String bundledDate() {
        try (InputStream in = PacksMc.class.getResourceAsStream("/assets/packcod/browse_top50.json")) {
            if (in == null) return "";
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().get("date").getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Live top 50 through PacksMC's official API (needs an approved key). Throws with a readable message. */
    public static List<Pack> fetchLive(String key) throws Exception {
        HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        HttpRequest r = HttpRequest.newBuilder(URI.create("https://packsmc.com/api/v1/packs?sort=downloads&limit=50"))
                .header("Authorization", "Bearer " + key.trim())
                .header("User-Agent", "pack.cod (Minecraft mod)")
                .timeout(Duration.ofSeconds(12)).GET().build();
        HttpResponse<String> resp = c.send(r, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            String msg = "HTTP " + resp.statusCode();
            try {
                msg = JsonParser.parseString(resp.body()).getAsJsonObject().getAsJsonObject("error").get("message").getAsString();
            } catch (Exception ignored) {
            }
            throw new Exception(msg);
        }
        return parse(JsonParser.parseString(resp.body()).getAsJsonObject().getAsJsonArray("data"), true);
    }

    private static List<Pack> parse(JsonArray arr, boolean api) {
        List<Pack> out = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject p = e.getAsJsonObject();
            List<String> tags = new ArrayList<>();
            JsonArray t = p.has(api ? "gamemodes" : "tags") && p.get(api ? "gamemodes" : "tags").isJsonArray()
                    ? p.getAsJsonArray(api ? "gamemodes" : "tags") : new JsonArray();
            for (JsonElement x : t) tags.add(pretty(x.getAsString()));
            String author = api ? (p.has("author") && p.get("author").isJsonObject()
                    ? p.getAsJsonObject("author").get("username").getAsString() : "") : str(p, "author");
            out.add(new Pack(str(p, "slug"), str(p, "name"), author, str(p, api ? "resolution" : "res"),
                    tags, p.has("downloads") ? p.get("downloads").getAsLong() : 0));
        }
        return out;
    }

    private static String pretty(String tag) {
        String s = tag.replace("PVP_", "").replace('_', ' ').toLowerCase(Locale.ROOT);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    /** Lowercase letters and digits only, for matching names to file names. */
    public static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("§.", "").replaceAll("[^a-z0-9]", "");
    }

    /** Is a pack with this name already in the resourcepacks folder? */
    public static boolean installed(Pack p, List<String> installedNorms) {
        String n = norm(p.name());
        if (n.length() < 3) return false;
        for (String f : installedNorms) if (f.contains(n) || (f.length() >= 5 && n.contains(f))) return true;
        return false;
    }

    public static List<String> installedNorms(Path resourcepacks) {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(resourcepacks)) {
            s.forEach(p -> out.add(norm(p.getFileName().toString().replaceAll("\\.zip$", ""))));
        } catch (Exception ignored) {
        }
        return out;
    }

    public static Path downloadsDir() {
        return Path.of(System.getProperty("user.home"), "Downloads");
    }

    private static final Map<String, Boolean> zipCheck = new HashMap<>();

    /**
     * Resource pack zips in Downloads (newest first) that aren't in the resourcepacks folder yet.
     * Only zips that really are packs (have a pack.mcmeta) count.
     */
    public static List<Found> scanDownloads(Path resourcepacks) {
        List<Found> out = new ArrayList<>();
        Path dir = downloadsDir();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> zips = s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")
                    && Files.isRegularFile(p)).toList();
            for (Path z : zips) {
                long mod = Files.getLastModifiedTime(z).toMillis();
                long size = Files.size(z);
                String name = z.getFileName().toString();
                if (Files.exists(resourcepacks.resolve(name))) continue;
                String ck = z + "|" + mod + "|" + size;
                Boolean pack = zipCheck.get(ck);
                if (pack == null) {
                    pack = isPack(z);
                    zipCheck.put(ck, pack);
                }
                if (pack) out.add(new Found(z, name.substring(0, name.length() - 4), size, mod));
            }
        } catch (Exception ignored) {
        }
        out.sort((a, b) -> Long.compare(b.modified(), a.modified()));
        return out;
    }

    private static boolean isPack(Path zip) {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            if (z.getEntry("pack.mcmeta") != null) return true;
            // some packs are zipped with an extra folder on top
            var en = z.entries();
            int n = 0;
            while (en.hasMoreElements() && n++ < 4000) {
                ZipEntry e = en.nextElement();
                String nm = e.getName();
                if (nm.endsWith("/pack.mcmeta") && nm.indexOf('/') == nm.lastIndexOf('/')) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Copies a downloaded pack into resourcepacks (the original stays in Downloads). */
    public static Path install(Found f, Path resourcepacks) throws Exception {
        Path target = resourcepacks.resolve(f.file().getFileName().toString());
        Files.copy(f.file(), target);
        return target;
    }
}
