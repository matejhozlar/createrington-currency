package com.saunhardy.createringtoncurrency.util;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Deposits {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private static final String REFUNDED = " Your bills were returned.";

    public interface Reporter {
        void started(ServerPlayer player, long amount);
        void succeeded(ServerPlayer player, long amount, @Nullable String playerMessage);
        void failed(ServerPlayer player, String text);
    }

    private Deposits() {}

    public static void onServerStopped(ServerStoppedEvent event) {
        IN_FLIGHT.clear();
    }

    public static void depositAll(ServerPlayer player, String tag, Reporter reporter) {
        if (!CurrencyApi.isAvailable()) {
            reporter.failed(player, "The bank is not available on this server.");
            return;
        }
        int[] bills = Bills.count(player.getInventory());
        long amount = Bills.value(bills);
        if (amount <= 0) {
            reporter.failed(player, "No bills to deposit.");
            return;
        }
        UUID uuid = player.getUUID();
        if (!IN_FLIGHT.add(uuid)) {
            reporter.failed(player, "A deposit is already in progress.");
            return;
        }

        MinecraftServer server = player.server;
        String name = player.getName().getString();
        String refundReason = "the refund of a failed deposit";
        String key = CurrencyApi.newIdempotencyKey();
        try {
            Bills.extract(player.getInventory(), bills);
            player.inventoryMenu.sendAllDataToRemote();
            reporter.started(player, amount);
            CurrencyApi.deposit(uuid, amount, key).whenComplete((resp, ex) -> {
                IN_FLIGHT.remove(uuid);
                if (ex == null && resp.isSuccess()) {
                    LOGGER.info("[DEPOSIT:{}] {} ({}): ${} key={}", tag, name, uuid, Bills.fmt(amount), key);
                    BillDelivery.whenOnline(server, uuid, p -> reporter.succeeded(p, amount, resp.getPlayerMessage()));
                    return;
                }

                BillDelivery.deliver(server, uuid, bills, refundReason);
                String text;
                if (ex != null) {
                    LOGGER.error("[DEPOSIT:{}] {} ({}): ${} key={} failed, bills returned: {}", tag, name, uuid, Bills.fmt(amount), key, ex.getMessage());
                    text = "Something went wrong. Please try again." + REFUNDED;
                } else {
                    LOGGER.warn("[DEPOSIT:{}] {} ({}): ${} key={} rejected, bills returned: {}", tag, name, uuid, Bills.fmt(amount), key, resp.getMessage());
                    text = CurrencyApi.errorText(resp, "Deposit failed. Please try again.") + REFUNDED;
                }
                BillDelivery.whenOnline(server, uuid, p -> reporter.failed(p, text));
            });
        } catch (RuntimeException e) {
            IN_FLIGHT.remove(uuid);
            BillDelivery.deliver(server, uuid, bills, refundReason);
            LOGGER.error("[DEPOSIT:{}] {} ({}): ${} key={} could not be submitted, bills returned", tag, name, uuid, Bills.fmt(amount), key, e);
            reporter.failed(player, "Something went wrong. Please try again." + REFUNDED);
        }
    }
}
