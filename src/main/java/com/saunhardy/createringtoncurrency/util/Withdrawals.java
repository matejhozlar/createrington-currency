package com.saunhardy.createringtoncurrency.util;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.slf4j.Logger;

import java.text.NumberFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class Withdrawals {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    public static final int MAX_BILLS = Inventory.INVENTORY_SIZE * 64;

    public interface Reporter {
        void succeeded(long amount);
        void failed(String text);
    }

    private Withdrawals() {}

    public static void withdraw(ServerPlayer player, int[] counts, String tag, Reporter reporter) {
        if (counts.length != Bills.DENOMINATIONS.length) {
            reporter.failed("Invalid amount.");
            return;
        }
        long total = 0;
        long bills = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] < 0) {
                reporter.failed("Invalid amount.");
                return;
            }
            bills += counts[i];
            total += (long) counts[i] * Bills.DENOMINATIONS[i];
        }
        final long amount = total;
        if (bills == 0) {
            reporter.failed("Invalid amount.");
            return;
        }
        if (bills > MAX_BILLS || !Bills.fitsInventory(player, counts)) {
            reporter.failed("Not enough inventory space for " + fmt(bills) + " bills.");
            return;
        }
        UUID uuid = player.getUUID();
        if (!IN_FLIGHT.add(uuid)) {
            reporter.failed("A withdrawal is already in progress.");
            return;
        }

        MinecraftServer server = player.server;
        String name = player.getName().getString();
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean rejected = new AtomicBoolean();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        int steps = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) continue;
            steps++;
            final int index = i;
            final int count = counts[i];
            final int denomination = Bills.DENOMINATIONS[i];
            chain = chain.thenCompose(ignored -> {
                if (rejected.get()) return CompletableFuture.completedFuture(null);
                return CurrencyApi.withdraw(uuid, denomination, count).thenAccept(resp -> {
                    if (!resp.isSuccess()) {
                        rejected.set(true);
                        LOGGER.warn("[WITHDRAW:{}] {} ({}): {} x ${} rejected: {}", tag, name, uuid, count, denomination, resp.getMessage());
                        reporter.failed(CurrencyApi.errorText(resp, "Withdraw failed. Please try again."));
                        return;
                    }
                    completed.incrementAndGet();
                    BillDelivery.deliver(server, uuid, Bills.only(index, count), "a withdrawal");
                });
            });
        }

        final int totalSteps = steps;
        chain.whenComplete((ignored, ex) -> {
            IN_FLIGHT.remove(uuid);
            if (ex != null) {
                LOGGER.error("[WITHDRAW:{}] {} ({}): ${} failed after {}/{} denominations: {}",
                        tag, name, uuid, fmt(amount), completed.get(), totalSteps, ex.getMessage());
                reporter.failed("Something went wrong. Please try again.");
                return;
            }
            if (rejected.get()) {
                if (completed.get() > 0) {
                    LOGGER.warn("[WITHDRAW:{}] {} ({}): partial bundle, {}/{} denominations completed before the rejection; the player kept those bills",
                            tag, name, uuid, completed.get(), totalSteps);
                }
                return;
            }
            LOGGER.info("[WITHDRAW:{}] {} ({}): ${}", tag, name, uuid, fmt(amount));
            reporter.succeeded(amount);
        });
    }

    private static String fmt(long amount) {
        return NumberFormat.getInstance().format(amount);
    }
}
