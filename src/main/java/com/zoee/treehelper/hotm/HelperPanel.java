package com.zoee.treehelper.hotm;

import com.zoee.treehelper.config.Progression;
import com.zoee.treehelper.config.TreeConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The two mod GUIs drawn on the open Heart of the Mountain menu, each draggable by its header
 * with its own persisted offset:
 * <ul>
 *   <li><b>Tree Helper</b> — the settings hub. Header has a [–]/[+] minimize button. Rows:
 *       Powder helper ON/OFF (shows/hides the other GUI), Path dropdown (identical in effect
 *       to {@code /tree <grind>}), Notifications ON/OFF, Re-notify interval
 *       (Once/30s/1m/5m/10m), Routing overlay ON/OFF, and the three overlay color swatches
 *       (click to cycle a palette).</li>
 *   <li><b>Powder Helper</b> — its own GUI: one card per powder type with the live balance,
 *       the next plan step, a progress bar toward affording it, and steps done.</li>
 * </ul>
 *
 * <p>All clicks on either GUI are swallowed before the container sees them. While the menu is
 * open the visible slots are polled (throttled) through {@link PowderTracker}. Draws and reads
 * only — never clicks the menu itself.
 */
public final class HelperPanel {

    private static final String HOTM_TITLE = "heart of the mountain";
    /** How often to re-read the open menu, in ms. */
    private static final long POLL_MS = 750;

    // Layout (GUI-scaled px).
    private static final int PANEL_W = 132;
    private static final int HEADER_H = 16;
    private static final int ROW_H = 13;
    private static final int SETTINGS_ROWS = 9;
    private static final int CARD_H = 68;
    private static final int GAP = 4;
    private static final int PAD = 5;
    private static final int LINE = 11;

    // Palette.
    private static final int BG_TOP = 0xEE14141C;
    private static final int BG_BOTTOM = 0xEE1E1E2A;
    private static final int BAR_BG = 0xFF262633;
    private static final int SETTINGS_BORDER = 0xFF3A3A48;
    private static final int GOLD = 0xFFFFC03C;
    private static final int WHITE = 0xFFF2F2F2;
    private static final int GRAY = 0xFF9AA0B0;
    private static final int YELLOW = 0xFFFFD35C;
    private static final int GREEN = 0xFF5CFF8A;
    private static final int RED = 0xFFFF6A6A;

    /** Re-notify choices in seconds; 0 = once per step. */
    private static final int[] RENOTIFY_CHOICES = {0, 30, 60, 300, 600};

    // Color picker layout.
    private static final int SV_SIZE = 60;   // saturation/value square
    private static final int HUE_W = 10;     // hue bar width
    private static final int PICKER_W = PAD + SV_SIZE + GAP + HUE_W + PAD;
    private static final int PICKER_H = PAD + SV_SIZE + 4 + 12 + PAD;

    private enum Drag { NONE, SETTINGS, POWDER, PICK_SV, PICK_HUE }

    private final TreeConfig config;
    private final PowderTracker tracker;
    private final HotmScanner scanner;
    private long lastPoll;

    // Last drawn positions (for hit-testing) and interaction state.
    private int settingsX, settingsY;
    private int powderX, powderY;
    private Drag drag = Drag.NONE;
    private int grabDX, grabDY;
    private boolean dropdownOpen;
    /** The Notifications settings dropdown (per-type toggles). */
    private boolean notifOpen;
    /** Which overlay color the open picker edits: "target" / "wrong" / "step"; null = closed. */
    private String pickerKey;
    private float pickHue, pickSat, pickVal;
    private int pickerX, pickerY;
    /** Clickable regions from the last render: id -> {x, y, w, h}. */
    private final Map<String, int[]> hits = new LinkedHashMap<>();
    /** Transient path-change popup (replaces the old chat message); empty = hidden. */
    private final List<String> popupLines = new ArrayList<>();
    private long popupUntilMs;

    private HelperPanel(TreeConfig config, PowderTracker tracker, HotmScanner scanner) {
        this.config = config;
        this.tracker = tracker;
        this.scanner = scanner;
    }

    /** Wire both GUIs (render + mouse handling) into the client's screen events. */
    public static void register(TreeConfig config, PowderTracker tracker, HotmScanner scanner) {
        HelperPanel panel = new HelperPanel(config, tracker, scanner);
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!isHotm(screen)) return;
            ScreenEvents.afterExtract(screen).register(panel::render);
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                    event.button() != 0 || !panel.handleClick((int) event.x(), (int) event.y()));
            ScreenMouseEvents.afterMouseRelease(screen).register((s, event, handled) -> {
                if (panel.drag != Drag.NONE) {
                    panel.drag = Drag.NONE;
                    config.save(); // persist the new offsets
                }
                return true;
            });
        });
    }

    private static boolean isHotm(Screen s) {
        return s instanceof AbstractContainerScreen<?>
                && s.getTitle() != null
                && s.getTitle().getString().toLowerCase(Locale.ROOT).contains(HOTM_TITLE)
                && MenuNav.onHypixel(Minecraft.getInstance());
    }

    // --- interaction --------------------------------------------------------

    /** Handles a left-click at (mx, my); returns true if either GUI consumed it. */
    private boolean handleClick(int mx, int my) {
        if (pickerKey != null) {
            if (hit("pk.sv", mx, my)) {
                drag = Drag.PICK_SV;
                return true;
            }
            if (hit("pk.hue", mx, my)) {
                drag = Drag.PICK_HUE;
                return true;
            }
            if (inside(new int[]{pickerX, pickerY, PICKER_W, PICKER_H}, mx, my)) return true;
            pickerKey = null; // clicked elsewhere: close (color was applied live already)
            config.save();
            // fall through so e.g. another swatch can open its picker with the same click
        }
        if (notifOpen) {
            if (hit("nopt:ready", mx, my)) {
                config.chatNotifications = !config.chatNotifications;
                config.save();
                return true; // stays open so several toggles can be flipped in a row
            }
            if (hit("nopt:advice", mx, my)) {
                config.grindAdvice = !config.grindAdvice;
                config.save();
                return true;
            }
            notifOpen = false;
            return overAnyPanel(mx, my); // clicks elsewhere just close the dropdown
        }
        if (dropdownOpen) {
            dropdownOpen = false;
            for (Map.Entry<String, int[]> e : hits.entrySet()) {
                if (e.getKey().startsWith("opt:") && inside(e.getValue(), mx, my)) {
                    config.activePath = Progression.fromArg(e.getKey().substring(4));
                    config.save();
                    showPathPopup();
                    return true; // the option list may hang below the panel — always swallow
                }
            }
            return overAnyPanel(mx, my); // clicks elsewhere just close the dropdown
        }

        if (hit("min", mx, my)) {
            config.panelCollapsed = !config.panelCollapsed;
            config.save();
            return true;
        }
        if (hit("t.getdata", mx, my)) {
            if (scanner.guideActive()) scanner.cancelGuidedScan();
            else scanner.startGuidedScan(Minecraft.getInstance());
            return true;
        }
        if (hit("t.powder", mx, my)) {
            config.powderHelperEnabled = !config.powderHelperEnabled;
            config.save();
            return true;
        }
        if (hit("nd", mx, my)) {
            notifOpen = true;
            dropdownOpen = false;
            return true;
        }
        if (hit("t.renotify", mx, my)) {
            config.renotifySeconds = nextRenotify(config.renotifySeconds);
            config.save();
            return true;
        }
        if (hit("t.routing", mx, my)) {
            config.routingOverlay = !config.routingOverlay;
            config.save();
            return true;
        }
        if (hit("dd", mx, my)) {
            dropdownOpen = true;
            notifOpen = false;
            return true;
        }
        for (String key : new String[]{"target", "wrong", "step"}) {
            if (hit("c." + key, mx, my)) {
                openPicker(key);
                return true;
            }
        }
        if (inHeader(settingsX, settingsY, mx, my)) {
            drag = Drag.SETTINGS;
            grabDX = mx - settingsX;
            grabDY = my - settingsY;
            return true;
        }
        if (config.powderHelperEnabled && inHeader(powderX, powderY, mx, my)) {
            drag = Drag.POWDER;
            grabDX = mx - powderX;
            grabDY = my - powderY;
            return true;
        }
        // Swallow clicks anywhere on either GUI body so they can't hit the menu behind it.
        return overAnyPanel(mx, my);
    }

    private static boolean inHeader(int x, int y, int mx, int my) {
        return mx >= x && mx < x + PANEL_W && my >= y && my < y + HEADER_H;
    }

    private boolean overAnyPanel(int mx, int my) {
        if (inside(new int[]{settingsX, settingsY, PANEL_W, settingsTotalHeight()}, mx, my)) return true;
        return config.powderHelperEnabled
                && inside(new int[]{powderX, powderY, PANEL_W, powderTotalHeight()}, mx, my);
    }

    private boolean hit(String id, int mx, int my) {
        int[] r = hits.get(id);
        return r != null && inside(r, mx, my);
    }

    private static boolean inside(int[] r, int mx, int my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private static int nextRenotify(int current) {
        for (int i = 0; i < RENOTIFY_CHOICES.length; i++) {
            if (RENOTIFY_CHOICES[i] == current) return RENOTIFY_CHOICES[(i + 1) % RENOTIFY_CHOICES.length];
        }
        return RENOTIFY_CHOICES[0];
    }

    private static String renotifyLabel(int seconds) {
        if (seconds <= 0) return "Once";
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m";
    }

    // --- path-change popup --------------------------------------------------

    /**
     * Builds the popup shown after picking a path (replaces the old chat message): always
     * names the new path; when a powder grind is picked below HOTM Tier
     * {@link HotmScanner#MIN_POWDER_TIER} it advises against it and recommends the HOTM path.
     */
    private void showPathPopup() {
        popupLines.clear();
        Progression p = config.activePath;
        popupLines.add("Path set to: " + pathName(p));
        int tier = config.data(Progression.HOTM).highestUnlockedTier;
        boolean warn = p != null && p.isPowder() && tier > 0 && tier < HotmScanner.MIN_POWDER_TIER;
        if (warn) {
            popupLines.add("You're HOTM " + tier + " — powder grinds below Tier "
                    + HotmScanner.MIN_POWDER_TIER + " are advised against.");
            popupLines.add("Recommended: set your Path to HOTM instead.");
        }
        popupUntilMs = System.currentTimeMillis() + (warn ? 8000 : 3000);
    }

    /** Toast-style popup centered near the top of the screen; warning lines in yellow. */
    private void drawPopup(GuiGraphicsExtractor gfx, Font font) {
        int w = 0;
        for (String line : popupLines) w = Math.max(w, font.width(line));
        w += 2 * (PAD + 3);
        int h = 2 * PAD + popupLines.size() * LINE;
        int x = (gfx.guiWidth() - w) / 2;
        int y = Math.max(4, gfx.guiHeight() / 6 - h / 2);
        gfx.fillGradient(x, y, x + w, y + h, 0xF8181820, 0xF8202030);
        outline(gfx, x, y, w, h, GOLD);
        int ty = y + PAD;
        boolean first = true;
        for (String line : popupLines) {
            gfx.centeredText(font, line, x + w / 2, ty, first ? GOLD : YELLOW);
            first = false;
            ty += LINE;
        }
    }

    // --- color picker -------------------------------------------------------

    private void openPicker(String key) {
        dropdownOpen = false;
        notifOpen = false;
        pickerKey = key;
        float[] hsv = rgbToHsv(editedColor() & 0xFFFFFF);
        pickHue = hsv[0];
        pickSat = hsv[1];
        pickVal = hsv[2];
    }

    private int editedColor() {
        return switch (pickerKey) {
            case "wrong" -> config.colorWrong;
            case "step" -> config.colorStep;
            default -> config.colorTarget;
        };
    }

    /** Writes the picked HSV back to the edited config color (keeping the 0xCC alpha). */
    private void applyPicked() {
        int argb = 0xCC000000 | hsvToRgb(pickHue, pickSat, pickVal);
        switch (pickerKey) {
            case "wrong" -> config.colorWrong = argb;
            case "step" -> config.colorStep = argb;
            default -> config.colorTarget = argb;
        }
    }

    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float hp = (((h % 360f) + 360f) % 360f) / 60f;
        float x = c * (1 - Math.abs(hp % 2f - 1));
        float r = 0, g = 0, b = 0;
        switch ((int) hp) {
            case 0 -> { r = c; g = x; }
            case 1 -> { r = x; g = c; }
            case 2 -> { g = c; b = x; }
            case 3 -> { g = x; b = c; }
            case 4 -> { r = x; b = c; }
            default -> { r = c; b = x; }
        }
        float m = v - c;
        return (Math.round((r + m) * 255) << 16)
                | (Math.round((g + m) * 255) << 8)
                | Math.round((b + m) * 255);
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * ((b - r) / d + 2f);
        else h = 60f * ((r - g) / d + 4f);
        if (h < 0) h += 360f;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private int settingsHeight() {
        return 2 * PAD + SETTINGS_ROWS * ROW_H;
    }

    private int settingsTotalHeight() {
        return config.panelCollapsed ? HEADER_H : HEADER_H + GAP + settingsHeight();
    }

    private int powderTotalHeight() {
        return HEADER_H + 3 * (GAP + CARD_H);
    }

    // --- rendering ----------------------------------------------------------

    private void render(Screen screen, GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
        Minecraft mc = Minecraft.getInstance();

        long now = System.currentTimeMillis();
        if (now - lastPoll >= POLL_MS) {
            lastPoll = now;
            tracker.scanOpenMenu(mc);
        }

        int[] origin = MenuNav.guiOrigin(cs);
        int[] size = MenuNav.guiSize(cs);
        int defaultX = origin[0] + size[0] + 8;
        // If there's no room on the right (tiny window / huge GUI scale), flip to the left side.
        if (defaultX + PANEL_W > gfx.guiWidth() - 2) defaultX = Math.max(2, origin[0] - PANEL_W - 8);
        int defaultY = origin[1];
        // The Powder Helper's default anchor sits below the settings panel's default spot.
        int powderDefaultY = defaultY + HEADER_H + GAP + settingsHeight() + GAP;

        if (drag == Drag.SETTINGS) {
            config.panelOffsetX = mouseX - grabDX - defaultX;
            config.panelOffsetY = mouseY - grabDY - defaultY;
        } else if (drag == Drag.POWDER) {
            config.powderPanelOffsetX = mouseX - grabDX - defaultX;
            config.powderPanelOffsetY = mouseY - grabDY - powderDefaultY;
        }
        settingsX = clamp(defaultX + config.panelOffsetX, 0, gfx.guiWidth() - PANEL_W);
        settingsY = clamp(defaultY + config.panelOffsetY, 0, Math.max(0, gfx.guiHeight() - settingsTotalHeight()));
        powderX = clamp(defaultX + config.powderPanelOffsetX, 0, gfx.guiWidth() - PANEL_W);
        powderY = clamp(powderDefaultY + config.powderPanelOffsetY, 0, Math.max(0, gfx.guiHeight() - powderTotalHeight()));

        // The picker pops up under the settings panel, right-aligned with it.
        pickerX = clamp(settingsX + PANEL_W - PICKER_W, 0, gfx.guiWidth() - PICKER_W);
        pickerY = clamp(settingsY + settingsTotalHeight() + GAP, 0, Math.max(0, gfx.guiHeight() - PICKER_H));
        if (pickerKey != null) {
            int svX = pickerX + PAD;
            int svY = pickerY + PAD;
            if (drag == Drag.PICK_SV) {
                pickSat = clamp01((mouseX - svX) / (float) (SV_SIZE - 1));
                pickVal = 1f - clamp01((mouseY - svY) / (float) (SV_SIZE - 1));
                applyPicked();
            } else if (drag == Drag.PICK_HUE) {
                pickHue = 360f * clamp01((mouseY - svY) / (float) (SV_SIZE - 1));
                applyPicked();
            }
        }

        hits.clear();
        Font font = mc.font;

        drawSettingsPanel(gfx, font);
        if (config.powderHelperEnabled) drawPowderPanel(gfx, font);
        if (dropdownOpen) drawDropdown(gfx, font);
        if (notifOpen) drawNotifDropdown(gfx, font);
        if (pickerKey != null) drawPicker(gfx, font);

        tooltipIfHovered(gfx, mouseX, mouseY, "t.getdata",
                "Scan your tree — right-click the arrows it highlights");

        if (!popupLines.isEmpty() && System.currentTimeMillis() < popupUntilMs) {
            drawPopup(gfx, font);
        }
    }

    private void tooltipIfHovered(GuiGraphicsExtractor gfx, int mx, int my, String id, String text) {
        int[] r = hits.get(id);
        if (r != null && inside(r, mx, my)) {
            gfx.setTooltipForNextFrame(Component.literal(text), mx, my);
        }
    }

    /** The RGB picker popup: hue bar + saturation/value square, applied to the color live. */
    private void drawPicker(GuiGraphicsExtractor gfx, Font font) {
        int x = pickerX;
        int y = pickerY;
        gfx.fillGradient(x, y, x + PICKER_W, y + PICKER_H, 0xF8181820, 0xF8202030);
        outline(gfx, x, y, PICKER_W, PICKER_H, GOLD);

        // Saturation/value square: one exact vertical gradient per column (hsv is linear in v).
        int svX = x + PAD;
        int svY = y + PAD;
        for (int c = 0; c < SV_SIZE; c++) {
            int top = 0xFF000000 | hsvToRgb(pickHue, c / (float) (SV_SIZE - 1), 1f);
            gfx.fillGradient(svX + c, svY, svX + c + 1, svY + SV_SIZE, top, 0xFF000000);
        }
        hits.put("pk.sv", new int[]{svX, svY, SV_SIZE, SV_SIZE});
        int mx = svX + Math.round(pickSat * (SV_SIZE - 1));
        int my = svY + Math.round((1f - pickVal) * (SV_SIZE - 1));
        outline(gfx, mx - 2, my - 2, 5, 5, 0xFFFFFFFF);

        // Hue bar: the six rainbow segments.
        int hx = svX + SV_SIZE + GAP;
        int seg = SV_SIZE / 6;
        for (int i = 0; i < 6; i++) {
            int c1 = 0xFF000000 | hsvToRgb(i * 60f, 1f, 1f);
            int c2 = 0xFF000000 | hsvToRgb((i + 1) * 60f % 360f, 1f, 1f);
            gfx.fillGradient(hx, svY + i * seg, hx + HUE_W, svY + (i + 1) * seg, c1, c2);
        }
        hits.put("pk.hue", new int[]{hx, svY, HUE_W, SV_SIZE});
        int hy = svY + Math.round(pickHue / 360f * (SV_SIZE - 1));
        outline(gfx, hx - 1, hy - 1, HUE_W + 2, 3, 0xFFFFFFFF);

        // Preview + hex readout.
        int py = svY + SV_SIZE + 4;
        int rgb = hsvToRgb(pickHue, pickSat, pickVal);
        gfx.fill(svX, py, svX + 10, py + 10, 0xFF000000 | rgb);
        outline(gfx, svX, py, 10, 10, GRAY);
        gfx.text(font, String.format("#%06X", rgb), svX + 14, py + 1, WHITE);
    }

    private void drawSettingsPanel(GuiGraphicsExtractor gfx, Font font) {
        int y = settingsY;
        box(gfx, settingsX, y, HEADER_H, GOLD);
        gfx.centeredText(font, "Tree Helper", settingsX + PANEL_W / 2, y + (HEADER_H - 8) / 2 + 1, GOLD);
        int bx = settingsX + PANEL_W - 14;
        int by = y + 2;
        gfx.fill(bx, by, bx + 12, by + 12, BAR_BG);
        gfx.centeredText(font, config.panelCollapsed ? "+" : "-", bx + 6, by + 2, GOLD);
        hits.put("min", new int[]{bx, by, 12, 12});
        if (config.panelCollapsed) return;
        y += HEADER_H + GAP;

        box(gfx, settingsX, y, settingsHeight(), SETTINGS_BORDER);
        int tx = settingsX + PAD;
        int right = settingsX + PANEL_W - PAD;
        int ry = y + PAD;

        // Get Data: starts the guided scan (highlighted arrows, user right-clicks them).
        gfx.text(font, "Get Data", tx, ry + 2, GRAY);
        boolean scanning = scanner.guideActive();
        rightText(gfx, font, scanning ? "[Cancel]" : "[Start]", right, ry + 2, scanning ? RED : GREEN);
        hits.put("t.getdata", rowRect(ry));
        ry += ROW_H;

        toggleRow(gfx, font, tx, right, ry, "Powder helper", config.powderHelperEnabled, "t.powder");
        ry += ROW_H;

        gfx.text(font, "Path:", tx, ry + 2, GRAY);
        rightText(gfx, font, pathName(config.activePath) + " v", right, ry + 2, WHITE);
        hits.put("dd", rowRect(ry));
        ry += ROW_H;

        gfx.text(font, "Notifications:", tx, ry + 2, GRAY);
        rightText(gfx, font, notifSummary() + " v", right, ry + 2, WHITE);
        hits.put("nd", rowRect(ry));
        ry += ROW_H;

        gfx.text(font, "Re-notify:", tx, ry + 2, GRAY);
        rightText(gfx, font, renotifyLabel(config.renotifySeconds), right, ry + 2, WHITE);
        hits.put("t.renotify", rowRect(ry));
        ry += ROW_H;

        toggleRow(gfx, font, tx, right, ry, "Routing overlay", config.routingOverlay, "t.routing");
        ry += ROW_H;

        // One labeled row per overlay color so it's clear what each swatch changes.
        colorRow(gfx, font, tx, right, ry, "Perks to enable", config.colorTarget, "c.target");
        ry += ROW_H;
        colorRow(gfx, font, tx, right, ry, "Perks to disable", config.colorWrong, "c.wrong");
        ry += ROW_H;
        colorRow(gfx, font, tx, right, ry, "Next upgrade", config.colorStep, "c.step");
    }

    /** A settings row with a text label and its clickable color swatch on the right. */
    private void colorRow(GuiGraphicsExtractor gfx, Font font, int tx, int right, int ry,
                          String label, int color, String id) {
        gfx.text(font, label, tx, ry + 2, GRAY);
        swatch(gfx, right - 10, ry + 1, color, id);
    }

    private void drawPowderPanel(GuiGraphicsExtractor gfx, Font font) {
        int y = powderY;
        box(gfx, powderX, y, HEADER_H, GOLD);
        gfx.centeredText(font, "Powder Helper", powderX + PANEL_W / 2, y + (HEADER_H - 8) / 2 + 1, GOLD);
        y += HEADER_H;
        for (Progression p : Progression.values()) {
            if (!p.isPowder()) continue;
            y += GAP;
            drawCard(gfx, font, powderX, y, p);
            y += CARD_H;
        }
    }

    private void toggleRow(GuiGraphicsExtractor gfx, Font font, int tx, int right, int ry,
                           String label, boolean on, String id) {
        gfx.text(font, label, tx, ry + 2, GRAY);
        rightText(gfx, font, on ? "ON" : "OFF", right, ry + 2, on ? GREEN : RED);
        hits.put(id, rowRect(ry));
    }

    private int[] rowRect(int ry) {
        return new int[]{settingsX + 1, ry, PANEL_W - 2, ROW_H};
    }

    private void swatch(GuiGraphicsExtractor gfx, int x, int y, int color, String id) {
        gfx.fill(x, y, x + 10, y + 10, 0xFF000000 | color); // full alpha so it reads clearly
        outline(gfx, x, y, 10, 10, GRAY);
        hits.put(id, new int[]{x, y, 10, 10});
    }

    /** Short state of the two notification types for the closed Notifications row. */
    private String notifSummary() {
        if (config.chatNotifications && config.grindAdvice) return "All";
        if (!config.chatNotifications && !config.grindAdvice) return "Off";
        return "Some";
    }

    /** The open Notifications dropdown: one ON/OFF toggle row per notification type. */
    private void drawNotifDropdown(GuiGraphicsExtractor gfx, Font font) {
        int[] anchor = hits.get("nd");
        if (anchor == null) return;
        int w = 96;
        int x = settingsX + PANEL_W - PAD - w;
        int y = anchor[1] + ROW_H;
        int h = 2 * ROW_H + 2;
        gfx.fillGradient(x, y, x + w, y + h, 0xF8181820, 0xF8202030);
        outline(gfx, x, y, w, h, GOLD);

        notifOption(gfx, font, x, y + 1, w, "Upgrade ready", config.chatNotifications, "nopt:ready");
        notifOption(gfx, font, x, y + 1 + ROW_H, w, "Grind advice", config.grindAdvice, "nopt:advice");
    }

    private void notifOption(GuiGraphicsExtractor gfx, Font font, int x, int y, int w,
                             String label, boolean on, String id) {
        gfx.text(font, label, x + 4, y + 3, WHITE);
        rightText(gfx, font, on ? "ON" : "OFF", x + w - 4, y + 3, on ? GREEN : RED);
        hits.put(id, new int[]{x, y, w, ROW_H});
    }

    /** The open path dropdown, drawn last so it sits on top of everything. */
    private void drawDropdown(GuiGraphicsExtractor gfx, Font font) {
        int[] anchor = hits.get("dd");
        if (anchor == null) return;
        int w = 76;
        int x = settingsX + PANEL_W - PAD - w;
        int y = anchor[1] + ROW_H;
        String[] options = {"NONE", "HOTM", "MITHRIL", "GEMSTONE", "GLACITE"};
        int h = options.length * ROW_H + 2;
        gfx.fillGradient(x, y, x + w, y + h, 0xF8181820, 0xF8202030);
        outline(gfx, x, y, w, h, GOLD);
        int oy = y + 1;
        for (String opt : options) {
            Progression p = Progression.fromArg(opt);
            boolean selected = config.activePath == p;
            gfx.text(font, pathName(p), x + 4, oy + 3, selected ? GOLD : WHITE);
            hits.put("opt:" + opt, new int[]{x, oy, w, ROW_H});
            oy += ROW_H;
        }
    }

    private static String pathName(Progression p) {
        if (p == null) return "None";
        if (p == Progression.HOTM) return "HOTM";
        return p.displayName.replace(" Powder", "");
    }

    private void drawCard(GuiGraphicsExtractor gfx, Font font, int x, int y, Progression type) {
        Progression grind = config.activePath;
        TreeConfig.ProgressionData data = config.data(type);
        int accent = accent(type);
        box(gfx, x, y, CARD_H, accent);

        var steps = UpgradePlan.steps(grind, type);
        UpgradePlan.Step next = UpgradePlan.nextStep(grind, type, tracker::levelOf);

        int tx = x + PAD;
        int right = x + PANEL_W - PAD;
        int ty = y + PAD;

        // Row 1: powder name + plan progress.
        gfx.text(font, type.displayName.replace(" Powder", ""), tx, ty, accent);
        if (steps != null) {
            rightText(gfx, font,
                    UpgradePlan.stepsDone(grind, type, tracker::levelOf) + "/" + steps.size(),
                    right, ty, GRAY);
        }
        ty += LINE;

        // Row 2: live spendable balance.
        long have = data.currentPowder;
        gfx.text(font, "Powder:", tx, ty, GRAY);
        rightText(gfx, font, have < 0 ? "—" : PowderTracker.fmt(have), right, ty, WHITE);
        ty += LINE;

        // Row 3 + 4: the next upgrade step (or why there isn't one).
        gfx.text(font, "Next upgrade:", tx, ty, GRAY);
        ty += LINE;
        if (grind == null) {
            gfx.text(font, "Pick a path first", tx, ty, GRAY);
            return;
        }
        if (steps == null) {
            gfx.text(font, grind == Progression.GLACITE ? "Commissions → HOTM 10"
                    : "No steps on this path", tx, ty, GRAY);
            return;
        }
        if (next == null) {
            gfx.text(font, "Plan complete!", tx, ty, GREEN);
            return;
        }
        int level = tracker.levelOf(next.perk());
        if (level <= 0) {
            // The step's perk isn't unlocked -> there is no upgrade to show yet.
            int tier = TreeData.NODE_TIERS.getOrDefault(next.perk(), 0);
            int myTier = config.data(Progression.HOTM).highestUnlockedTier;
            gfx.text(font, myTier > 0 && tier > myTier ? "Requires HOTM " + tier
                    : "Perk not unlocked", tx, ty, GRAY);
            return;
        }
        gfx.text(font, next.label(level), tx, ty, WHITE);
        ty += LINE;

        // Row 5 + 6: powder progress toward affording the whole step.
        long cost = UpgradePlan.costToFinish(next, level);
        int barW = PANEL_W - 2 * PAD;
        gfx.fill(tx, ty, tx + barW, ty + 4, BAR_BG);
        if (cost > 0 && have > 0) {
            int fill = (int) Math.round(barW * Math.min(1.0, have / (double) cost));
            if (fill > 0) gfx.fill(tx, ty, tx + fill, ty + 4, accent);
        }
        ty += 4 + 3;
        if (cost < 0 || have < 0) {
            gfx.text(font, "Cost unknown", tx, ty, GRAY);
        } else if (have >= cost) {
            gfx.text(font, "Ready! (" + PowderTracker.fmt(cost) + ")", tx, ty, GREEN);
        } else {
            gfx.text(font, "Need " + PowderTracker.fmt(cost - have) + " more", tx, ty, YELLOW);
        }
    }

    /** Per-powder accent color: mithril teal-green, gemstone magenta, glacite ice blue. */
    private static int accent(Progression p) {
        return switch (p) {
            case MITHRIL -> 0xFF3CE6A0;
            case GEMSTONE -> 0xFFE066FF;
            case GLACITE -> 0xFF66C6FF;
            default -> GOLD;
        };
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Panel chrome: vertical gradient fill with a 1 px border. */
    private static void box(GuiGraphicsExtractor gfx, int x, int y, int h, int border) {
        gfx.fillGradient(x, y, x + PANEL_W, y + h, BG_TOP, BG_BOTTOM);
        outline(gfx, x, y, PANEL_W, h, border);
    }

    private static void outline(GuiGraphicsExtractor gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x, y, x + w, y + 1, color);
        gfx.fill(x, y + h - 1, x + w, y + h, color);
        gfx.fill(x, y, x + 1, y + h, color);
        gfx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void rightText(GuiGraphicsExtractor gfx, Font font, String s, int rightX, int y, int color) {
        gfx.text(font, s, rightX - font.width(s), y, color);
    }
}
