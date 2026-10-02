package com.auctionhud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public class AuctionMod implements ClientModInitializer {
    public static final AuctionManager AUCTION = new AuctionManager();

    @Override
    public void onInitializeClient() {
        // Server messages like "Name paid you $5K" arrive as GAME messages.
        // (Player-typed chat is not read, so people can't fake a bid by typing it.)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) AUCTION.onMessage(message.getString());
        });

        ClientTickEvents.END_CLIENT_TICK.register(AUCTION::tick);

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("auctionhud", "auction_bar"),
                AuctionHud::render);

        ClientCommandRegistrationCallback.EVENT.register(AuctionCommand::register);
    }
}
