package com.auctionhud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class AuctionHud {
    private static final int BG     = 0xE0301060; // dark purple
    private static final int BORDER = 0xFFB45CFF; // bright purple
    private static final int ACCENT = 0xFF7B2CFF;
    private static final int WHITE  = 0xFFFFFFFF;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        AuctionManager a = AuctionMod.AUCTION;
        if (a.state == AuctionManager.State.IDLE || mc.options.hideGui) return;

        Font font = mc.font; // default Minecraft font
        int w = 290, h = 42;
        int x = (g.guiWidth() - w) / 2;
        int y = 4;

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + w, y + 2, ACCENT);

        // item icon on the left
        g.renderItem(a.item, x + 8, y + (h - 16) / 2 + 1);
        int tx = x + 30;
        int right = x + w - 8;

        Component name = a.item.getHoverName();

        if (a.state == AuctionManager.State.RUNNING) {
            long left = Math.max(0, a.endTime - System.currentTimeMillis());
            long secs = (left + 999) / 1000;
            Component time = Component.literal(String.format("%d:%02d", secs / 60, secs % 60))
                    .withStyle(ChatFormatting.BOLD, secs <= 10 ? ChatFormatting.RED : ChatFormatting.WHITE);

            // row 1: item name .......... timer
            g.drawString(font, name, tx, y + 8, WHITE);
            g.drawString(font, time, right - font.width(time), y + 8, WHITE);

            // row 2: worth | top bid
            Component worth;
            if (a.worth > 0) worth = Component.literal("Worth: " + AuctionManager.formatMoney(a.worth)).withStyle(ChatFormatting.GOLD);
            else if (a.scanning) worth = Component.literal("Worth: checking...").withStyle(ChatFormatting.GRAY);
            else worth = Component.literal("Worth: unknown").withStyle(ChatFormatting.GRAY);
            g.drawString(font, worth, tx, y + 24, WHITE);

            String top = a.topBidder();
            Component bid = top == null
                    ? Component.literal("No bids").withStyle(ChatFormatting.GRAY)
                    : Component.literal(top + " " + AuctionManager.formatMoney(a.topAmount())).withStyle(ChatFormatting.LIGHT_PURPLE);
            g.drawString(font, bid, right - font.width(bid), y + 24, WHITE);
        } else {
            Component title = Component.literal("AUCTION ENDED").withStyle(ChatFormatting.BOLD, ChatFormatting.LIGHT_PURPLE);
            g.drawString(font, name, tx, y + 8, WHITE);
            g.drawString(font, title, right - font.width(title), y + 8, WHITE);

            if (a.winner == null) {
                g.drawString(font, Component.literal("No bids").withStyle(ChatFormatting.GRAY), tx, y + 24, WHITE);
            } else {
                Component win = Component.literal("WINNER: " + a.winner).withStyle(ChatFormatting.BOLD, ChatFormatting.GREEN);
                Component amt = Component.literal(AuctionManager.formatMoney(a.winAmount)).withStyle(ChatFormatting.GOLD);
                g.drawString(font, win, tx, y + 24, WHITE);
                g.drawString(font, amt, right - font.width(amt), y + 24, WHITE);
            }
        }
    }
}
