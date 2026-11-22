package com.enc.packcod.pack;

import java.io.Closeable;
import java.util.Set;

/**
 * Something that can hand out assets: the built-in vanilla assets, or one of the
 * resource packs in the resourcepacks folder.
 *
 * Keys are always relative to assets/minecraft/, for example
 * "textures/block/stone.png", "models/item/diamond_sword.json", "sounds/mob/cow/say1.ogg".
 */
public interface AssetSource extends Closeable {

    /** Stable id: "vanilla" or the pack's file name. Saved in the project file. */
    String id();

    String displayName();

    /** ARGB colour for chips and stripes in the UI. */
    int color();

    boolean isVanilla();

    /** Every mixable asset this source has (see {@link Assets#isMixable}). */
    Set<String> entries();

    /** Raw bytes of any file under assets/minecraft/, or null. Works for companions like .png.mcmeta too. */
    byte[] read(String key);

    default boolean has(String key) {
        return entries().contains(key);
    }

    @Override
    default void close() {
    }
}
