package dev.cod.packcod.pack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Hand-made categories that cut across Minecraft's folders, so "PvP" shows swords, armor,
 * pearls, totems, potions, crystals, hearts and fire all in one place.
 * An asset can be in more than one category.
 */
public final class Categories {

    public record Category(String name, Predicate<String> test) {}

    /**
     * How the list on the left is laid out. A node with children folds open; clicking its name
     * shows everything in it (its own category, when it has one).
     */
    public record Node(String label, String category, List<Node> children) {
        static Node leaf(String label, String category) { return new Node(label, category, List.of()); }
        static Node group(String label, String category, Node... kids) { return new Node(label, category, List.of(kids)); }
        public boolean isGroup() { return !children.isEmpty(); }
    }

    // ------------------------------------------------------------------ kits
    // Items from the PvP tier-test gamemodes (MCTiers main tiers and SubTiers). The Mace kit
    // follows a published kit list; the rest are the usual loadouts for each mode.

    private static final String[] DIA = {"diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots"};
    private static final String[] NETH = {"netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots"};
    private static final String[] IRON = {"iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"};
    private static final String[] SHIELD = {"entity/shield_base_nopattern", "entity/shield/base_nopattern", "entity/shield_base", "entity/shield/base"};
    private static final String[] POTS = {"splash_potion", "potion", "potion_overlay", "lingering_potion"};
    private static final String[] CRYSTAL = {"end_crystal", "block/obsidian", "block/respawn_anchor_top", "block/respawn_anchor_side0",
            "block/glowstone"};

    private static Set<String> keys(Object... parts) {
        Set<String> out = new HashSet<>();
        for (Object o : parts) {
            for (String s : o instanceof String[] a ? a : new String[]{(String) o}) {
                out.add(s.contains("/") ? "textures/" + s + ".png" : "textures/item/" + s + ".png");
            }
        }
        return out;
    }

    private static Category kit(String name, Object... parts) {
        Set<String> k = keys(parts);
        return new Category("Kit: " + name, k::contains);
    }

    private static final List<Category> KITS = List.of(
            kit("Diamond SMP", DIA, "diamond_sword", "diamond_axe", SHIELD, "golden_apple", "ender_pearl",
                    "experience_bottle", POTS, "block/cobweb"),
            kit("Crystal", NETH, "netherite_sword", "netherite_pickaxe", CRYSTAL, "totem_of_undying", "golden_apple",
                    "ender_pearl", "experience_bottle", POTS, SHIELD),
            kit("Mace", NETH, "netherite_sword", "netherite_axe", "mace", SHIELD, "elytra", "golden_apple",
                    "ender_pearl", "wind_charge", "totem_of_undying", POTS),
            kit("Sword", DIA, "diamond_sword"),
            kit("Axe", DIA, "diamond_axe", "diamond_sword", SHIELD, "bow", "crossbow_standby", "arrow"),
            kit("UHC", DIA, "diamond_sword", "diamond_axe", "bow", "crossbow_standby", "arrow", "golden_apple",
                    "lava_bucket", "water_bucket", "block/cobweb", "block/cobblestone", "block/oak_planks", SHIELD),
            kit("Pot", DIA, "diamond_sword", POTS, "cooked_beef"),
            kit("NethOP", NETH, "netherite_sword", POTS, "golden_apple", "totem_of_undying", "ender_pearl", "experience_bottle"),
            kit("SMP", NETH, "netherite_sword", "netherite_axe", SHIELD, "totem_of_undying", "golden_apple", "ender_pearl",
                    "experience_bottle", POTS, "block/cobweb"),
            // sub tiers
            kit("Cart", DIA, "bow", "crossbow_standby", "arrow", "tnt_minecart", "block/rail", "block/powered_rail",
                    "flint_and_steel", POTS, "block/tnt_side"),
            kit("Bed", DIA, "diamond_sword", "diamond_pickaxe", "entity/bed/red", "block/obsidian", "golden_apple"),
            kit("Creeper", DIA, "diamond_sword", "creeper_spawn_egg", "flint_and_steel", "totem_of_undying", "golden_apple"),
            kit("OG Vanilla", DIA, "diamond_sword", "end_crystal", "block/obsidian", "golden_apple", "totem_of_undying",
                    "ender_pearl"),
            kit("Speed", DIA, "diamond_sword", POTS, "golden_apple"),
            kit("Manhunt", IRON, "iron_sword", "iron_axe", "bow", "arrow", "compass", "lava_bucket", "water_bucket",
                    "ender_pearl", "flint_and_steel"),
            kit("Iron Pot", IRON, "iron_sword", POTS, "cooked_beef"),
            kit("Diamond Vanilla", DIA, "diamond_sword", "diamond_pickaxe", CRYSTAL, "totem_of_undying", "golden_apple",
                    "ender_pearl", "experience_bottle"),
            kit("Trident", NETH, "trident", "water_bucket", "totem_of_undying", "golden_apple"),
            kit("Elytra", NETH, "elytra", "firework_rocket", "mace", "totem_of_undying", "golden_apple", "ender_pearl"),
            kit("Bow", "bow", "arrow", "spectral_arrow", "tipped_arrow_base", "tipped_arrow_head", "golden_apple")
    );

    /** The left-hand list: PvP and Kits fold open, the rest are plain entries. */
    public static final List<Node> TREE = List.of(
            Node.group("PvP", "PvP",
                    Node.leaf("Swords", "Swords"),
                    Node.leaf("Hearts", "Hearts"),
                    Node.leaf("Tools", "Tools"),
                    Node.leaf("UI", "UI"),
                    Node.leaf("Crosshair", "Crosshairs"),
                    Node.leaf("Armor", "Armor"),
                    Node.leaf("Shield", "Shield"),
                    Node.leaf("Pots", "Pots"),
                    Node.leaf("Hit effects", "Hit effects"),
                    Node.leaf("Breaking", "Breaking"),
                    Node.leaf("Totem", "Totem")),
            Node.group("Kits", "All kits",
                    Node.leaf("Diamond SMP", "Kit: Diamond SMP"),
                    Node.leaf("Crystal", "Kit: Crystal"),
                    Node.leaf("Mace", "Kit: Mace"),
                    Node.leaf("Sword", "Kit: Sword"),
                    Node.leaf("Axe", "Kit: Axe"),
                    Node.leaf("UHC", "Kit: UHC"),
                    Node.leaf("Pot", "Kit: Pot"),
                    Node.leaf("NethOP", "Kit: NethOP"),
                    Node.leaf("SMP", "Kit: SMP"),
                    Node.group("Sub tiers", null,
                            Node.leaf("Cart", "Kit: Cart"),
                            Node.leaf("Bed", "Kit: Bed"),
                            Node.leaf("Creeper", "Kit: Creeper"),
                            Node.leaf("OG Vanilla", "Kit: OG Vanilla"),
                            Node.leaf("Speed", "Kit: Speed"),
                            Node.leaf("Manhunt", "Kit: Manhunt"),
                            Node.leaf("Iron Pot", "Kit: Iron Pot"),
                            Node.leaf("Diamond Vanilla", "Kit: Diamond Vanilla"),
                            Node.leaf("Trident", "Kit: Trident"),
                            Node.leaf("Elytra", "Kit: Elytra"),
                            Node.leaf("Bow", "Kit: Bow"))),
            Node.leaf("Sky", "Sky"),
            Node.leaf("Weapons", "Weapons"),
            Node.leaf("Worn Armor", "Worn Armor"),
            Node.leaf("Natural", "Natural"),
            Node.leaf("Ores & Gems", "Ores & Gems"),
            Node.leaf("Building", "Building"),
            Node.leaf("Redstone", "Redstone"),
            Node.leaf("Utility", "Utility"),
            Node.leaf("Food & Farming", "Food & Farming"),
            Node.leaf("Mobs", "Mobs"),
            Node.leaf("UI & HUD (all)", "UI & HUD"),
            Node.leaf("Particles & Effects", "Particles & Effects"),
            Node.leaf("Sky & Water", "Sky & Water"),
            Node.leaf("Sounds", "Sounds"),
            Node.leaf("Models", "Models")
    );

    private static List<Category> buildAll() {
        List<Category> all = new ArrayList<>(BASE);
        all.add(new Category("UI", k -> k.startsWith("textures/gui/") && !k.contains("/hud/heart/") && !k.contains("/hud/crosshair")));
        all.add(new Category("Shield", k -> tex(k) && name(k).contains("shield") || k.contains("/entity/shield/")));
        all.add(new Category("Pots", k -> k.startsWith("textures/item/") && has(name(k), "potion", "tipped_arrow")
                || k.startsWith("textures/mob_effect/")));
        all.add(new Category("Sky", Categories::isSky));
        all.add(new Category("Hit effects", Categories::isHitEffect));
        all.add(new Category("Breaking", k -> k.startsWith("textures/block/destroy_stage_")
                || k.startsWith("sounds/dig/") || k.equals("particles/block.json")));
        all.add(new Category("Totem", k -> k.equals("textures/item/totem_of_undying.png") || k.startsWith("sounds/item/totem/")
                || k.startsWith("textures/particle/glitter") || k.equals("textures/particle/totem.png")
                || k.equals("particles/totem_of_undying.json")));
        all.addAll(KITS);
        all.add(new Category("All kits", k -> KITS.stream().anyMatch(c -> c.test().test(k))));
        return List.copyOf(all);
    }

    private static final List<Category> BASE = Arrays.asList(
            new Category("PvP", k -> tex(k) && has(name(k),
                    "sword", "mace", "spear", "bow", "arrow", "trident", "shield", "totem", "ender_pearl",
                    "golden_apple", "potion", "splash", "lingering", "end_crystal", "obsidian", "respawn_anchor",
                    "cobweb", "wind_charge", "experience_bottle", "fire_0", "fire_1", "pumpkinblur", "heart",
                    "_helmet", "_chestplate", "_leggings", "_boots", "crit", "sweep", "damage", "hunger",
                    "food_full", "food_half", "armor_full", "armor_half") && !k.contains("/trims/")
                    || k.startsWith("textures/entity/equipment/humanoid")),
            new Category("Crosshairs", k -> k.startsWith("textures/gui/sprites/hud/crosshair")),
            new Category("Hearts", k -> k.startsWith("textures/gui/sprites/hud/heart/")),
            new Category("Swords", k -> k.startsWith("textures/item/") && name(k).contains("sword")),
            new Category("Weapons", k -> tex(k) && (has(name(k), "sword", "mace", "spear", "bow", "arrow", "trident")
                    || (name(k).contains("_axe") && !name(k).contains("pickaxe")))),
            // Things you wear, as items. Opens on a player wearing them.
            new Category("Armor", k -> k.startsWith("textures/item/") && has(name(k), "_helmet", "_chestplate",
                    "_leggings", "_boots", "elytra", "horse_armor", "wolf_armor", "shield")),
            // The sheets drawn on the player's body when armor is worn.
            new Category("Worn Armor", k -> k.startsWith("textures/entity/equipment/humanoid")),
            new Category("Tools", k -> tex(k) && has(name(k), "pickaxe", "shovel", "_hoe", "_axe", "shears",
                    "flint_and_steel", "fishing_rod", "brush", "spyglass", "compass", "clock")),
            new Category("Natural", k -> block(k) && has(name(k), "grass", "dirt", "stone", "sand", "gravel",
                    "leaves", "_log", "sapling", "water", "lava", "snow", "ice", "moss", "clay", "mud", "mushroom",
                    "vine", "kelp", "coral", "cactus", "sugar_cane", "bamboo", "deepslate", "netherrack", "end_stone",
                    "tuff", "calcite", "dripstone", "azalea", "fern", "bush", "poppy", "dandelion", "tulip", "orchid",
                    "allium", "bluet", "daisy", "cornflower", "lily", "rose", "peony", "lilac", "sunflower", "podzol",
                    "mycelium", "soul_s", "basalt", "blackstone", "nylium", "wart", "seagrass", "pointed", "root")
                    && !has(name(k), "redstone", "stonecutter", "brick", "polished", "smooth", "chiseled", "cut_")),
            // Only actual ores, gems and their storage blocks / raw / ingots / nuggets.
            new Category("Ores & Gems", Categories::isOreOrGem),
            new Category("Building", k -> block(k) && has(name(k), "planks", "brick", "concrete", "terracotta",
                    "wool", "glass", "polished", "smooth", "chiseled", "cut_", "quartz_block", "prismarine",
                    "purpur", "copper_block", "tiles", "bookshelf", "_door", "trapdoor", "stripped")),
            new Category("Redstone", k -> tex(k) && has(name(k), "redstone", "repeater", "comparator", "piston",
                    "observer", "dispenser", "dropper", "hopper", "lever", "button", "pressure_plate", "tripwire",
                    "daylight_detector", "target", "note_block", "rail", "tnt", "sculk_sensor", "crafter",
                    "copper_bulb", "lightning_rod", "trapped_chest", "slime_block", "honey_block")),
            new Category("Utility", k -> tex(k) && has(name(k), "crafting_table", "furnace", "smoker", "chest",
                    "barrel", "anvil", "enchant", "brewing", "cauldron", "_bed", "lantern", "torch", "map",
                    "bucket", "bundle", "shulker", "beacon", "lodestone", "grindstone", "smithing", "stonecutter",
                    "loom", "cartography", "fletching", "composter", "sign", "book", "lectern", "campfire",
                    "name_tag", "lead", "saddle", "firework", "boat", "minecart")),
            new Category("Food & Farming", k -> tex(k) && has(name(k), "apple", "bread", "carrot", "potato",
                    "beetroot", "wheat", "melon", "pumpkin", "cookie", "cake", "beef", "porkchop", "chicken", "mutton",
                    "rabbit", "cod", "salmon", "stew", "berries", "honey", "farmland", "pie", "kelp", "chorus_fruit",
                    "egg", "milk", "sugar", "cocoa")),
            new Category("Mobs", k -> k.startsWith("textures/entity/") && !isEquipment(k)),
            new Category("UI & HUD", k -> k.startsWith("textures/gui/") || k.startsWith("textures/font/")
                    || k.startsWith("textures/map/")),
            new Category("Particles & Effects", k -> k.startsWith("textures/particle/") || k.startsWith("particles/")
                    || k.startsWith("textures/mob_effect/") || k.startsWith("textures/effect/")
                    || name(k).contains("glint")),
            new Category("Sky & Water", k -> k.startsWith("textures/environment/") || k.startsWith("textures/colormap/")
                    || has(name(k), "water_still", "water_flow", "water_overlay", "lava_still", "lava_flow", "end_sky")),
            new Category("Sounds", Assets::isSound),
            new Category("Models", k -> k.startsWith("models/") || k.startsWith("items/") || k.startsWith("blockstates/"))
    );

    /** Every category by name (declared after BASE and KITS so they exist when this is built). */
    public static final List<Category> ALL = buildAll();

    /**
     * Files that only look right together. Picking one of them from a pack takes the whole set
     * from that pack, so hearts never end up half from one pack and half from another.
     */
    public static String setOf(String k) {
        if (k.startsWith("textures/gui/sprites/hud/heart/")) return "hearts";
        if (k.startsWith("textures/gui/sprites/hud/crosshair")) return "crosshair";
        if (isShield(k)) return "shield";
        if (isSky(k)) return "sky";
        if (k.equals("textures/item/end_crystal.png") || k.startsWith("textures/entity/end_crystal/")) return "end_crystal";
        if (k.startsWith("textures/block/") && k.endsWith(".png")) return "block:" + blockBase(k);
        if (k.startsWith("textures/particle/") && k.endsWith(".png")) {
            String n = name(k).replace(".png", "");
            String base = n.replaceAll("_\\d+$", "");
            if (!base.equals(n) && !base.isEmpty()) return "particle:" + base;
        }
        return null;
    }

    private static final String[] SIDE_SUFFIXES = {"_side_overlay", "_overlay", "_top", "_bottom", "_side", "_front",
            "_back", "_end", "_inner", "_outer", "_snow", "_on", "_lit", "_open", "_closed"};

    /** "textures/block/grass_block_side_overlay.png" -> "grass_block"; "fire_1" -> "fire"; "wheat_stage3" -> "wheat_stage". */
    public static String blockBase(String k) {
        String n = k.substring(k.lastIndexOf('/') + 1).replace(".png", "");
        boolean changed = true;
        while (changed) {
            changed = false;
            String stripped = n.replaceAll("_?\\d+$", "");
            if (!stripped.equals(n) && !stripped.isEmpty()) { n = stripped; changed = true; }
            for (String s : SIDE_SUFFIXES) {
                if (n.endsWith(s) && n.length() > s.length()) {
                    n = n.substring(0, n.length() - s.length());
                    changed = true;
                    break;
                }
            }
        }
        return n;
    }

    /** Which member of a set stands for it in lists. */
    public static boolean isPreferredRep(String k, String set) {
        if (set.equals("hearts")) return k.endsWith("/heart/full.png");
        if (set.equals("crosshair")) return k.endsWith("/crosshair.png");
        if (set.equals("shield")) return k.endsWith("base_nopattern.png");
        if (set.equals("sky")) return k.equals("textures/environment/sun.png");
        if (set.equals("end_crystal")) return k.equals("textures/item/end_crystal.png");
        if (set.equals("block:destroy_stage")) return k.equals("textures/block/destroy_stage_7.png");
        if (set.startsWith("block:")) return k.equals("textures/block/" + set.substring(6) + ".png");
        if (set.startsWith("particle:")) return k.equals("textures/particle/" + set.substring(9) + "_0.png");
        return false;
    }

    /** Crit, sweep, enchanted hit and damage particles plus the hit sounds. */
    public static boolean isHitEffect(String k) {
        if (k.startsWith("textures/particle/")) {
            String n = name(k);
            return n.startsWith("critical_hit") || n.startsWith("enchanted_hit") || n.startsWith("sweep_")
                    || n.startsWith("damage") || n.startsWith("crit");
        }
        if (k.startsWith("particles/")) return has(name(k), "crit", "sweep", "damage_indicator", "enchanted_hit");
        if (k.startsWith("sounds/entity/player/attack/") || k.startsWith("sounds/damage/")) return true;
        return k.startsWith("textures/gui/sprites/hud/crosshair_attack_indicator");
    }

    /** Every shield texture (plain, base and banner-pattern versions) counts as one shield. */
    public static boolean isShield(String k) {
        return k.startsWith("textures/entity/shield") || k.startsWith("textures/item/shield");
    }

    private static final Set<String> ORE_ITEMS = Set.of("diamond", "emerald", "lapis_lazuli", "amethyst_shard",
            "quartz", "coal", "charcoal", "iron_ingot", "gold_ingot", "copper_ingot", "netherite_ingot", "netherite_scrap",
            "iron_nugget", "gold_nugget", "copper_nugget", "raw_iron", "raw_gold", "raw_copper", "redstone", "echo_shard");
    private static final Set<String> ORE_BLOCKS = Set.of("diamond_block", "emerald_block", "lapis_block", "gold_block",
            "iron_block", "copper_block", "netherite_block", "coal_block", "redstone_block", "amethyst_block",
            "budding_amethyst", "amethyst_cluster", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud",
            "quartz_block", "ancient_debris", "raw_iron_block", "raw_gold_block", "raw_copper_block");

    static boolean isOreOrGem(String k) {
        String n = name(k).replace(".png", "");
        if (k.startsWith("textures/item/")) return ORE_ITEMS.contains(n);
        if (!k.startsWith("textures/block/")) return false;
        String base = blockBase(k);
        return base.endsWith("_ore") || ORE_BLOCKS.contains(base);
    }

    /** Sun, moon, clouds, end sky and pack custom skies: one "sky" that swaps as a whole. */
    public static boolean isSky(String k) {
        if (k.startsWith("optifine/sky/")) return true;
        if (!k.startsWith("textures/environment/")) return false;
        String n = name(k);
        return n.startsWith("sun") || n.startsWith("moon") || n.startsWith("clouds") || n.startsWith("end_sky")
                || n.startsWith("celestial");
    }

    public static boolean isEquipment(String k) {
        return k.startsWith("textures/entity/equipment/") || k.startsWith("textures/models/armor/");
    }

    private static boolean tex(String k) { return k.startsWith("textures/"); }

    private static boolean block(String k) { return k.startsWith("textures/block/"); }

    private static String name(String k) {
        return k.substring(k.lastIndexOf('/') + 1);
    }

    private static boolean has(String n, String... parts) {
        for (String p : parts) if (n.contains(p)) return true;
        return false;
    }

    private Categories() {
    }
}
