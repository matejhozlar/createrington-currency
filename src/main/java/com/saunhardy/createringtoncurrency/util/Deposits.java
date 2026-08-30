package com.saunhardy.createringtoncurrency.util;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.text.NumberFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Deposits {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    public interface Reporter {
        void started(int amount);
        void succeeded(int amount, @Nullable String playerMessage);
        void failed(String text);
    }

    private Deposits() {}

    public static void depositAll(ServerPlayer player, String tag, Reporter reporter) {
        int[] bills = Bills.count(player.getInventory());
        int amount = Bills.value(bills);
        if (amount <= 0) {
            reporter.failed("No bills to deposit.");
            return;
        }
        UUID uuid = player.getUUID();
        if (!IN_FLIGHT.add(uuid)) {
            reporter.failed("A deposit is already in progress.");
            return;
        }

        Bills.extract(player.getInventory(), bills);
        player.inventoryMenu.sendAllDataToRemote();
        reporter.started(amount);

        MinecraftServer server = player.server;
        String name = player.getName().getString();
        CurrencyApi.deposit(uuid, amount).whenComplete((resp, ex) -> {
            IN_FLIGHT.remove(uuid);
            if (ex == null && resp.isSuccess()) {
                LOGGER.info("[DEPOSIT:{}] {} ({}): ${}", tag, name, uuid, fmt(amount));
                reporter.succeeded(amount, resp.getPlayerMessage());
                return;
            }

            BillDelivery.deliver(server, uuid, bills, "the refund of a failed deposit");
            if (ex != null) {
                LOGGER.error("[DEPOSIT:{}] {} ({}): ${} failed, bills returned: {}", tag, name, uuid, fmt(amount), ex.getMessage());
                reporter.failed("Something went wrong. Please try again. Your bills were returned.");
            } else {
                LOGGER.warn("[DEPOSIT:{}] {} ({}): ${} rejected, bills returned: {}", tag, name, uuid, fmt(amount), resp.getMessage());
                reporter.failed(CurrencyApi.errorText(resp, "Deposit failed. Please try again.") + " Your bills were returned.");
            }
        });
    }

    private static String fmt(long amount) {
        return NumberFormat.getInstance().format(amount);
    }
}
