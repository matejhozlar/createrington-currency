package com.saunhardy.createringtoncurrency.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.MoneyCommands;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;

public final class ATMNetworking {
    private static final Gson GSON = new Gson();

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");

        reg.playToServer(ATMDepositPayload.TYPE, ATMDepositPayload.STREAM_CODEC, ATMNetworking::handleDeposit);

        reg.playToServer(ATMWithdrawPayload.TYPE, ATMWithdrawPayload.STREAM_CODEC, ATMNetworking::handleWithdraw);

        reg.playToClient(ATMResultPayload.TYPE, ATMResultPayload.STREAM_CODEC, ATMNetworking::handleResultClient);

        reg.playToServer(ATMQueryBalancePayload.TYPE, ATMQueryBalancePayload.STREAM_CODEC, ATMNetworking::handleQueryBalance);

        reg.playToClient(ATMBalancePayload.TYPE, ATMBalancePayload.STREAM_CODEC, ATMNetworking::handleBalanceClient);
    }

    private static void handleQueryBalance(final ATMQueryBalancePayload pkt, final net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        var p = ctx.player();
        if (!(p instanceof net.minecraft.server.level.ServerPlayer player)) return;

        com.saunhardy.createringtoncurrency.MoneyCommands.EXECUTOR.submit(() -> {
            try {
                var base = com.saunhardy.createringtoncurrency.Config.API_BASE_URL.get();
                var path = com.saunhardy.createringtoncurrency.Config.API_BALANCE_URL.get();
                var url  = java.net.URI.create(com.saunhardy.createringtoncurrency.MoneyCommands.safeJoin(base, path) + "?uuid=" + player.getUUID()).toURL();

                String token = com.saunhardy.createringtoncurrency.MoneyCommands.getOrFetchToken(player);

                var conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                java.io.InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
                var sb = new StringBuilder();
                if (is != null) {
                    var br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                    String line; while ((line = br.readLine()) != null) sb.append(line); br.close();
                }

                int balance = -1;
                try {
                    JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                    if (json.has("balance")) balance = json.get("balance").getAsInt();
                } catch (Exception ignored) {}
                player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(
                        new ATMBalancePayload(Math.max(0, balance))
                ));
            } catch (Exception e) {
                player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(
                        new ATMBalancePayload(0)
                ));
            }
        });
    }

    private static void handleBalanceClient(final ATMBalancePayload pkt, final net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof com.saunhardy.createringtoncurrency.client.ATMScreen scr) {
                scr.updateBalance(pkt.balance());
            }
        });
    }


    private static void handleResultClient(final ATMResultPayload pkt, final net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof com.saunhardy.createringtoncurrency.client.ATMScreen scr) {
                int color = switch (pkt.kind()) { case 1 -> 0x2ECC71; case 2 -> 0xE74C3C; default -> 0xFFFFFF; };
                scr.showStatus(pkt.message(), color);
            } else if (mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(pkt.message()), false);
            }
        });
    }

    private static void sendResult(ServerPlayer player, int kind, String msg) {
        player.connection.send(new ClientboundCustomPayloadPacket(new ATMResultPayload(kind, msg)));
    }

    private static void handleDeposit(final ATMDepositPayload pkt, final IPayloadContext ctx) {
        var p = ctx.player();
        if (!(p instanceof ServerPlayer player)) return;

        Map<Object, Integer> values = Map.of(
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
            var st = player.getInventory().getItem(i);
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

        MoneyCommands.EXECUTOR.submit(() -> {
            try {
                String token = MoneyCommands.getOrFetchToken(player);
                URL url = URI.create(MoneyCommands.safeJoin(
                        Config.API_BASE_URL.get(), Config.API_DEPOSIT_URL.get()
                )).toURL();

                var payload = Map.of(
                        "uuid", player.getUUID().toString(),
                        "amount", totalAmount
                );
                PostResult result = post(url, token, payload);

                if (result.code == 200) {
                    player.server.execute(() -> {
                        for (var entry : slotsByDenom.entrySet()) {
                            for (int slot : entry.getValue()) {
                                player.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
                            }
                        }
                        player.inventoryMenu.broadcastChanges();
                        player.closeContainer();
                    });
                    sendResult(player, 1, "Deposited $" + totalAmount);
                } else {
                    sendResult(player, 2, extractApiMessage(result.body, "Deposit failed. Please try again."));
                }
            } catch (Exception e) {
                sendResult(player, 2, "Something went wrong. Please try again.");
            }
        });
    }


    private static void handleWithdraw(final ATMWithdrawPayload pkt, final IPayloadContext ctx) {
        var p = ctx.player();
        if (!(p instanceof ServerPlayer player)) return;

        java.util.function.BiConsumer<Integer,Integer> give = (denom, count) -> {
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
            var stack = new net.minecraft.world.item.ItemStack(item, count);
            player.server.execute(() -> player.getInventory().placeItemBackInInventory(stack));
        };

        MoneyCommands.EXECUTOR.submit(() -> {
            try {
                String token = MoneyCommands.getOrFetchToken(player);
                URL url = URI.create(MoneyCommands.safeJoin(
                        Config.API_BASE_URL.get(), Config.API_WITHDRAW_URL.get()
                )).toURL();

                if (pkt.mode() == 0) {
                    var payload = Map.of(
                            "uuid", player.getUUID().toString(),
                            "denomination", pkt.a(),
                            "count", pkt.b()
                    );
                    PostResult result = post(url, token, payload);
                    if (result.code == 200) {
                        give.accept(pkt.a(), pkt.b());
                        sendResult(player, 1, "Withdrew $" + (pkt.a() * pkt.b()));
                    } else {
                        sendResult(player, 2, extractApiMessage(result.body, "Withdraw failed. Please try again."));
                    }
                } else {
                    int total = pkt.a();
                    int[] denoms = {1000, 500, 100, 50, 20, 10, 5, 1};
                    Map<Integer,Integer> bundle = new LinkedHashMap<>();
                    for (int d : denoms) {
                        int cnt = total / d;
                        if (cnt > 0) { bundle.put(d, cnt); total -= d * cnt; }
                    }
                    if (total != 0) {
                        sendResult(player, 2, "Cannot make exact change.");
                        return;
                    }

                    boolean ok = true;
                    for (var e : bundle.entrySet()) {
                        var payload = Map.of(
                                "uuid", player.getUUID().toString(),
                                "denomination", e.getKey(),
                                "count", e.getValue()
                        );
                        PostResult result = post(url, token, payload);
                        if (result.code == 200) {
                            give.accept(e.getKey(), e.getValue());
                        } else {
                            ok = false;
                            sendResult(player, 2, extractApiMessage(result.body, "Withdraw failed. Please try again."));
                        }
                    }
                    if (ok) {
                        sendResult(player, 1, "Withdrawal complete");
                    }
                }
            } catch (Exception e) {
                sendResult(player, 2, "Something went wrong. Please try again.");
            }
        });
    }


    private record PostResult(int code, String body) {}

    private static PostResult post(URL url, String bearer, Object payload) throws Exception {
        var conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + bearer);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(new Gson().toJson(payload).getBytes());
        int code = conn.getResponseCode();
        var is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            return new PostResult(code, "");
        }
        var br = new BufferedReader(new InputStreamReader(is));
        var sb = new StringBuilder();
        String line; while ((line = br.readLine()) != null) sb.append(line); br.close();
        return new PostResult(code, sb.toString());
    }

    private static String extractApiMessage(String body, String fallback) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("message")) return json.get("message").getAsString();
            if (json.has("error")) return json.get("error").getAsString();
        } catch (Exception ignored) {}
        return fallback;
    }
}
