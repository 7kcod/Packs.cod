package dev.cod.packcod.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a plain resource pack zip. The result has no dependency on this mod:
 * anyone can drop it into their resourcepacks folder.
 */
public final class PackExporter {

    /** Resource pack format for Minecraft 1.21.11. */
    public static final int PACK_FORMAT = 75;

    private static final String ASSETS = "assets/minecraft/";
    private static final String[] ICON_TILES = {
            "textures/block/grass_block_side.png", "textures/item/diamond_sword.png",
            "textures/block/oak_planks.png", "textures/item/cod.png"
    };

    public record Result(Path file, int assets) {}

    public static Result export(PackLibrary lib, MixProject project, Path outDir) throws IOException {
        return export(lib, project, outDir, safeName(project.name));
    }

    public static Result export(PackLibrary lib, MixProject project, Path outDir, String base) throws IOException {
        Files.createDirectories(outDir);
        Path tmp = Files.createTempFile(outDir, ".packcod-", ".tmp");
        Map<String, Integer> credits = new TreeMap<>();
        Set<String> written = new HashSet<>();

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp))) {
            put(zip, "pack.mcmeta", mcmeta(project).getBytes(StandardCharsets.UTF_8));
            byte[] icon = customIcon(project.iconPath);
            if (icon == null) icon = buildIcon(lib, project);
            if (icon != null) put(zip, "pack.png", icon);

            // 1) Everything the player picked explicitly.
            for (Map.Entry<String, String> e : project.picks.entrySet()) {
                AssetSource src = lib.source(e.getValue());
                if (src == null || src.isVanilla()) continue;
                if (copy(zip, src, e.getKey(), written)) credits.merge(src.displayName(), 1, Integer::sum);
            }
            // 2) An item texture picked from a pack brings that pack's item definition and model with it,
            //    so 3D / custom-shaped items come out looking the way they did in the preview.
            for (Map.Entry<String, String> e : project.picks.entrySet()) {
                String k = e.getKey();
                if (!k.startsWith("textures/item/")) continue;
                AssetSource src = lib.source(e.getValue());
                if (src == null || src.isVanilla()) continue;
                String name = k.substring("textures/item/".length(), k.length() - 4);
                // Armor: also bring the sheets drawn on the player, so it looks right when worn.
                for (String piece : new String[]{"_helmet", "_chestplate", "_leggings", "_boots"}) {
                    if (!name.endsWith(piece)) continue;
                    String mat = name.substring(0, name.length() - piece.length());
                    if (mat.equals("golden")) mat = "gold";
                    for (String sheet : new String[]{"textures/entity/equipment/humanoid/" + mat + ".png",
                            "textures/entity/equipment/humanoid_leggings/" + mat + ".png"}) {
                        if (!project.picks.containsKey(sheet) && src.has(sheet)) copy(zip, src, sheet, written);
                    }
                }
                for (String extra : new String[]{"items/" + name + ".json", "models/item/" + name + ".json"}) {
                    if (project.picks.containsKey(extra) || !src.has(extra)) continue;
                    copy(zip, src, extra, written);
                    if (extra.startsWith("models/")) {
                        for (String tex : referencedTextures(src.read(extra))) {
                            if (!project.picks.containsKey(tex) && src.has(tex)) copy(zip, src, tex, written);
                        }
                    }
                }
            }
            // 3) Textures a picked model points at, taken from the same pack, unless the player chose them separately.
            for (Map.Entry<String, String> e : project.picks.entrySet()) {
                if (!e.getKey().startsWith("models/")) continue;
                AssetSource src = lib.source(e.getValue());
                if (src == null || src.isVanilla()) continue;
                for (String tex : referencedTextures(src.read(e.getKey()))) {
                    if (!project.picks.containsKey(tex) && src.has(tex)) copy(zip, src, tex, written);
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(project.name).append('\n');
            sb.append("Mixed on ").append(LocalDate.now()).append(" with pack.cod\n\n");
            sb.append("Assets taken from:\n");
            credits.forEach((name, n) -> sb.append("  ").append(name).append("  (").append(n).append(")\n"));
            sb.append("\nCredit the original pack authors if you share this further.\n");
            put(zip, "credits.txt", sb.toString().getBytes(StandardCharsets.UTF_8));
        }

        Path target = outDir.resolve(base + ".zip");
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException locked) {
            // On Windows the old zip is locked while the game has it enabled. Use a new name instead.
            int n = 2;
            do {
                target = outDir.resolve(base + " (" + n++ + ").zip");
            } while (Files.exists(target));
            Files.move(tmp, target);
        }
        return new Result(target, written.size());
    }

    private static boolean copy(ZipOutputStream zip, AssetSource src, String key, Set<String> written) throws IOException {
        if (written.contains(key)) return false;
        byte[] data = src.read(key);
        if (data == null) return false;
        put(zip, ASSETS + key, data);
        written.add(key);
        if (key.endsWith(".png")) {
            byte[] meta = src.read(key + ".mcmeta"); // animations, emissive settings, etc.
            if (meta != null) put(zip, ASSETS + key + ".mcmeta", meta);
        }
        return true;
    }

    /** "textures": {"all": "minecraft:block/stone"} -> textures/block/stone.png */
    private static Set<String> referencedTextures(byte[] json) {
        Set<String> out = new HashSet<>();
        if (json == null) return out;
        try {
            JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("textures") && root.get("textures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> t : root.getAsJsonObject("textures").entrySet()) {
                    if (!t.getValue().isJsonPrimitive()) continue;
                    String v = t.getValue().getAsString();
                    if (v.startsWith("#")) continue;
                    if (v.contains(":")) {
                        if (!v.startsWith("minecraft:")) continue;
                        v = v.substring("minecraft:".length());
                    }
                    out.add("textures/" + v + ".png");
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Description shown under the pack in the game: the name in its colour, then "Made with pack.cod". */
    private static String description(MixProject p) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        com.google.gson.JsonObject name = new com.google.gson.JsonObject();
        name.addProperty("text", p.name == null || p.name.isBlank() ? "My Mix" : p.name);
        name.addProperty("color", String.format("#%06X", p.nameColor & 0xFFFFFF));
        a.add(name);
        com.google.gson.JsonObject made = new com.google.gson.JsonObject();
        made.addProperty("text", "\nMade with pack.cod");
        made.addProperty("color", "gray");
        a.add(made);
        return a.toString();
    }

    /** The chosen picture, cropped to a square from the middle and scaled to 128x128 PNG. */
    public static byte[] customIcon(String path) {
        if (path == null) return null;
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.File(path));
            if (img == null) return null;
            int s = Math.min(img.getWidth(), img.getHeight());
            java.awt.image.BufferedImage sq = img.getSubimage((img.getWidth() - s) / 2, (img.getHeight() - s) / 2, s, s);
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    s > 128 ? java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR : java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(sq, 0, 0, 128, 128, null);
            g.dispose();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(out, "png", bo);
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** The icon that would be used right now (custom picture, else the automatic 2x2). */
    public static byte[] iconFor(PackLibrary lib, MixProject project) {
        byte[] b = customIcon(project.iconPath);
        return b != null ? b : buildIcon(lib, project);
    }

    private static String mcmeta(MixProject p) {
        return "{\n" +
                "  \"pack\": {\n" +
                "    \"description\": " + description(p) + ",\n" +
                "    \"pack_format\": " + PACK_FORMAT + ",\n" +
                "    \"min_format\": " + PACK_FORMAT + ",\n" +
                "    \"max_format\": " + PACK_FORMAT + "\n" +
                "  }\n" +
                "}\n";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    public static String safeName(String s) {
        String n = s == null ? "" : s.replaceAll("[^A-Za-z0-9 _\\-.]", "").trim();
        return n.isEmpty() ? "My Mix" : n;
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    /** 64x64 icon: four textures from the mix in a 2x2 grid. */
    private static byte[] buildIcon(PackLibrary lib, MixProject project) {
        try (NativeImage icon = new NativeImage(64, 64, true)) {
            for (int t = 0; t < ICON_TILES.length; t++) {
                String key = ICON_TILES[t];
                AssetSource src = lib.source(project.pickFor(key));
                if (src == null) src = lib.vanilla();
                byte[] png = src.read(key);
                if (png == null) png = lib.vanilla().read(key);
                if (png == null) continue;
                try (NativeImage img = NativeImage.read(png)) {
                    int side = Math.min(img.getWidth(), img.getHeight());
                    int ox = (t % 2) * 32, oy = (t / 2) * 32;
                    for (int y = 0; y < 32; y++) {
                        for (int x = 0; x < 32; x++) {
                            icon.setPixel(ox + x, oy + y, img.getPixel(x * side / 32, y * side / 32));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            Path f = Files.createTempFile("packcod-icon", ".png");
            try {
                icon.writeToFile(f);
                return Files.readAllBytes(f);
            } finally {
                Files.deleteIfExists(f);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private PackExporter() {
    }
}
