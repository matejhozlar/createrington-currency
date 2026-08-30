package com.saunhardy.createringtoncurrency.network;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.saunhardy.createrington.api.currency.HistoryResponse;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
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

    private static final int KIND_INFO = 0;
    private static final int KIND_SUCCESS = 1;
    private static final int KIND_ERROR = 2;

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("2");

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
        Deposits.depositAll(player, "atm", new Deposits.Reporter() {
            @Override
            public void started(int amount) {
                sendResult(player, KIND_INFO, "Depositing $" + amount + "...");
            }

            @Override
            public void succeeded(int amount, String playerMessage) {
                sendResult(player, KIND_SUCCESS, "Deposited $" + amount);
            }

            @Override
            public void failed(String text) {
                sendResult(player, KIND_ERROR, text);
            }
        });
    }

    private static void handleWithdraw(final ATMWithdrawPayload pkt, final IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        Withdrawals.withdraw(player, pkt.toArray(), "atm", new Withdrawals.Reporter() {
            @Override
            public void succeeded(long amount) {
                sendResult(player, KIND_SUCCESS, "Withdrew $" + amount);
            }

            @Override
            public void failed(String text) {
                sendResult(player, KIND_ERROR, text);
            }
        });
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
                    case KIND_SUCCESS -> 0x2ECC71;
                    case KIND_ERROR -> 0xE74C3C;
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
