package com.auctionhud;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class AuctionCommand {
    private static final Pattern TIME = Pattern.compile("(?i)(\\d+)\\s*([sm]?)");

    public static void register(CommandDispatcher<FabricClientCommandSource> d, CommandBuildContext ctxBuild) {
        d.register(literal("auction")
                // /auction run <item> [time]   time = 90, 90s, or 2m (default 60s)
                .then(literal("run")
                        .then(argument("item", ItemArgument.item(ctxBuild))
                                .executes(ctx -> run(ctx.getSource(), ctx, "60"))
                                .then(argument("time", StringArgumentType.word())
                                        .executes(ctx -> run(ctx.getSource(), ctx, StringArgumentType.getString(ctx, "time"))))))
                // /auction worth <amount>   manual override, e.g. 385m
                .then(literal("worth")
                        .then(argument("amount", StringArgumentType.word())
                                .executes(ctx -> {
                                    Matcher m = Pattern.compile("(?i)([\\d,.]+)([kmb]?)").matcher(StringArgumentType.getString(ctx, "amount"));
                                    if (!m.matches()) {
                                        ctx.getSource().sendFeedback(Component.literal("Use something like 250k or 1.5m").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    AuctionMod.AUCTION.worth = AuctionManager.parseMoney(m.group(1), m.group(2));
                                    AuctionMod.AUCTION.scanning = false;
                                    return 1;
                                })))
                .then(literal("end").executes(ctx -> { AuctionMod.AUCTION.end(); return 1; }))
                .then(literal("cancel").executes(ctx -> {
                    AuctionMod.AUCTION.cancel();
                    ctx.getSource().sendFeedback(Component.literal("Auction cancelled").withStyle(ChatFormatting.YELLOW));
                    return 1;
                }))
                .then(literal("debug").executes(ctx -> {
                    AuctionMod.AUCTION.debug = !AuctionMod.AUCTION.debug;
                    ctx.getSource().sendFeedback(Component.literal("Debug " + (AuctionMod.AUCTION.debug ? "ON" : "OFF")));
                    return 1;
                })));
    }

    private static int run(FabricClientCommandSource src, com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx, String timeStr) {
        Matcher m = TIME.matcher(timeStr);
        if (!m.matches()) {
            src.sendFeedback(Component.literal("Time must look like 60, 90s or 2m").withStyle(ChatFormatting.RED));
            return 0;
        }
        int n = Integer.parseInt(m.group(1));
        int seconds = m.group(2).equalsIgnoreCase("m") ? n * 60 : n;
        if (seconds < 5 || seconds > 3600) {
            src.sendFeedback(Component.literal("Time must be between 5 seconds and 60 minutes").withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemStack stack = new ItemStack(ItemArgument.getItem(ctx, "item").getItem());
        AuctionMod.AUCTION.start(stack, seconds);
        return 1;
    }
}
