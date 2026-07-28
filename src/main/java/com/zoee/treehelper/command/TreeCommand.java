package com.zoee.treehelper.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * The client-side {@code /tree} command. Everything the mod does lives in the HOTM-menu GUI
 * (2026-07-28 decision: the command surface was removed for simplicity — path picking,
 * scanning and settings are all panel-only now), so the only command left is
 * {@code /tree} / {@code /tree help}, which points the user at the menu.
 */
public final class TreeCommand {

    private TreeCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var root = literal("tree").executes(ctx -> printHelp(ctx.getSource()));
        root.then(literal("help").executes(ctx -> printHelp(ctx.getSource())));
        dispatcher.register(root);
    }

    private static int printHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Tree Helper").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" lives in your Heart of the Mountain menu.")
                        .withStyle(ChatFormatting.GRAY)));
        source.sendFeedback(Component.literal("Open ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/hotm").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" — pick a Path in the Tree Helper panel, then press ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Get Data").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" and follow the highlighted arrows.")
                        .withStyle(ChatFormatting.GRAY)));
        return 1;
    }
}
