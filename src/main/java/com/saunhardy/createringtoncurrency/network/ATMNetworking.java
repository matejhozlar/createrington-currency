package com.saunhardy.createringtoncurrency.network;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.saunhardy.createrington.api.currency.HistoryResponse;
import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ATMNetworking {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");

        reg.playToServer(ATMDepositPayload.TYPE, ATMDepositPayload.STREAM_CODEC, ATMNetworking::handleDeposit);
        reg.playToServer(ATMWithdrawPayload.TYPE, ATMWithdrawPayload.STREAM_CODEC, ATMNetworking::handleWithdraw);
        reg.playToClient(ATMResultPayload.TYPE, ATMResultPayload.STREAM_CODEC, ATMNetworking::handleResultClient);
        reg.playToServer(ATMQueryBalancePayload.TYPE, ATMQueryBalancePayload.STREAM_CODEC, ATMNetworking::handleQueryBalance);
        reg.playToClient(ATMBalancePayload.TYPE, ATMBalancePayload.STREAM_CODEC, ATMNetworking::handleBalanceClient);
        reg.playToServer(ATMQueryHistoryPayload.TYPE, ATMQueryHistoryPayload.STREAM_CODEC, ATMNetworking::handleQueryHistory);
        reg.playToClient(ATMHistoryPayload.TYPE, ATMHistoryPayload.STREAM_CODEC, ATMNetworking::handleHistoryClient);
    }

    // ---- Server-side handlers ---------------------------------------------

    private static void handleQueryBalance(final ATMQueryBalancePayload pkt, final IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        CurrencyApi.balance(player.getUUID())
                .thenAccept(resp -> {
                    int balance = resp.isSuccess() && resp.getData() != null ? (int) resp.getData().balance() : 0;
                    player.connection.send(new ClientboundCustomPayloadPacket(new ATMBalancePayload(Math.max(0, balance))));
                })
                .exceptionally(ex -> {
                    LOGGER.error("ATM balance query failed for {}: {}", player.getName().getString(), ex.getMessage());
                    player.connection.send(new ClientboundCustomPayloadPacket(new ATMBalancePayload(0)));
                    return null;
                });
    }

    private static void handleQueryHistory(final ATMQueryHistoryPayload pkt, final IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        CurrencyApi.history(player.getUUID(), pkt.page(), 5)
                .thenAccept(resp -> {
                    if (!resp.isSuccess() || resp.getData() == null) {
                        player.connection.send(new ClientboundCustomPayloadPacket(
                                new ATMHistoryPayload(pkt.page(), 0, "[]")));
                        return;
                    }
                    HistoryResponse data = resp.getData();
                    String json = GSON.toJson(data.transactions());
                    player.connection.send(new ClientboundCustomPayloadPacket(
                            new ATMHistoryPayload(data.page(), data.hasMore() ? 1 : 0, json)));
                })
                .exceptionally(ex -> {
                    LOGGER.error("ATM history query failed for {}: {}", player.getName().getString(), ex.getMessage());
                    player.connection.send(new ClientboundCustomPayloadPacket(
                            new ATMHistoryPayload(pkt.page(), 0, "[]")));
                    return null;
                });
    }

    private static void handleDeposit(final ATMDepositPayload pkt, final IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;

        Map<net.minecraft.world.item.Item, Integer> values = Map.of(
                CreateringtonCurrency.BILL_1.get(), 1,
                CreateringtonCurrency.BILL_5.get(), 5,
                CreateringtonCurrency.BILL_10.get(), 10,
                CreateringtonCurrency.BILL_20.get(), 20,
                CreateringtonCurrency.BILL_50.get(), 50,
                CreateringtonCurrency.BILL_100.get(), 100,
                CreateringtonCurrency.BILL_500.get(), 500,
                CreateringtonCurrency.BILL_1000.get(), 1000
        );

        Map<Integer, List<Integer>> slotsByDenom = new HashMap<>();
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (!st.isEmpty() && values.containsKey(st.getItem())) {
                int val = values.get(st.getItem());
                total += val * st.getCount();
                slotsByDenom.computeIfAbsent(val, k -> new ArrayList<>()).add(i);
            }
        }

        if (total <= 0) {
            sendResult(player, 2, "No bills to deposit.");
            return;
        }

        final int totalAmount = total;
        CurrencyApi.deposit(player.getUUID(), totalAmount)
                .thenAccept(resp -> {
                    if (resp.isSuccess()) {
                        player.server.execute(() -> {
                            for (var entry : slotsByDenom.entrySet()) {
                                for (int slot : entry.getValue()) {
                                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                                }
                            }
                            player.inventoryMenu.sendAllDataToRemote();
                            sendResult(player, 1, "Deposited $" + totalAmount);
                        });
                        LOGGER.info("[ATM DEPOSIT] {} ({}): ${}", player.getName().getString(), player.getUUID(), totalAmount);
                    } else {
                        sendResult(player, 2, errorText(resp, "Deposit failed. Please try again."));
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("ATM deposit failed for {}: {}", player.getName().getString(), ex.getMessage());
                    sendResult(player, 2, "Something went wrong. Please try again.");
                    return null;
                });
    }

    private static void handleWithdraw(final ATMWithdrawPayload pkt, final IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;

        if (pkt.a() <= 0 || (pkt.mode() == 0 && pkt.b() <= 0)) {
            sendResult(player, 2, "Invalid amount.");
            return;
        }

        if (pkt.mode() == 0) {
            singleWithdraw(player, pkt.a(), pkt.b());
            return;
        }

        int total = pkt.a();
        int[] denoms = CreateringtonCurrency.DENOMINATIONS;
        Map<Integer, Integer> bundle = new LinkedHashMap<>();
        for (int d : denoms) {
            int cnt = total / d;
            if (cnt > 0) {
                bundle.put(d, cnt);
                total -= d * cnt;
            }
        }
        if (total != 0) {
            sendResult(player, 2, "Cannot make exact change.");
            return;
        }
        optimizedWithdraw(player, bundle, pkt.a());
    }

    private static void singleWithdraw(ServerPlayer player, int denomination, int count) {
        CurrencyApi.withdraw(player.getUUID(), denomination, count)
                .thenAccept(resp -> {
                    if (resp.isSuccess()) {
                        giveBill(player, denomination, count);
                        long amount = (long) denomination * count;
                        sendResult(player, 1, "Withdrew $" + amount);
                        LOGGER.info("[ATM WITHDRAW] {} ({}): ${} ({}x${})", player.getName().getString(), player.getUUID(), amount, count, denomination);
                    } else {
                        sendResult(player, 2, errorText(resp, "Withdraw failed. Please try again."));
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("ATM withdraw failed for {}: {}", player.getName().getString(), ex.getMessage());
                    sendResult(player, 2, "Something went wrong. Please try again.");
                    return null;
                });
    }

    private static void optimizedWithdraw(ServerPlayer player, Map<Integer, Integer> bundle, int totalRequested) {
        final int totalSteps = bundle.size();
        var completedSteps = new java.util.concurrent.atomic.AtomicInteger(0);
        var overall = java.util.concurrent.CompletableFuture.completedFuture(true);
        var failed = new java.util.concurrent.atomic.AtomicBoolean(false);
        for (Map.Entry<Integer, Integer> entry : bundle.entrySet()) {
            int denom = entry.getKey();
            int count = entry.getValue();
            overall = overall.thenCompose(prevOk -> {
                if (failed.get()) return java.util.concurrent.CompletableFuture.completedFuture(false);
                return CurrencyApi.withdraw(player.getUUID(), denom, count).thenApply(resp -> {
                    if (resp.isSuccess()) {
                        giveBill(player, denom, count);
                        completedSteps.incrementAndGet();
                        return true;
                    }
                    failed.set(true);
                    sendResult(player, 2, errorText(resp, "Withdraw failed. Please try again."));
                    return false;
                });
            });
        }
        overall.whenComplete((ignored, ex) -> {
            if (ex != null) {
                LOGGER.error("ATM optimized withdraw failed for {}: {}", player.getName().getString(), ex.getMessage());
                sendResult(player, 2, "Something went wrong. Please try again.");
                return;
            }
            if (failed.get() && completedSteps.get() > 0) {
                LOGGER.warn("[ATM WITHDRAW] Partial optimized bundle for {} ({}): {}/{} denominations completed before failure; player kept those bills",
                        player.getName().getString(), player.getUUID(), completedSteps.get(), totalSteps);
                return;
            }
            if (!failed.get()) {
                sendResult(player, 1, "Withdrawal complete");
                LOGGER.info("[ATM WITHDRAW] {} ({}): ${} (optimized)", player.getName().getString(), player.getUUID(), totalRequested);
            }
        });
    }

    private static void giveBill(ServerPlayer player, int denom, int count) {
        var item = switch (denom) {
            case 1 -> CreateringtonCurrency.BILL_1.get();
            case 5 -> CreateringtonCurrency.BILL_5.get();
            case 10 -> CreateringtonCurrency.BILL_10.get();
            case 20 -> CreateringtonCurrency.BILL_20.get();
            case 50 -> CreateringtonCurrency.BILL_50.get();
            case 100 -> CreateringtonCurrency.BILL_100.get();
            case 500 -> CreateringtonCurrency.BILL_500.get();
            case 1000 -> CreateringtonCurrency.BILL_1000.get();
            default -> null;
        };
        if (item == null) return;
        ItemStack stack = new ItemStack(item, count);
        player.server.execute(() -> player.getInventory().placeItemBackInInventory(stack));
    }

    private static String errorText(com.saunhardy.crnet.http.ApiResponse<?> resp, String fallback) {
        if (resp.getPlayerMessage() != null) return resp.getPlayerMessage();
        if (resp.getMessage() != null) return resp.getMessage();
        return fallback;
    }

    // ---- Client-side handlers ---------------------------------------------

    private static void handleBalanceClient(final ATMBalancePayload pkt, final IPayloadContext ctx) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof com.saunhardy.createringtoncurrency.client.ATMScreen scr) {
                scr.updateBalance(pkt.balance());
            }
        });
    }

    private static void handleHistoryClient(final ATMHistoryPayload pkt, final IPayloadContext ctx) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof com.saunhardy.createringtoncurrency.client.ATMScreen scr) {
                scr.updateHistory(pkt.page(), pkt.hasMore() == 1, pkt.data());
            }
        });
    }

    private static void handleResultClient(final ATMResultPayload pkt, final IPayloadContext ctx) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof com.saunhardy.createringtoncurrency.client.ATMScreen scr) {
                int color = switch (pkt.kind()) {
                    case 1 -> 0x2ECC71;
                    case 2 -> 0xE74C3C;
                    default -> 0xFFFFFF;
                };
                scr.showStatus(pkt.message(), color);
            } else if (mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(pkt.message()), false);
            }
        });
    }

    private static void sendResult(ServerPlayer player, int kind, String msg) {
        player.connection.send(new ClientboundCustomPayloadPacket(new ATMResultPayload(kind, msg)));
    }
}
