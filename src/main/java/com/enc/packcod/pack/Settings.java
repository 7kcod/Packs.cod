package com.enc.packcod.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/** Things that aren't part of a mix: favourite packs, live mode, the resolution filter. */
public final class Settings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings instance;

    /** Pack ids marked with a star. */
    public Set<String> favorites = new TreeSet<>();
    /** When on, leaving pack.cod applies the mix in game automatically. */
    public boolean live;
    /** 0 = all packs, 16 / 32 = only that resolution, 64 = 64x and up. */
    public int resFilter;
    /** Look: palette name, style name, accent override (0 = the palette's own). */
    public String palette = "Cod";
    public String style = "CLASSIC";
    public int accent;
    /** Optional PacksMC API key (pmc_...) so Browse can refresh its list live. */
    public String packsmcKey = "";

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("packcod").resolve("settings.json");
    }

    public static Settings get() {
        if (instance == null) {
            try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
                instance = GSON.fromJson(r, Settings.class);
            } catch (Exception e) {
                instance = null;
            }
            if (instance == null) instance = new Settings();
            if (instance.favorites == null) instance.favorites = new TreeSet<>();
        }
        return instance;
    }

    public void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (Exception ignored) {
        }
    }

    /** Does a pack of this resolution pass the filter? Unknown (0) always passes. */
    public boolean resolutionOk(int res) {
        if (resFilter == 0 || res == 0) return true;
        if (resFilter >= 64) return res >= 64;
        return res == resFilter;
    }
}
