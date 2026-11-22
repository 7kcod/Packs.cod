package com.enc.packcod;

import com.enc.packcod.ui.Backgrounds;
import com.enc.packcod.ui.MixerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;

/**
 * Ways in:
 *  - a "pack.cod" button on the title screen and on the Resource Packs screen
 *  - the /packcod client command while in a world
 */
public final class PackCodClient implements ClientModInitializer {

    /** Shown small next to the logo inside the mod. Comes from gradle.properties (mod_version). */
    public static final String VERSION = FabricLoader.getInstance().getModContainer("packcod")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");

    private static final Identifier COD = Identifier.withDefaultNamespace("textures/item/cod.png");

    private static boolean openNextTick;
    /** Ticks left before grabbing a clean (HUD-less) view of the world for preview backgrounds. */
    private static int captureIn = -1;
    private static boolean hadHideGui;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("packcod").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null) {
                        // In a world: hide the HUD and hand for a moment and keep the view as a preview background.
                        hadHideGui = mc.options.hideGui;
                        mc.options.hideGui = true;
                        captureIn = 3;
                    } else {
                        // The chat screen closes right after the command runs, so open on the next tick.
                        openNextTick = true;
                    }
                    return 1;
                })));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (captureIn > 0 && --captureIn == 0) {
                captureIn = -1;
                try {
                    Screenshot.takeScreenshot(client.getMainRenderTarget(), Backgrounds::save);
                } catch (Throwable ignored) {
                }
                client.options.hideGui = hadHideGui;
                openNextTick = true;
            }
            if (openNextTick) {
                openNextTick = false;
                client.setScreen(new MixerScreen(client.screen));
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            // Inside pvp.cod, pack.cod only opens from the pvp.cod menu (no cod button of its own).
            if (FabricLoader.getInstance().isModLoaded("pvpcod")) return;
            boolean packs = screen instanceof PackSelectionScreen
                    && screen.getTitle().getString().equals(Component.translatable("resourcePack.title").getString());
            if (packs || screen instanceof TitleScreen) {
                // Just the cod: an invisible button with the cod drawn on top, bobbing gently.
                Button b = Button.builder(Component.empty(), btn -> client.setScreen(new MixerScreen(screen)))
                        .bounds(6, 6, 20, 20)
                        .tooltip(Tooltip.create(Component.literal("pack.cod: mix your own pack")))
                        .build();
                b.setAlpha(0f);
                Screens.getButtons(screen).add(b);
                ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, delta) -> {
                    double t = System.currentTimeMillis() / 1000.0;
                    boolean hover = b.isHoveredOrFocused();
                    float bob = (float) Math.sin(t * 2.2) * 1.5f - (hover ? 2f : 0f);
                    float scale = hover ? 1.15f : 1f;
                    g.pose().pushMatrix();
                    g.pose().translate(b.getX() + 10f, b.getY() + 10f + bob);
                    g.pose().rotate((float) Math.toRadians(Math.sin(t * 1.6) * 6));
                    g.pose().scale(scale, scale);
                    g.blit(RenderPipelines.GUI_TEXTURED, COD, -8, -8, 0f, 0f, 16, 16, 16, 16, 16, 16);
                    g.pose().popMatrix();
                });
            }
        });
    }
}
