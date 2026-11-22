# Changelog: packs.cod (pack.cod)

Every update gets a new `0.x` version. The version also shows small next to the logo in the mod.
Newest first.

## 0.24
- When pack.cod is running inside pvp.cod, its cod button on the title screen and Resource Packs screen is hidden; open it from the pvp.cod menu instead. On its own, pack.cod works exactly as before.

## 0.23
- Look: styles are now cards in a grid, so Outline and Glow show up on small windows too
- Breaking effects (under PvP): all 10 crack stages swap as one; Block view shows a cracked block, In game mines a block with the pack's pickaxe until it pops into bits; dig sounds included
- New Browse tab (top bar, or "Browse packs" at the top of the list): PacksMC's 50 most downloaded packs with search and 16x/32x/64x+ filters
- Get opens the pack on packsmc.com (PacksMC only allows downloads through its site); pack.cod watches your Downloads folder and shows the zip with an Install button that copies it into resourcepacks
- Installed packs are marked; Refresh can load the live list with a PacksMC API key

## 0.22
- New Look screen (top right): change pack.cod's colours and style, it applies as you click
- 8 colour palettes: Cod, Deep Ocean, Nether, The End, Emerald, Snow (light), Midnight, Cherry
- Accent colour: keep the palette's own or pick one of 10
- 5 styles that change how buttons, slots and panels are drawn: Classic, Pixel (Minecraft-style bevels), Flat, Outline, Glow (rounded with an accent glow)
- Live preview on the Look screen, plus Reset

## 0.21
- Mixes: save as many mixes as you want, switch between them in one click, start a blank one (the old one is kept)
- Mix codes: Copy code puts a short code on your clipboard; a friend pastes it to get the same mix (it says which packs they're missing)
- Live: turn it on in the bottom bar and your mix goes on in game by itself when you leave pack.cod
- Totem pop preview: the totem's In game view plays the pop with the pack's totem and glitter, and pop-reducer packs show smaller
- Hit effects (under PvP): crit, enchanted hit, sweep and damage particles plus hit sounds; In game shows them animated in front of you
- Particle frames (sweep_0..7 and so on) now swap together as one, and play when you hover them
- Stars: star a pack in the Brush list and it shows first everywhere
- Resolution filter: the chip next to Brush cycles All / 16x / 32x / 64x+ and hides the other packs
- Surprise me: a random pack for everything shown; click again to re-roll
- 3D mobs: zombies, husks, drowned, skeletons, strays, wither skeletons, creepers and player skins get a 3D view (grid shows their face)
- Recolour in Draw: hue, saturation and brightness sliders plus quick colours
- Export shows the pack's size and file count, and warns when block and item textures mix resolutions

## 0.20
- Fixed a crash when opening the Sky entry (its name broke the label code).
- End crystal In game view: no obsidian block, and the crystal is about twice as big, centred on screen.

## 0.19
- Sky changer: new "Sky" entry swaps a pack's whole sky in one go (sun, moon, clouds, end sky, and custom OptiFine-style skies in optifine/sky). The preview shows the pack's custom sky image, or its sun, moon and clouds. Custom skies need a sky mod (OptiFine or FabricSkyBoxes-compatible) to show in game.
- End crystal "In game" view shows the crystal placed on obsidian, spinning and bobbing, using each pack's crystal texture. The crystal item and its entity texture swap together.
- Totems (and any item a pack changes through its model, not by replacing the texture file) now show each pack's real texture and count as a version; exporting brings the pack's model and texture along.
- Closed the thin seams between faces on 3D blocks.
- Export screen: the "drop a picture" hint no longer sits under the EXPORT button.

## 0.18
- Export screen: name the pack, pick the colour its name shows in, and upload a pack icon (or drop a picture on the window; "Auto icon" goes back to the generated one). A preview shows it like the game's pack list. The pack's description is the coloured name with "Made with pack.cod" under it.
- Credits tab (bottom bar): Made by 7kcod, solo dev.

## 0.17
- Animated textures play when you hover them (grid, Board, item pages, inspector preview, 3D block faces), using the frame order and timing from the texture's .mcmeta. Fire, magma, sea lanterns, prismarine, compasses, clocks and packs' animated items all move.

## 0.16
- Imported pictures can be placed: after Import img the right panel shows Size (10%-400%), Opacity, Crop (left/right/top/bottom), Move (arrow buttons, Centre, or drag the picture on the canvas), Fit/Fill, colours and background removal, then Done or Cancel. The picture is laid over your drawing, so lower opacity blends it in.

## 0.15
- Image converter in the Draw screen: "Import img" (or drag a picture onto the window) turns any PNG/JPG/GIF/BMP into a texture-sized pixel version. Smooth box-filter shrinking, hard pixel edges, Fit or Fill, optional background removal, and colours: all, 16, 8, or "Match pack" (snaps to the colours the original texture uses). Every change is undoable.

## 0.14
- Ores & Gems fixed: it only holds real ores (stone, deepslate and nether), gems, storage blocks, raw ores, ingots, nuggets, coal, quartz, redstone and amethyst. Copper chests, golems, horse/nautilus armor, villager badges, slot outlines, particles and trim colour strips no longer land there (they showed as empty tiles).

## 0.13
- Shield is one entry now, like hearts: picking a pack takes all its shield textures together (plain, base and banner-pattern ones), and it shows the shield's front face instead of the whole texture sheet.

## 0.12
- The list on the left folds open. PvP opens into Swords, Hearts, Tools, UI, Crosshair, Armor, Shield and Pots (clicking PvP itself shows all of it). A "Folders" group holds the raw game folders, closed by default.
- New Kits group with the items of each PvP tier-test kit: Diamond SMP, Crystal, Mace, Sword, Axe, UHC, Pot, NethOP, SMP, and a Sub tiers group (Cart, Bed, Creeper, OG Vanilla, Speed, Manhunt, Iron Pot, Diamond Vanilla, Trident, Elytra, Bow). Clicking Kits shows every kit item together. The Mace kit follows a published kit list; the others are the usual loadouts and may differ a little from server to server.

## 0.11
- Blocks with several faces (grass, logs, furnaces, lamps, doors...) are one entry now; picking a pack for it takes every side from that pack together. Same for animation frames like fire.
- Hearts are one entry: "hearts (all kinds)" swaps every heart (full, half, empty, poisoned, frozen, hardcore...) together.
- Blocks show in 3D everywhere, grid included, and open on the Block view. Fire, flowers, saplings, grass and similar show as crossed planes like in the world, and are held like flat items in the In game view.
- "On player" view only for armor now.

## 0.10
- "In game" previews use real views of your world as the background instead of a drawn sky and grass. Typing /packcod while in a world hides the HUD and hand for a moment, keeps that view, then opens pack.cod; the newest 4 are kept (config/packcod/backgrounds) and each pack's preview uses one of them. With none captured yet, the drawn sky is used and the item page tells you how to add some.

## 0.9
- Held items in the "In game" view are now rendered in real 3D the same way the game builds them: the sprite extruded one pixel thick, placed with the game's right-hand position and the item's firstperson_righthand transform (read from each pack's own model, so packs that shrink or move swords show that), seen through the game's 70 degree hand camera, with game-style lighting. Blocks in hand use the block transform.
- "Flat" is always the first view and what items open on.

## 0.8
- Item page shows more previews at once (4-5 across for In game, 6+ for the others) instead of two huge ones.
- Held sword in the In game view matched to a pack showcase shot: bigger, steeper, tip toward the top right, handle off the bottom, with its side faces in a darker shade of its own colours on the right.

## 0.7
- New "Crosshairs" and "Hearts" categories at the top of the list.
- Crosshair and heart swapper: picking any heart (or crosshair) from a pack swaps that pack's whole set in one go, so they always match.
- "In game" view now draws each pack's real crosshair and hearts. When you're looking at HUD art it shows an empty hand and a bigger HUD so you can compare them; picking a special heart (poisoned, frozen, hardcore...) fills the row with it.

## 0.6
- "In game" preview is now screen-shaped (16:9) like a real monitor, and the held item is placed from measurements of a real 1.21.11 first-person screenshot: about 60% of screen height, at the right edge, 13 degrees steeper than the texture.
- Empty off-hand slot now uses the hotbar's own slot graphic, so it looks like a real empty slot.

## 0.5
- "In game" preview rebuilt to look like the real first-person view: the held item is big at the right edge with its side thickness showing (it was invisible before), blocky clouds, thin crosshair, and the HUD (hearts, hunger, XP bar, hotbar with the item selected, off-hand slot) drawn with each pack's own GUI sprites.

## 0.4
- Pixel editor: hit "Draw" on any texture to paint over the version in use (or clear it and start blank). Pencil, eraser, fill, colour picker, mirror, grid, undo/redo, RGBA sliders, palette, colours-in-this-texture. Saving puts it in a "pack.cod edits" pack in your resourcepacks folder and switches the item to it, so drawings mix and export like any pack (and work as a normal pack on their own).
- Armor fixed: the Armor category now shows only real armor items and opens them on a player wearing that pack's armor. Trim palettes and patterns are out of it. New "Worn Armor" category for the sheets drawn on the player.
- "Hand" view replaced by "In game": sky, ground, crosshair, the item held in first person with thickness like the game draws it, and your hotbar.
- Exporting an armor item also brings that pack's worn-armor sheets.
- Esc on an item page goes back to the list instead of closing the mod.
- The title-screen button is now just the cod icon, bobbing a little (lifts when you hover it).

## 0.3
- Click any item to open its page: every pack's version side by side, big, with < > to step to the next item (wooden -> netherite sword and so on).
- Layout now fits small windows: side panels shrink, the inspector hides on narrow screens, header buttons wrap to a second row.
- New "Swords" category.
- Board view flips to one row per pack when there are more packs than items.
- Reads overlay folders (e.g. Bare Bones' overlay_1_21_11), so packs show what 1.21.11 actually uses.
- Exporting an item texture also brings that pack's item model, so 3D/custom items keep their shape.
- "pack.cod preview" (from Try in game) no longer shows up as a source pack.
- Small version label next to the logo.

## 0.2
- Categories (PvP, Weapons, Armor, Tools, Natural, Ores & Gems, Building, Redstone, Utility, Food & Farming, Mobs, UI & HUD, Particles & Effects, Sky & Water, Sounds, Models).
- Board view, Block/World/Hand/Player previews, Try in game.

## 0.1
- First version: mix textures, models and sounds from installed packs, sound player, compare page, export to a normal resource pack.
