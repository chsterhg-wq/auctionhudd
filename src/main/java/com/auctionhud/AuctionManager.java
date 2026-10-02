package com.auctionhud;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuctionManager {
    /** Payment message. Anchored to the START of the line so typed chat can't spoof it.
     *  Group 1 = username, 2 = number, 3 = optional K/M/B suffix. Edit if Donut's format differs. */
    private static final Pattern PAID = Pattern.compile(
            "(?i)^\\s*(\\.?\\w{3,16}) paid you \\$?\\s*([\\d,]+(?:\\.\\d+)?)\\s*([kmb])?");

    private static final Pattern MONEY = Pattern.compile(
            "\\$\\s*([\\d,]+(?:\\.\\d+)?)\\s*([kKmMbB])?");

    /** Automatically run /ah <item> when an auction starts, to read the item's price. */
    public static final boolean AUTO_SCAN = true;
    /** Post the winner in public chat when the auction ends. */
    public static final boolean ANNOUNCE_IN_CHAT = true;

    private static final long WINNER_SHOW_MS = 15_000;
    private static final long SCAN_TIMEOUT_MS = 8_000;

    public enum State { IDLE, RUNNING, ENDED }

    public State state = State.IDLE;
    public ItemStack item = ItemStack.EMPTY;
    public long endTime;
    public double worth = -1;          // per-item AH price, -1 = unknown
    public boolean scanning = false;
    public boolean debug = false;

    private long showUntil;
    private long scanStart, scanDeadline;
    private final Map<String, Double> totals = new LinkedHashMap<>();
    public String winner = null;
    public double winAmount = 0;

    // ---------- start / stop ----------

    public void start(ItemStack stack, int seconds) {
        Minecraft mc = Minecraft.getInstance();
        item = stack;
        totals.clear();
        winner = null;
        winAmount = 0;
        worth = -1;
        endTime = System.currentTimeMillis() + seconds * 1000L;
        state = State.RUNNING;

        if (AUTO_SCAN && mc.player != null) {
            scanning = true;
            scanStart = System.currentTimeMillis();
            scanDeadline = scanStart + SCAN_TIMEOUT_MS;
            mc.player.connection.sendCommand("ah " + stack.getHoverName().getString());
        }
    }

    public void cancel() {
        state = State.IDLE;
        scanning = false;
        totals.clear();
    }

    public void end() {
        if (state != State.RUNNING) return;
        winner = null;
        winAmount = 0;
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            if (e.getValue() > winAmount) { // strict ">" => earliest bidder wins ties
                winner = e.getKey();
                winAmount = e.getValue();
            }
        }
        state = State.ENDED;
        showUntil = System.currentTimeMillis() + WINNER_SHOW_MS;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String itemName = item.getHoverName().getString();
        if (winner == null) {
            mc.player.displayClientMessage(Component.literal("[Auction] No bids for " + itemName + ".").withStyle(ChatFormatting.YELLOW), false);
        } else {
            String msg = "Auction over! " + winner + " won " + itemName + " for " + formatMoney(winAmount) + "!";
            mc.player.displayClientMessage(Component.literal("[Auction] " + msg).withStyle(ChatFormatting.GREEN), false);
            if (ANNOUNCE_IN_CHAT) mc.player.connection.sendChat(msg);
        }
    }

    // ---------- reading payments ----------

    public void onMessage(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (debug && text.toLowerCase().contains("paid") && mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[Auction debug] " + text).withStyle(ChatFormatting.GRAY), false);
        }
        if (state != State.RUNNING) return;
        if (System.currentTimeMillis() >= endTime) return; // late payments don't count

        Matcher m = PAID.matcher(text);
        if (!m.find()) return;
        double amount = parseMoney(m.group(2), m.group(3));
        if (amount <= 0) return;
        totals.merge(m.group(1), amount, Double::sum); // several payments add up
    }

    // ---------- tick: timer + auction house scan ----------

    public void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (state == State.RUNNING && now >= endTime) end();
        else if (state == State.ENDED && now >= showUntil) state = State.IDLE;
        if (scanning) scan(mc, now);
    }

    private void scan(Minecraft mc, long now) {
        if (state == State.IDLE || mc.player == null) { scanning = false; return; }
        boolean inContainer = mc.screen instanceof AbstractContainerScreen<?>;

        if (now > scanDeadline) {
            scanning = false;
            if (inContainer) mc.player.closeContainer();
            return;
        }
        if (!inContainer || now < scanStart + 700) return; // wait for listings to load

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
        double best = -1;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack s = slot.getItem();
            if (s.isEmpty() || !s.is(item.getItem())) continue;
            double price = priceFromLore(s);
            if (price <= 0) continue;
            double unit = price / s.getCount();
            if (best < 0 || unit < best) best = unit;
        }
        if (best > 0) {
            worth = best;
            scanning = false;
            mc.player.closeContainer();
        }
    }

    private static double priceFromLore(ItemStack s) {
        ItemLore lore = s.get(DataComponents.LORE);
        if (lore == null) return -1;
        // prefer a line that says "price", otherwise any line with a $ amount
        for (int pass = 0; pass < 2; pass++) {
            for (Component line : lore.lines()) {
                String t = line.getString();
                if (pass == 0 && !t.toLowerCase().contains("price")) continue;
                Matcher m = MONEY.matcher(t);
                if (m.find()) return parseMoney(m.group(1), m.group(2));
            }
        }
        return -1;
    }

    // ---------- bid info for the HUD ----------

    public String topBidder() {
        String best = null; double bestAmt = 0;
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            if (e.getValue() > bestAmt) { best = e.getKey(); bestAmt = e.getValue(); }
        }
        return best;
    }

    public double topAmount() {
        String t = topBidder();
        return t == null ? 0 : totals.get(t);
    }

    // ---------- money helpers ----------

    public static double parseMoney(String num, String suffix) {
        double v;
        try { v = Double.parseDouble(num.replace(",", "")); }
        catch (NumberFormatException e) { return -1; }
        if (suffix != null) {
            switch (suffix.toLowerCase()) {
                case "k" -> v *= 1_000;
                case "m" -> v *= 1_000_000;
                case "b" -> v *= 1_000_000_000;
            }
        }
        return v;
    }

    public static String formatMoney(double v) {
        String s;
        if (v >= 1_000_000_000) s = trim(v / 1_000_000_000) + "B";
        else if (v >= 1_000_000) s = trim(v / 1_000_000) + "M";
        else if (v >= 1_000) s = trim(v / 1_000) + "K";
        else s = trim(v);
        return "$" + s;
    }

    private static String trim(double d) {
        return String.format("%.2f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
