package com.zoee.treehelper.hotm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chest-menu primitives for MC 26.1.2 (deobf jar / real Mojang names). Every call into
 * Minecraft internals is verified against the 26.1.2 client (ported from the working
 * CoalFlipper mod):
 * <ul>
 *   <li>lore read : {@code ItemStack.get(DataComponents.LORE).lines()}</li>
 *   <li>title     : {@code screen.getTitle().getString()}</li>
 * </ul>
 *
 * <p>Read-only since 2026-07-28: the mod never clicks slots, sends commands, or closes
 * screens — all menu navigation is done by the user (the guided Get Data flow just watches).
 * Everything here is called from the client/render thread.
 */
final class MenuNav {

    private MenuNav() {}

    static String strip(String s) {
        return s == null ? "" : s.replaceAll("§[0-9a-fk-or]", "");
    }

    /**
     * True when connected to a Hypixel server (address ends with {@code hypixel.net}).
     * The mod sends {@code /hotm} and parses menus/tab-list — none of that belongs on
     * other servers, so every active feature is gated on this.
     */
    static boolean onHypixel(Minecraft mc) {
        net.minecraft.client.multiplayer.ServerData server = mc.getCurrentServer();
        if (server == null || server.ip == null) return false; // singleplayer / no server
        String host = server.ip.toLowerCase(Locale.ROOT).trim();
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

    private static AbstractContainerScreen<?> container(Minecraft mc) {
        return mc.screen instanceof AbstractContainerScreen<?> cs ? cs : null;
    }

    /** The currently open container screen, or null. Used by the overlay renderer. */
    static AbstractContainerScreen<?> openContainer(Minecraft mc) {
        return container(mc);
    }

    // AbstractContainerScreen.leftPos/topPos/imageWidth/imageHeight are protected with no
    // getters; read them reflectively.
    private static java.lang.reflect.Field leftField, topField, widthField, heightField;

    /** {@code [leftPos, topPos]} — the screen-space origin the slots are drawn relative to. */
    static int[] guiOrigin(AbstractContainerScreen<?> cs) {
        try {
            if (leftField == null) {
                leftField = AbstractContainerScreen.class.getDeclaredField("leftPos");
                topField = AbstractContainerScreen.class.getDeclaredField("topPos");
                leftField.setAccessible(true);
                topField.setAccessible(true);
            }
            return new int[]{leftField.getInt(cs), topField.getInt(cs)};
        } catch (ReflectiveOperationException e) {
            return new int[]{0, 0};
        }
    }

    /** {@code [imageWidth, imageHeight]} — the size of the drawn GUI texture. */
    static int[] guiSize(AbstractContainerScreen<?> cs) {
        try {
            if (widthField == null) {
                widthField = AbstractContainerScreen.class.getDeclaredField("imageWidth");
                heightField = AbstractContainerScreen.class.getDeclaredField("imageHeight");
                widthField.setAccessible(true);
                heightField.setAccessible(true);
            }
            return new int[]{widthField.getInt(cs), heightField.getInt(cs)};
        } catch (ReflectiveOperationException e) {
            return new int[]{176, 166}; // vanilla chest-GUI size as a fallback
        }
    }

    static String title(Minecraft mc) {
        if (mc.screen == null) return "";
        Component t = mc.screen.getTitle();
        return t == null ? "" : strip(t.getString());
    }

    /** Number of container slots (excludes the trailing 36 player-inventory slots). */
    static int containerSlotCount(Minecraft mc) {
        AbstractContainerScreen<?> cs = container(mc);
        if (cs == null) return 0;
        return Math.max(0, cs.getMenu().slots.size() - 36);
    }

    static String slotName(Minecraft mc, int slot) {
        AbstractContainerScreen<?> cs = container(mc);
        if (cs == null || slot < 0 || slot >= cs.getMenu().slots.size()) return "";
        ItemStack s = cs.getMenu().slots.get(slot).getItem();
        return s.isEmpty() ? "" : strip(s.getHoverName().getString()).trim();
    }

    /** Registry path of the slot's item (e.g. "coal", "emerald_block"), or "" if empty. */
    static String itemPath(Minecraft mc, int slot) {
        AbstractContainerScreen<?> cs = container(mc);
        if (cs == null || slot < 0 || slot >= cs.getMenu().slots.size()) return "";
        ItemStack s = cs.getMenu().slots.get(slot).getItem();
        if (s.isEmpty()) return "";
        return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
    }

    static List<String> slotLore(Minecraft mc, int slot) {
        List<String> out = new ArrayList<>();
        AbstractContainerScreen<?> cs = container(mc);
        if (cs == null || slot < 0 || slot >= cs.getMenu().slots.size()) return out;
        ItemStack s = cs.getMenu().slots.get(slot).getItem();
        if (s.isEmpty()) return out;
        ItemLore lore = s.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) out.add(strip(line.getString()));
        }
        return out;
    }

    /** First container-slot index whose display name contains {@code needle} (case-insensitive), or -1. */
    static int findSlotByName(Minecraft mc, String needle) {
        String want = needle.toLowerCase(Locale.ROOT).trim();
        int n = containerSlotCount(mc);
        for (int i = 0; i < n; i++) {
            String name = slotName(mc, i).toLowerCase(Locale.ROOT);
            if (!name.isEmpty() && name.contains(want)) return i;
        }
        return -1;
    }

    /** Chest width is 9 columns; grid row/col derive from the slot index. */
    static int rowOf(int slot) {
        return slot / 9;
    }

    static int colOf(int slot) {
        return slot % 9;
    }

    /**
     * If the given slot is a perk / pickaxe-ability node, returns it as a {@link PerkNode} with
     * its {@link PerkKind} and {@link PerkState} inferred from the item material; otherwise null.
     * Nodes are identified purely by material, which is far more reliable than tooltip text:
     * <ul>
     *   <li>coal → locked perk, coal block → locked ability</li>
     *   <li>emerald → unlocked perk, diamond → maxed perk</li>
     *   <li>emerald block → enabled ability, redstone block → disabled ability</li>
     * </ul>
     * Any other material (glass fillers, tier blocks, scroll/reset buttons, the Core) → null.
     */
    private static final java.util.regex.Pattern LEVEL_LINE =
            java.util.regex.Pattern.compile("Level (\\d+)/\\d+");

    static PerkNode readPerk(Minecraft mc, int slot) {
        String name = slotName(mc, slot);
        if (name.isEmpty()) return null;

        PerkKind kind;
        PerkState state;
        switch (itemPath(mc, slot)) {
            case "coal"           -> { kind = PerkKind.PERK;    state = PerkState.LOCKED; }
            case "coal_block"     -> { kind = PerkKind.ABILITY; state = PerkState.LOCKED; }
            case "emerald"        -> { kind = PerkKind.PERK;    state = PerkState.UNLOCKED; }
            case "diamond"        -> { kind = PerkKind.PERK;    state = PerkState.MAXED; }
            case "emerald_block"  -> { kind = PerkKind.ABILITY; state = PerkState.ENABLED; }
            case "redstone_block" -> { kind = PerkKind.ABILITY; state = PerkState.DISABLED; }
            default -> { return null; } // not a perk/ability node
        }

        int level = 0;
        if (state != PerkState.LOCKED) {
            for (String line : slotLore(mc, slot)) {
                java.util.regex.Matcher m = LEVEL_LINE.matcher(line);
                if (m.find()) {
                    level = Integer.parseInt(m.group(1));
                    break;
                }
            }
        }
        return new PerkNode(name, kind, state, level, rowOf(slot), colOf(slot));
    }

    /** A cheap fingerprint of the container's visible items, used to detect a scroll refresh. */
    static String signature(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        int n = containerSlotCount(mc);
        for (int i = 0; i < n; i++) sb.append(i).append('=').append(slotName(mc, i)).append(';');
        return sb.toString();
    }

}
