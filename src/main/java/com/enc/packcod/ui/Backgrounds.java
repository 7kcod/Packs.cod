package com.enc.packcod.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Real in-game views (no HUD, no hand) used behind the "In game" preview.
 * Captured automatically when you type /packcod in a world; the newest 4 are kept in
 * config/packcod/backgrounds/.
 */
public final class Backgrounds {

    public static final int KEEP = 4;

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("packcod").resolve("backgrounds");
    }

    /** Saves a capture and drops the oldest beyond KEEP. Closes the image. */
    public static void save(NativeImage img) {
        try {
            Files.createDirectories(dir());
            img.writeToFile(dir().resolve("bg-" + System.currentTimeMillis() + ".png"));
            List<Path> all = all();
            for (int i = KEEP; i < all.size(); i++) Files.deleteIfExists(all.get(i));
        } catch (Exception ignored) {
        } finally {
            img.close();
        }
    }

    /** Newest first, at most KEEP. */
    public static List<Path> list() {
        List<Path> a = all();
        return a.size() > KEEP ? a.subList(0, KEEP) : a;
    }

    private static List<Path> all() {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir())) return out;
        try (Stream<Path> s = Files.list(dir())) {
            s.filter(p -> p.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .forEach(out::add);
        } catch (Exception ignored) {
        }
        return out;
    }

    private Backgrounds() {
    }
}
