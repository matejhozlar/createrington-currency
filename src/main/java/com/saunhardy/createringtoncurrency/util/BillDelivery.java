package com.saunhardy.createringtoncurrency.util;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.text.NumberFormat;
import java.util.UUID;

public final class BillDelivery {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BillDelivery() {}

    public static void deliver(MinecraftServer server, UUID uuid, int[] counts, String reason) {
        if (Bills.isEmpty(counts)) return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                Bills.give(player, counts);
                player.inventoryMenu.sendAllDataToRemote();
                return;
            }
            PendingBillsData.get(server).add(uuid, counts);
            LOGGER.warn("[BILLS] {} is offline; ${} from {} queued until their next login",
                    uuid, fmt(Bills.value(counts)), reason);
        });
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int[] counts = PendingBillsData.get(player.server).take(player.getUUID());
        if (counts == null) return;

        Bills.give(player, counts);
        player.inventoryMenu.sendAllDataToRemote();
        String amount = fmt(Bills.value(counts));
        player.sendSystemMessage(Component.literal("💵 $" + amount
                + " in bills from a transaction that finished while you were offline were placed in your inventory.")
                .withStyle(ChatFormatting.GOLD));
        LOGGER.info("[BILLS] Handed ${} of queued bills to {} ({})", amount, player.getName().getString(), player.getUUID());
    }

    private static String fmt(long amount) {
        return NumberFormat.getInstance().format(amount);
    }
}
