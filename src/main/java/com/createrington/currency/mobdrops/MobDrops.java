package com.createrington.currency.mobdrops;

import com.createrington.currency.CreateringtonCurrency;
import com.createrington.currency.Config;
import com.createrington.currency.MoneyCommands;
import com.createrington.currency.enchantment.ModEnchantments;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.entity.EntityType;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;

import static com.mojang.text2speech.Narrator.LOGGER;

public class MobDrops {
    private static final Set<UUID> warnedToday = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> backendLimitReached = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, DailyEarnings> dailyEarnings = new ConcurrentHashMap<>();
    private static final int DAILY_LIMIT = 1000;
    private static final ExecutorService EXECUTOR = MoneyCommands.EXECUTOR;
    private static final Set<EntityType<?>> ALLOWED_MOB_TYPES = Set.of(
            EntityType.ZOMBIE,
            EntityType.CREEPER,
            EntityType.SPIDER,
            EntityType.SKELETON
    );

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {

        ServerPlayer player = (ServerPlayer) event.getEntity();
        UUID uuid = player.getUUID();

        warnedToday.remove(uuid);
        backendLimitReached.remove(uuid);

        checkBackendLimitOnce(uuid, player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        UUID uuid = player.getUUID();

        warnedToday.remove(uuid);
    }

    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (player instanceof FakePlayer) return;
        if (player.isSpectator()) return;

        ItemStack stack = player.getMainHandItem();
        int enchantmentLevel = 0;
        if(!stack.isEmpty()) {
            var registryAccess = player.level().registryAccess();
            var enchantmentRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
            var lightningStrikerHolder = enchantmentRegistry.getHolderOrThrow(ModEnchantments.CAPITALIST_GREED);
            enchantmentLevel = stack.getEnchantmentLevel(lightningStrikerHolder);
        }

        LivingEntity dead = event.getEntity();
        EntityType<?> type = dead.getType();
        if (!ALLOWED_MOB_TYPES.contains(type)) return;
        UUID uuid = player.getUUID();

        // First, local check: is limit reached?
        if (backendLimitReached.contains(uuid)) {
            if (!warnedToday.contains(uuid)) {
                player.sendSystemMessage(message());
                warnedToday.add(uuid);
            }
            return;
        }

        DailyEarnings progress = dailyEarnings.computeIfAbsent(uuid, u -> new DailyEarnings(LocalDate.now(), 0));
        if (!progress.date.equals(LocalDate.now())) {
            progress.date = LocalDate.now();
            progress.earnedToday = 0;
        }

        int earned = 0;
        Item billToDrop = null;

        double baseChance = 0.0;

        if (type == EntityType.ZOMBIE || type == EntityType.CREEPER || type == EntityType.SPIDER) {
            baseChance = Config.ZOM_SPI_CRE_DROP.get();
        }

        if (type == EntityType.SKELETON) {
            baseChance = Config.SKELETON_DROP.get();
        }

        switch (enchantmentLevel) {
            case 1 -> baseChance += 5.0;
            case 2 -> baseChance += 8.0;
            case 3 -> baseChance += 10.0;
        }

        if(ThreadLocalRandom.current().nextDouble() < (baseChance / 100.0)){
            earned = 1;
            billToDrop = CreateringtonCurrency.BILL_1.get();
        }

        if (type == EntityType.SKELETON && ThreadLocalRandom.current().nextDouble() < 0.01){
            earned = 5;
            billToDrop = CreateringtonCurrency.BILL_5.get();
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
                    sendLimitReached(player);
                    backendLimitReached.add(uuid);
                }
                return;
            }

            progress.earnedToday += earned;
            dropBill(dead, billToDrop);

            // After reaching limit exactly:
            if (progress.earnedToday >= DAILY_LIMIT) {
                sendLimitReached(player);
                backendLimitReached.add(uuid);
            }
        }
    }

    private static void checkBackendLimitOnce(UUID uuid, ServerPlayer player) {
        if (backendLimitReached.contains(uuid)) return;

        EXECUTOR.submit(() -> {
            try {
                String token = MoneyCommands.getOrFetchToken(player);
                String apiUrl = MoneyCommands.safeJoin(Config.API_BASE_URL.get(), Config.API_MOB_LIMIT_URL.get() + "?uuid=" + uuid);
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
            } catch (Exception e) {
                LOGGER.warn("Backend limit check failed for {}: {}", uuid, e.getMessage());
            }
        });
    }

    private static void sendLimitReached(ServerPlayer player) {
        UUID uuid = player.getUUID();
        EXECUTOR.submit(() -> {
            try {
                String token = MoneyCommands.getOrFetchToken(player);
                String apiUrl = MoneyCommands.safeJoin(Config.API_BASE_URL.get(), Config.API_MOB_LIMIT_URL.get());
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

                LOGGER.info("Sent mob limit reached update for {}", uuid);
            } catch (Exception e) {
                LOGGER.warn("Failed to send limit reached for {}: {}", uuid, e.getMessage());
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

    private static Component message() {
        return Component.literal("⚠ You've reached today's mob farming limit ($1000).").withStyle(ChatFormatting.RED);
    }
}
