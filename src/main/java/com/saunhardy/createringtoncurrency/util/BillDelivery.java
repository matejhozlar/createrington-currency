package com.saunhardy.createringtoncurrency.util;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.function.Consumer;

public final class BillDelivery {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BillDelivery() {}

    public static void deliver(MinecraftServer server, UUID uuid, int[] counts, String reason) {
        if (Bills.isEmpty(counts)) return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                queue(server, uuid, counts);
                LOGGER.warn("[BILLS] {} is offline; ${} from {} queued until their next login",
                        uuid, Bills.fmt(Bills.value(counts)), reason);
                return;
            }
            int[] leftover = insert(player, counts);
            if (Bills.isEmpty(leftover)) return;
            queue(server, uuid, leftover);
            String amount = Bills.fmt(Bills.value(leftover));
            player.sendSystemMessage(Component.literal("💵 $" + amount
                    + " in bills did not fit in your inventory; it will be delivered when you next log in.")
                    .withStyle(ChatFormatting.GOLD));
            LOGGER.warn("[BILLS] {} ({}) had no room for ${} from {}; queued until their next login",
                    player.getName().getString(), uuid, amount, reason);
        });
    }

    public static void whenOnline(MinecraftServer server, UUID uuid, Consumer<ServerPlayer> action) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) action.accept(player);
        });
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.server;
        PendingBillsData data = PendingBillsData.get(server);
        int[] counts = data.take(player.getUUID());
        if (counts == null) return;

        int[] leftover = insert(player, counts);
        if (!Bills.isEmpty(leftover)) data.add(player.getUUID(), leftover);
        PendingBillsData.flush(server);

        long delivered = Bills.value(counts) - Bills.value(leftover);
        if (delivered > 0) {
            player.sendSystemMessage(Component.literal("💵 $" + Bills.fmt(delivered)
                    + " in bills from a transaction that finished while you were offline was placed in your inventory.")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (!Bills.isEmpty(leftover)) {
            player.sendSystemMessage(Component.literal("💵 $" + Bills.fmt(Bills.value(leftover))
                    + " in bills is still waiting for you; free up inventory space and rejoin to receive it.")
                    .withStyle(ChatFormatting.GOLD));
        }
        LOGGER.info("[BILLS] Handed ${} of queued bills to {} ({}); ${} still queued",
                Bills.fmt(delivered), player.getName().getString(), player.getUUID(), Bills.fmt(Bills.value(leftover)));
    }

    private static int[] insert(ServerPlayer player, int[] counts) {
        int[] leftover = Bills.insert(new PlayerMainInvWrapper(player.getInventory()), counts);
        player.inventoryMenu.sendAllDataToRemote();
        return leftover;
    }

    private static void queue(MinecraftServer server, UUID uuid, int[] counts) {
        PendingBillsData.get(server).add(uuid, counts);
        PendingBillsData.flush(server);
    }
}
