package com.saunhardy.createringtoncurrency.util;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class Withdrawals {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    public static final int MAX_BILLS = Inventory.INVENTORY_SIZE * 64;
    static final String MALFORMED = "Invalid request.";
    static final String NOTHING = "Invalid amount.";

    public interface Reporter {
        void succeeded(ServerPlayer player, long amount);
        void failed(ServerPlayer player, String text);
    }

    private Withdrawals() {}

    public static void onServerStopped(ServerStoppedEvent event) {
        IN_FLIGHT.clear();
    }

    @Nullable
    public static String validate(int[] counts) {
        if (counts.length != Bills.DENOMINATIONS.length) return MALFORMED;
        for (int count : counts) {
            if (count < 0) return MALFORMED;
        }
        long bills = Bills.pieces(counts);
        if (bills == 0) return NOTHING;
        if (bills > MAX_BILLS) return noRoom(bills);
        return null;
    }

    static String noRoom(long bills) {
        return "Not enough inventory space for " + Bills.fmt(bills) + " bills.";
    }

    public static void withdraw(ServerPlayer player, int[] counts, String tag, Reporter reporter) {
        UUID uuid = player.getUUID();
        String name = player.getName().getString();

        String problem = validate(counts);
        if (problem != null) {
            if (MALFORMED.equals(problem)) {
                LOGGER.warn("[WITHDRAW:{}] malformed request from {} ({}): {}", tag, name, uuid, Arrays.toString(counts));
            }
            reporter.failed(player, problem);
            return;
        }
        if (!Bills.fitsInventory(player, counts)) {
            reporter.failed(player, noRoom(Bills.pieces(counts)));
            return;
        }
        if (!CurrencyApi.isAvailable()) {
            reporter.failed(player, "The bank is not available on this server.");
            return;
        }
        if (!IN_FLIGHT.add(uuid)) {
            reporter.failed(player, "A withdrawal is already in progress.");
            return;
        }

        MinecraftServer server = player.server;
        long amount = Bills.value(counts);
        CurrencyApi.balance(uuid).whenComplete((resp, ex) -> {
            if (ex != null || !resp.isSuccess() || resp.getData() == null) {
                IN_FLIGHT.remove(uuid);
                LOGGER.warn("[WITHDRAW:{}] {} ({}): ${} not started, balance check failed: {}",
                        tag, name, uuid, Bills.fmt(amount), ex != null ? ex.getMessage() : resp.getMessage());
                BillDelivery.whenOnline(server, uuid, p -> reporter.failed(p, "Could not check your balance. Please try again."));
                return;
            }
            long balance = (long) Math.floor(resp.getData().balance());
            if (balance < amount) {
                IN_FLIGHT.remove(uuid);
                LOGGER.info("[WITHDRAW:{}] {} ({}): ${} refused, balance is ${}", tag, name, uuid, Bills.fmt(amount), Bills.fmt(balance));
                BillDelivery.whenOnline(server, uuid, p -> reporter.failed(p, insufficient(balance)));
                return;
            }
            submit(server, uuid, name, counts, amount, tag, reporter);
        });
    }

    static String insufficient(long balance) {
        return "Insufficient funds: your balance is $" + Bills.fmt(balance) + ".";
    }

    static String partial(long withdrawn, long amount, String reason) {
        return "Only $" + Bills.fmt(withdrawn) + " of $" + Bills.fmt(amount) + " was withdrawn: " + reason;
    }

    private static void submit(MinecraftServer server, UUID uuid, String name, int[] counts, long amount, String tag, Reporter reporter) {
        AtomicInteger completed = new AtomicInteger();
        AtomicLong withdrawn = new AtomicLong();
        AtomicBoolean rejected = new AtomicBoolean();
        Queue<String> keys = new ConcurrentLinkedQueue<>();
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
                String key = CurrencyApi.newIdempotencyKey();
                keys.add(key);
                return CurrencyApi.withdraw(uuid, denomination, count, key).thenAccept(resp -> {
                    if (!resp.isSuccess()) {
                        rejected.set(true);
                        LOGGER.warn("[WITHDRAW:{}] {} ({}): {} x ${} key={} rejected: {}", tag, name, uuid, count, denomination, key, resp.getMessage());
                        String reason = CurrencyApi.errorText(resp, "Withdraw failed. Please try again.");
                        long got = withdrawn.get();
                        String text = got > 0 ? partial(got, amount, reason) : reason;
                        BillDelivery.whenOnline(server, uuid, p -> reporter.failed(p, text));
                        return;
                    }
                    completed.incrementAndGet();
                    withdrawn.addAndGet((long) count * denomination);
                    BillDelivery.deliver(server, uuid, Bills.only(index, count), "a withdrawal");
                });
            });
        }

        final int totalSteps = steps;
        chain.whenComplete((ignored, ex) -> {
            IN_FLIGHT.remove(uuid);
            if (ex != null) {
                LOGGER.error("[WITHDRAW:{}] {} ({}): ${} failed after {}/{} denominations, keys in order={} (the last one failed): {}",
                        tag, name, uuid, Bills.fmt(amount), completed.get(), totalSteps, String.join(",", keys), ex.getMessage());
                long got = withdrawn.get();
                String text = got > 0 ? partial(got, amount, "something went wrong.") : "Something went wrong. Please try again.";
                BillDelivery.whenOnline(server, uuid, p -> reporter.failed(p, text));
                return;
            }
            if (rejected.get()) {
                if (completed.get() > 0) {
                    LOGGER.warn("[WITHDRAW:{}] {} ({}): partial bundle, {}/{} denominations completed before the rejection, keys in order={} (the last one was rejected); the player kept those bills",
                            tag, name, uuid, completed.get(), totalSteps, String.join(",", keys));
                }
                return;
            }
            LOGGER.info("[WITHDRAW:{}] {} ({}): ${} keys={}", tag, name, uuid, Bills.fmt(amount), String.join(",", keys));
            BillDelivery.whenOnline(server, uuid, p -> reporter.succeeded(p, amount));
        });
    }
}
