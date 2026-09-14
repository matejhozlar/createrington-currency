package com.saunhardy.createringtoncurrency.network;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.saunhardy.createrington.api.currency.HistoryResponse;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
import com.saunhardy.createringtoncurrency.util.Bills;
import com.saunhardy.createringtoncurrency.util.Deposits;
import com.saunhardy.createringtoncurrency.util.Withdrawals;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

public final class ATMNetworking {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    private static final int KIND_INFO = ATMResultPayload.KIND_INFO;
    private static final int KIND_SUCCESS = ATMResultPayload.KIND_SUCCESS;
    private static final int KIND_ERROR = ATMResultPayload.KIND_ERROR;

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("4");

        reg.playToClient(ATMOpenPayload.TYPE, ATMOpenPayload.STREAM_CODEC, ATMNetworking::handleOpenClient);
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
        if (!CurrencyApi.isAvailable()) {
            player.connection.send(new ClientboundCustomPayloadPacket(ATMBalancePayload.unavailable()));
            return;
        }
        CurrencyApi.balance(player.getUUID())
                .thenAccept(resp -> {
                    if (!resp.isSuccess() || resp.getData() == null) {
                        LOGGER.warn("ATM balance query rejected for {}: {}", player.getName().getString(), resp.getMessage());
                        player.connection.send(new ClientboundCustomPayloadPacket(ATMBalancePayload.unavailable()));
                        return;
                    }
                    int balance = Math.max(0, (int) resp.getData().balance());
                    player.connection.send(new ClientboundCustomPayloadPacket(new ATMBalancePayload(balance, true)));
                })
                .exceptionally(ex -> {
                    LOGGER.error("ATM balance query failed for {}: {}", player.getName().getString(), ex.getMessage());
                    player.connection.send(new ClientboundCustomPayloadPacket(ATMBalancePayload.unavailable()));
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
        Deposits.Reporter reporter = new Deposits.Reporter() {
            @Override
            public void started(ServerPlayer recipient, long amount) {
                sendResult(recipient, KIND_INFO, "Depositing $" + Bills.fmt(amount) + "...");
            }

            @Override
            public void succeeded(ServerPlayer recipient, long amount, String playerMessage) {
                sendResult(recipient, KIND_SUCCESS, playerMessage != null ? playerMessage : "Deposited $" + Bills.fmt(amount));
            }

            @Override
            public void failed(ServerPlayer recipient, String text) {
                sendResult(recipient, KIND_ERROR, text);
            }
        };
        if (pkt.isAll()) {
            Deposits.depositAll(player, "atm", reporter);
        } else {
            Deposits.deposit(player, pkt.amount(), "atm", reporter);
        }
    }

    private static void handleWithdraw(final ATMWithdrawPayload pkt, final IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        Withdrawals.withdraw(player, pkt.toArray(), "atm", new Withdrawals.Reporter() {
            @Override
            public void succeeded(ServerPlayer recipient, long amount) {
                sendResult(recipient, KIND_SUCCESS, "Withdrew $" + Bills.fmt(amount));
            }

            @Override
            public void failed(ServerPlayer recipient, String text) {
                sendResult(recipient, KIND_ERROR, text);
            }
        });
    }

    // ---- Client-side handlers ---------------------------------------------

    private static void handleOpenClient(final ATMOpenPayload pkt, final IPayloadContext ctx) {
        com.saunhardy.createringtoncurrency.client.ATMScreen.open();
    }

    private static void handleBalanceClient(final ATMBalancePayload pkt, final IPayloadContext ctx) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof com.saunhardy.createringtoncurrency.client.ATMScreen scr) {
                scr.updateBalance(pkt.balance(), pkt.available());
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
                scr.showResult(pkt.kind(), pkt.message());
            } else if (mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(pkt.message()), false);
            }
        });
    }

    private static void sendResult(ServerPlayer player, int kind, String msg) {
        player.connection.send(new ClientboundCustomPayloadPacket(new ATMResultPayload(kind, msg)));
    }
}
