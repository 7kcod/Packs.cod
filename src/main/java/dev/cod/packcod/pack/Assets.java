package dev.cod.packcod.pack;

/** Which files can be mixed, and how they're grouped into sections. */
public final class Assets {

    /** Top-level folders under assets/minecraft/ that we scan. */
    public static final String[] ROOTS = {"textures", "models", "blockstates", "items", "sounds", "particles"};

    public static boolean isMixable(String key) {
        // Custom skies (OptiFine / FabricSkyBoxes-compatible): images plus their .properties.
        if (key.startsWith("optifine/sky/")) return key.endsWith(".png") || key.endsWith(".properties");
        if (key.startsWith("textures/")) return key.endsWith(".png");
        if (key.startsWith("sounds/")) return key.endsWith(".ogg");
        if (key.startsWith("models/") || key.startsWith("blockstates/")
                || key.startsWith("items/") || key.startsWith("particles/")) return key.endsWith(".json");
        return false;
    }

    public static boolean isTexture(String key) { return key.startsWith("textures/"); }
    public static boolean isSound(String key) { return key.startsWith("sounds/"); }
    public static boolean isJson(String key) { return key.endsWith(".json"); }

    /**
     * textures/block/x.png -> "block"; models/item/x.json -> "models: item";
     * sounds/mob/cow/x.ogg -> "sounds: mob"; blockstates/x.json -> "blockstates".
     */
    public static String sectionOf(String key) {
        String[] parts = key.split("/");
        String root = parts[0];
        String sub = parts.length > 2 ? parts[1] : null;
        return switch (root) {
            case "textures" -> sub == null ? "misc" : sub;
            case "models" -> "models: " + (sub == null ? "misc" : sub);
            case "sounds" -> "sounds: " + (sub == null ? "misc" : sub);
            case "items" -> "item definitions";
            case "particles" -> "particle definitions";
            default -> root;
        };
    }

    /** Sort order: blocks, items, then other textures, then models, definitions, sounds. */
    public static int sectionRank(String section) {
        if (section.equals("block")) return 0;
        if (section.equals("item")) return 1;
        if (section.equals("particle")) return 2;
        if (section.startsWith("models: ")) return 4;
        if (section.equals("blockstates") || section.endsWith("definitions")) return 5;
        if (section.startsWith("sounds: ")) return 6;
        return 3;
    }

    /** "textures/block/oak_log_top.png" -> "oak log top"; sounds keep their last folder: "cow say1". */
    public static String pretty(String key) {
        String[] parts = key.split("/");
        String file = parts[parts.length - 1];
        int dot = file.lastIndexOf('.');
        if (dot > 0) file = file.substring(0, dot);
        if (isSound(key) && parts.length > 2) file = parts[parts.length - 2] + " " + file;
        return file.replace('_', ' ');
    }

    /**
     * For models and item definitions, the texture most likely to represent it in the grid.
     * models/item/diamond_sword.json -> textures/item/diamond_sword.png
     */
    public static String iconTextureFor(String key) {
        String name = key.substring(key.lastIndexOf('/') + 1).replace(".json", ".png");
        if (key.startsWith("models/block/") || key.startsWith("blockstates/")) return "textures/block/" + name;
        if (key.startsWith("models/item/") || key.startsWith("items/")) return "textures/item/" + name;
        if (key.startsWith("particles/")) return "textures/particle/" + name;
        return null;
    }

    private Assets() {
    }
}
