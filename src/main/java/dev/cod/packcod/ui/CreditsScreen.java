package dev.cod.packcod.ui;

import dev.cod.packcod.PackCodClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import static dev.cod.packcod.ui.Theme.*;

/** Credits: who made pack.cod. */
final class CreditsScreen extends Screen {

    private static final Identifier HEAD = Identifier.fromNamespaceAndPath("packcod", "textures/gui/credits_head.png");
    private static final Identifier COD = Identifier.withDefaultNamespace("textures/item/cod.png");

    private final Screen parent;
    private int backX, backY, backW = 70, backH = 18;

    CreditsScreen(Screen parent) {
        super(Component.literal("Credits"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, BG);
        int cx = width / 2;
        double t = System.currentTimeMillis() / 1000.0;

        // logo
        int ly = Math.max(10, height / 2 - 112);
        g.blit(RenderPipelines.GUI_TEXTURED, COD, cx - 44, ly, 0f, 0f, 16, 16, 16, 16, 16, 16);
        g.drawString(font, "pack", cx - 24, ly + 4, TEXT, false);
        g.drawString(font, ".cod", cx - 24 + font.width("pack"), ly + 4, ACCENT, false);
        String v = "v" + PackCodClient.VERSION;
        g.drawString(font, v, cx - 24 + font.width("pack.cod") + 4, ly + 4, FAINT, false);

        // the dev's head, bobbing a little
        int size = Math.min(96, height / 3);
        float bob = (float) Math.sin(t * 1.8) * 3f;
        g.pose().pushMatrix();
        g.pose().translate(cx, ly + 30 + size / 2f + bob);
        g.pose().rotate((float) Math.toRadians(Math.sin(t * 1.2) * 3));
        g.blit(RenderPipelines.GUI_TEXTURED, HEAD, -size / 2, -size / 2, 0f, 0f, size, size, 256, 256, 256, 256);
        g.pose().popMatrix();

        // name, big
        int ny = ly + 40 + size;
        String made = "Made by 7kcod";
        g.pose().pushMatrix();
        g.pose().translate(cx - font.width(made), ny);
        g.pose().scale(2f, 2f);
        g.drawString(font, "Made by ", 0, 0, TEXT, false);
        g.drawString(font, "7kcod", font.width("Made by "), 0, ACCENT, false);
        g.pose().popMatrix();
        String solo = "solo dev";
        g.drawString(font, solo, cx - font.width(solo) / 2, ny + 22, DIM, false);

        // back
        backX = cx - backW / 2;
        backY = Math.min(height - backH - 8, ny + 44);
        boolean hov = mx >= backX && my >= backY && mx < backX + backW && my < backY + backH;
        raised(g, backX, backY, backW, backH, hov, false, true);
        String b = "← Back";
        g.drawString(font, b, cx - font.width(b) / 2, backY + 5, TEXT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= backX && event.y() >= backY && event.x() < backX + backW && event.y() < backY + backH) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
