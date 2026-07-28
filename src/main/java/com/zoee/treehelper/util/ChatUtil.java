package com.zoee.treehelper.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Client-only chat output. Safe to call from any thread — routes onto the client thread. */
public final class ChatUtil {

    private static final Component PREFIX =
            Component.literal("[Tree Helper] ").withStyle(ChatFormatting.AQUA);

    private ChatUtil() {}

    public static void send(String text, ChatFormatting color) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            MutableComponent body = Component.literal(text).withStyle(color);
            mc.player.sendSystemMessage(Component.empty().append(PREFIX).append(body));
        });
    }

    public static void send(String text) {
        send(text, ChatFormatting.WHITE);
    }

    /** Prefixed send of an already-styled component (for mixed-color lines). */
    public static void send(Component body) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            mc.player.sendSystemMessage(Component.empty().append(PREFIX).append(body));
        });
    }
}
