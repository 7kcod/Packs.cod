package com.enc.packcod.ui;

import com.enc.packcod.pack.AssetSource;
import com.enc.packcod.pack.MixProject;
import com.enc.packcod.pack.PackLibrary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.enc.packcod.ui.Theme.*;

/**
 * Saved mixes and mix codes. Save the current mix under a name, switch to another in one
 * click, start blank, or copy a short code a friend can paste to get the same mix
 * (they need the same packs in their folder).
 */
final class MixesScreen extends Screen {

    private static final int ROW = 18;

    private final MixerScreen parent;
    private final MixProject project;
    private final PackLibrary lib;
    private List<String> saved = new ArrayList<>();
    private EditBox nameBox;
    private int scroll;
    private String status = "";
    private int statusColor = DIM;
    /** Name waiting for a second click to confirm delete. */
    private String confirmDelete;

    private interface Click { void on(); }
    private record Hit(int x, int y, int w, int h, Click action) {
        boolean in(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }
    private final List<Hit> hits = new ArrayList<>();

    MixesScreen(MixerScreen parent, MixProject project, PackLibrary lib) {
        super(Component.literal("Mixes"));
        this.parent = parent;
        this.project = project;
        this.lib = lib;
    }

    private int pw() { return Math.min(320, width - 20); }
    private int px() { return (width - pw()) / 2; }
    private int py() { return Math.max(8, height / 2 - 120); }
    private int ph() { return Math.min(240, height - 16); }

    @Override
    protected void init() {
        saved = MixProject.savedNames();
        nameBox = new EditBox(font, px() + 10, py() + 26, pw() - 90, 10, Component.literal("Mix name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(48);
        nameBox.setValue(project.name);
        addRenderableWidget(nameBox);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        g.fill(0, 0, width, height, BG);
        int x = px(), y = py(), w = pw(), h = ph();
        panel(g, x, y, w, h);
        Theme.label(g, font, "Mixes", x + 8, y + 7);
        String hint = "your saved mixes";
        g.drawString(font, hint, x + w - 8 - font.width(hint), y + 7, FAINT, false);

        // save row
        slot(g, x + 6, y + 22, w - 86, 15);
        button(g, x + w - 76, y + 22, 70, 15, "Save mix", mx, my, true, true, () -> {
            String n = nameBox.getValue().trim();
            if (n.isEmpty()) n = "My Mix";
            project.name = n;
            project.saveAs(n);
            project.save();
            saved = MixProject.savedNames();
            say("Saved \"" + n + "\"", GOOD);
        });

        // list
        int ly = y + 44;
        int listH = h - 44 - 64;
        int rows = Math.max(1, listH / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, saved.size() - rows)));
        slot(g, x + 6, ly, w - 12, rows * ROW + 2);
        if (saved.isEmpty()) {
            g.drawString(font, "Nothing saved yet. Name your mix and hit Save mix.", x + 12, ly + 6, FAINT, false);
        }
        for (int i = 0; i < rows && i + scroll < saved.size(); i++) {
            String n = saved.get(i + scroll);
            int ry = ly + 1 + i * ROW;
            boolean current = n.equals(project.name);
            boolean hov = mx >= x + 7 && my >= ry && mx < x + w - 7 && my < ry + ROW;
            if (hov || current) g.fill(x + 7, ry, x + w - 7, ry + ROW, current ? PANEL_HI : HOVER_FILL);
            if (current) g.fill(x + 7, ry, x + 9, ry + ROW, ACCENT);
            int delW = font.width("Delete") + 8;
            int loadW = font.width("Load") + 10;
            g.drawString(font, fit(font, n, w - 30 - delW - loadW), x + 13, ry + 5, current ? TEXT : DIM, false);
            int bx = x + w - 10 - delW;
            boolean confirm = n.equals(confirmDelete);
            button(g, bx, ry + 2, delW, 14, confirm ? "Sure?" : "Delete", mx, my, false, true, () -> {
                if (n.equals(confirmDelete)) {
                    MixProject.deleteSaved(n);
                    saved = MixProject.savedNames();
                    confirmDelete = null;
                    say("Deleted \"" + n + "\"", DIM);
                } else {
                    confirmDelete = n;
                }
            });
            bx -= loadW + 3;
            button(g, bx, ry + 2, loadW, 14, "Load", mx, my, true, true, () -> load(n));
        }

        // codes + blank
        int by = y + h - 58;
        int bw = (w - 18) / 3;
        button(g, x + 6, by, bw, 16, "Copy code", mx, my, false, true, this::copyCode);
        button(g, x + 9 + bw, by, bw, 16, "Paste code", mx, my, false, true, this::pasteCode);
        button(g, x + 12 + 2 * bw, by, bw, 16, "New blank", mx, my, false, true, () -> {
            if (!project.picks.isEmpty()) project.saveAs(project.name);
            MixProject blank = new MixProject();
            blank.name = "New Mix";
            project.copyFrom(blank);
            project.save();
            nameBox.setValue(project.name);
            parent.mixChanged();
            saved = MixProject.savedNames();
            say("Started a blank mix (the old one was saved)", GOOD);
        });
        g.drawString(font, fit(font, status, w - 16), x + 8, by + 22, statusColor, false);

        button(g, x + (w - 70) / 2, y + h - 22, 70, 16, "← Back", mx, my, false, true, this::onClose);
    }

    private void load(String n) {
        MixProject m = MixProject.loadSaved(n);
        if (m == null) {
            say("Couldn't read \"" + n + "\"", BAD);
            return;
        }
        // keep what you had: save it before switching
        if (!project.picks.isEmpty() && !project.name.equals(n)) project.saveAs(project.name);
        project.copyFrom(m);
        project.save();
        nameBox.setValue(project.name);
        parent.mixChanged();
        saved = MixProject.savedNames();
        say("Loaded \"" + n + "\"  (" + project.picks.size() + " changes)", GOOD);
    }

    private void copyCode() {
        if (project.picks.isEmpty()) {
            say("Nothing in this mix to share yet", BAD);
            return;
        }
        String code = project.toCode();
        minecraft.keyboardHandler.setClipboard(code);
        say("Code copied (" + code.length() + " letters). Friends paste it here.", GOOD);
    }

    private void pasteCode() {
        MixProject m = MixProject.fromCode(minecraft.keyboardHandler.getClipboard());
        if (m == null) {
            say("No mix code on your clipboard", BAD);
            return;
        }
        Set<String> missing = new LinkedHashSet<>();
        m.picks.entrySet().removeIf(e -> {
            AssetSource s = lib == null ? null : lib.source(e.getValue());
            if (s == null) {
                missing.add(e.getValue().replaceAll("\\.zip$", ""));
                return true;
            }
            return false;
        });
        if (!project.picks.isEmpty()) project.saveAs(project.name);
        project.copyFrom(m);
        project.save();
        nameBox.setValue(project.name);
        parent.mixChanged();
        saved = MixProject.savedNames();
        if (missing.isEmpty()) say("Loaded \"" + project.name + "\" from the code", GOOD);
        else say("Loaded, but you're missing: " + String.join(", ", missing), BAD);
    }

    private void say(String s, int c) {
        status = s;
        statusColor = c;
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String text, int mx, int my,
                        boolean primary, boolean enabled, Click action) {
        boolean hov = enabled && mx >= x && my >= y && mx < x + w && my < y + h;
        raised(g, x, y, w, h, hov, primary, enabled);
        int color = !enabled ? FAINT : primary ? ON_ACCENT : TEXT;
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, color, false);
        if (enabled) hits.add(new Hit(x, y, w, h, action));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).in(event.x(), event.y())) {
                setFocused(null);
                hits.get(i).action().on();
                return true;
            }
        }
        confirmDelete = null;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll += sy > 0 ? -1 : sy < 0 ? 1 : 0;
        return true;
    }

    @Override
    public void onClose() {
        String n = nameBox.getValue().trim();
        if (!n.isEmpty()) project.name = n;
        project.save();
        minecraft.setScreen(parent);
    }
}
