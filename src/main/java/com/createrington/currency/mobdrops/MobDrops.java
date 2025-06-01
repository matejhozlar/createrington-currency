package com.createrington.currency.mobdrops;

import com.createrington.currency.CreateringtonCurrency;
import com.createrington.currency.Config;
import com.createrington.currency.MoneyCommands;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.world.entity.EntityType;

@EventBusSubscriber(modid = CreateringtonCurrency.MODID)
public class MobDrops {

    private static final Set<UUID> warnedToday = new HashSet<>();
    private static final Set<UUID> apiLimitAlreadySent = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> backendLimitReached = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, DailyEarnings> dailyEarnings = new ConcurrentHashMap<>();
    private static final int DAILY_LIMIT = 1000;
    private static final ExecutorService EXECUTOR = MoneyCommands.EXECUTOR;

    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        LivingEntity dead = event.getEntity();
        EntityType<?> type = dead.getType();
        UUID uuid = player.getUUID();
        LocalDate today = LocalDate.now();

        DailyEarnings progress = dailyEarnings.getOrDefault(uuid, new DailyEarnings(today, 0));
        if (!progress.date.equals(today)) {
            progress = new DailyEarnings(today, 0);
            warnedToday.remove(uuid);
            apiLimitAlreadySent.remove(uuid);
            backendLimitReached.remove(uuid);
        }

        // Async backend check once per day
        checkBackendLimitOnce(uuid, player);

        // If backend says limit reached, block entirely
        if (backendLimitReached.contains(uuid)) {
            if (!warnedToday.contains(uuid)) {
                player.sendSystemMessage(message());
                warnedToday.add(uuid);
            }
            dailyEarnings.put(uuid, progress);
            return;
        }

        int earned = 0;
        Item billToDrop = null;

        if (type == EntityType.ZOMBIE || type == EntityType.CREEPER || type == EntityType.SPIDER) {
            if (ThreadLocalRandom.current().nextDouble() < (Config.zomSpiCreDrop / 100.0)) {
                earned = 1;
                billToDrop = CreateringtonCurrency.BILL_1.get();
            }
        }

        if (type == EntityType.SKELETON) {
            if (ThreadLocalRandom.current().nextDouble() < (Config.skeletonDrop) / 100.0) {
                earned = 1;
                billToDrop = CreateringtonCurrency.BILL_1.get();
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.01) {
                earned = 5;
                billToDrop = CreateringtonCurrency.BILL_5.get();
            }
        }

        if (earned > 0) {
            if (progress.earnedToday + earned > DAILY_LIMIT) {
                int allowed = DAILY_LIMIT - progress.earnedToday;
                if (allowed > 0) {
                    progress.earnedToday += allowed;
                    dropBill(dead, billToDrop);
                } else {
                    if (!warnedToday.contains(uuid)) {
                        player.sendSystemMessage(message());
                        warnedToday.add(uuid);
                    }
                    if (!apiLimitAlreadySent.contains(uuid)) {
                        sendLimitReached(player);
                        apiLimitAlreadySent.add(uuid);
                    }
                }
                dailyEarnings.put(uuid, progress);
                return;
            }

            progress.earnedToday += earned;
            dailyEarnings.put(uuid, progress);
            dropBill(dead, billToDrop);
        }
    }

    private static void checkBackendLimitOnce(UUID uuid, ServerPlayer player) {
        if (backendLimitReached.contains(uuid)) return;

        EXECUTOR.submit(() -> {
            try {
                String token = MoneyCommands.getOrFetchToken(player);
                String apiUrl = MoneyCommands.safeJoin(Config.apiBaseUrl, Config.apiMobLimitUrl + "?uuid=" + uuid);
                URL url = URI.create(apiUrl).toURL();

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    boolean limitReached = obj.get("limitReached").getAsBoolean();
                    if (limitReached) {
                        backendLimitReached.add(uuid);
                    }
                }
            } catch (Exception ignored) {
                // fail silently
            }
        });
    }

    private static void dropBill(LivingEntity dead, Item bill) {
        if (bill != null) {
            ItemStack stack = new ItemStack(bill, 1);
            dead.spawnAtLocation(stack);
        }
    }

    private static class DailyEarnings {
        LocalDate date;
        int earnedToday;

        public DailyEarnings(LocalDate date, int earnedToday) {
            this.date = date;
            this.earnedToday = earnedToday;
        }
    }

    private static void sendLimitReached(ServerPlayer player) {
        EXECUTOR.submit(() -> {
            try {
                String token = MoneyCommands.getOrFetchToken(player);
                String apiUrl = MoneyCommands.safeJoin(Config.apiBaseUrl, Config.apiMobLimitUrl);
                URL url = URI.create(apiUrl).toURL();

                String json = "{}";

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.getOutputStream().write(json.getBytes());
                conn.getInputStream().close();
            } catch (Exception ignored) {
                // fail silently
            }
        });
    }

    private static Component message() {
        return Component.literal("⚠" + " " + "You've reached today's mob farming limit ($1000).").withStyle(ChatFormatting.RED);
    }
}
