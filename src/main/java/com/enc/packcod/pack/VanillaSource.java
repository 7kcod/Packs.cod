package com.enc.packcod.pack;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** The game's own assets. Used for previews and as the "Default" choice; never copied into exports. */
public final class VanillaSource implements AssetSource {

    public static final String ID = "vanilla";

    private final VanillaPackResources resources;
    private final Set<String> entries;

    public VanillaSource(Minecraft mc) {
        this.resources = mc.getVanillaPackResources();
        Set<String> found = new TreeSet<>();
        for (String root : Assets.ROOTS) {
            try {
                resources.listResources(PackType.CLIENT_RESOURCES, Identifier.DEFAULT_NAMESPACE, root, (id, supplier) -> {
                    if (Assets.isMixable(id.getPath())) found.add(id.getPath());
                });
            } catch (Exception ignored) {
                // Some roots (sounds) live in the asset index and may not be listable on every setup.
            }
        }
        this.entries = Collections.unmodifiableSet(found);
    }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Default"; }
    @Override public int color() { return 0xFF8C857B; }
    @Override public boolean isVanilla() { return true; }
    @Override public Set<String> entries() { return entries; }

    @Override
    public byte[] read(String key) {
        try {
            IoSupplier<InputStream> supplier = resources.getResource(PackType.CLIENT_RESOURCES, Identifier.withDefaultNamespace(key));
            if (supplier == null) return null;
            try (InputStream in = supplier.get()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
