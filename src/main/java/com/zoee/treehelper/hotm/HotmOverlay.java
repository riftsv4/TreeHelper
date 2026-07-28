package com.zoee.treehelper.hotm;

import com.zoee.treehelper.config.Progression;
import com.zoee.treehelper.config.TreeConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Passive highlight overlay ("optimal routing overlay") for the open Heart of the Mountain menu.
 * Three kinds of boxes, colors configurable, all gated by {@code config.routingOverlay}:
 * <ul>
 *   <li><b>target</b> (default green) — node the target build wants enabled but isn't yet;</li>
 *   <li><b>wrong</b> (default red) — node enabled but not part of the target build;</li>
 *   <li><b>step</b> (default blue) — the perk of the current {@link UpgradePlan} upgrade step
 *       per powder type (what you should be levelling right now). Takes priority over the
 *       other two on the same node.</li>
 * </ul>
 * Never clicks anything — it re-evaluates each render tick, so it follows the tree as the
 * player scrolls.
 *
 * <p>Level comes from the last {@code /tree gmd} scan ({@code highestUnlockedTier}); perk
 * levels for the step highlight come from {@code config.perkLevels} (kept fresh by the panel's
 * menu polling).
 */
public final class HotmOverlay {

    private static final String HOTM_TITLE = "heart of the mountain";
    private static final int BORDER = 2; // outline thickness in px
    /** Gold box + label on the scroll arrow the guided Get Data scan wants right-clicked. */
    private static final int GUIDE_COLOR = 0xFFFFC03C;

    private final TreeConfig config;
    private final HotmScanner scanner;

    private HotmOverlay(TreeConfig config, HotmScanner scanner) {
        this.config = config;
        this.scanner = scanner;
    }

    /** Wire the overlay into the client's screen render events. */
    public static void register(TreeConfig config, HotmScanner scanner) {
        HotmOverlay overlay = new HotmOverlay(config, scanner);
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (isHotm(screen)) {
                ScreenEvents.afterExtract(screen).register(overlay::render);
            }
        });
    }

    private static boolean isHotm(Screen s) {
        return s instanceof AbstractContainerScreen<?>
                && s.getTitle() != null
                && s.getTitle().getString().toLowerCase(Locale.ROOT).contains(HOTM_TITLE)
                && MenuNav.onHypixel(Minecraft.getInstance());
    }

    private void render(Screen screen, GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        if (!(screen instanceof AbstractContainerScreen<?> cs)) return;

        // The Get Data arrow highlight is part of an explicit user action, so it draws even
        // with the routing overlay toggled off.
        drawGuideTarget(cs, gfx);

        if (!config.routingOverlay) return;

        Progression grind = config.activePath;
        int level = config.data(Progression.HOTM).highestUnlockedTier;
        Set<String> target = TreeData.enabledFor(grind, level); // null = no guide data

        // The perks being levelled right now: the next plan step per powder type. Only shown
        // while the Powder helper is enabled — turning it off hides the whole powder feature,
        // blue boxes included. A step whose perk sits on a tier the player hasn't unlocked yet
        // is NOT highlighted — it can't be worked on until the tier is reached. (level 0 =
        // never scanned: no tier gating.)
        Set<String> stepPerks = new HashSet<>();
        if (grind != null && config.powderHelperEnabled) {
            for (Progression type : Progression.values()) {
                if (!type.isPowder()) continue;
                UpgradePlan.Step step = UpgradePlan.nextStep(grind, type,
                        perk -> config.perkLevels.getOrDefault(perk, 0));
                if (step == null) continue;
                if (config.perkLevels.getOrDefault(step.perk(), 0) <= 0) continue; // not unlocked
                int tier = TreeData.NODE_TIERS.getOrDefault(step.perk(), 0);
                if (level > 0 && tier > level) continue;
                stepPerks.add(step.perk());
            }
        }
        if (target == null && stepPerks.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        int[] origin = MenuNav.guiOrigin(cs);
        List<Slot> slots = cs.getMenu().slots;
        int n = Math.min(MenuNav.containerSlotCount(mc), slots.size());
        for (int i = 0; i < n; i++) {
            PerkNode node = MenuNav.readPerk(mc, i);
            if (node == null || !TreeData.ALL_NODES.contains(node.name())) continue; // real tree nodes only

            int color;
            if (stepPerks.contains(node.name())) {
                color = config.colorStep;               // the upgrade we're doing right now
            } else if (target == null) {
                continue;
            } else {
                boolean inTarget = target.contains(node.name());
                boolean unlocked = node.state().isUnlocked();
                if (inTarget && !unlocked) {
                    color = config.colorTarget;         // should be enabled, but isn't yet
                } else if (!inTarget && unlocked) {
                    color = config.colorWrong;          // unlocked but not part of the build
                } else {
                    continue;                           // already correct -> no highlight
                }
            }

            Slot s = slots.get(i);
            drawBox(gfx, origin[0] + s.x - 1, origin[1] + s.y - 1, 18, 18, color);
        }
    }

    /** While a guided Get Data scan runs: gold box + "Right-click!" on the arrow to press. */
    private void drawGuideTarget(AbstractContainerScreen<?> cs, GuiGraphicsExtractor gfx) {
        Minecraft mc = Minecraft.getInstance();
        int idx = scanner.guideTargetSlot(mc);
        if (idx < 0 || idx >= cs.getMenu().slots.size()) return;
        Slot s = cs.getMenu().slots.get(idx);
        int[] origin = MenuNav.guiOrigin(cs);
        int x = origin[0] + s.x - 1;
        int y = origin[1] + s.y - 1;
        drawBox(gfx, x, y, 18, 18, GUIDE_COLOR);
        String label = "Right-click!";
        gfx.text(mc.font, label, x - mc.font.width(label) - 4, y + 5, GUIDE_COLOR);
    }

    /** Draw a hollow rectangle outline from four thin filled edges. */
    private static void drawBox(GuiGraphicsExtractor gfx, int x, int y, int w, int h, int argb) {
        gfx.fill(x, y, x + w, y + BORDER, argb);             // top
        gfx.fill(x, y + h - BORDER, x + w, y + h, argb);     // bottom
        gfx.fill(x, y, x + BORDER, y + h, argb);             // left
        gfx.fill(x + w - BORDER, y, x + w, y + h, argb);     // right
    }
}
