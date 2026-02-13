package com.saunhardy.createringtoncurrency.mobdrops;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.enchantment.ModEnchantments;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.entity.EntityType;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class MobDrops {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> warnedToday = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, DailyEarnings> dailyEarnings = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type EARNINGS_MAP_TYPE = new TypeToken<Map<String, DailyEarningsData>>() {}.getType();
    private static final AtomicBoolean dirty = new AtomicBoolean(false);
    private static final int SAVE_INTERVAL_TICKS = 6000; // 5 minutes
    private static int tickCounter = 0;
    private static Path dataFile;
    private static final Set<EntityType<?>> ALLOWED_MOB_TYPES = Set.of(
            EntityType.ZOMBIE,
            EntityType.CREEPER,
            EntityType.SPIDER,
            EntityType.SKELETON,
            EntityType.WITHER_SKELETON,
            EntityType.BLAZE
    );

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Path worldDir = event.getServer().getWorldPath(LevelResource.ROOT);
        dataFile = worldDir.resolve("createringtoncurrency_mob_earnings.json");
        loadDailyEarnings();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (dirty.compareAndSet(true, false)) {
            saveDailyEarnings();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter >= SAVE_INTERVAL_TICKS) {
            tickCounter = 0;
            if (dirty.compareAndSet(true, false)) {
                saveDailyEarnings();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        UUID uuid = player.getUUID();
        warnedToday.remove(uuid);
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

        int dailyLimit = Config.MOB_DAILY_LIMIT.get();
        if (dailyLimit <= 0) return; // limit disabled

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

        DailyEarnings progress = dailyEarnings.computeIfAbsent(uuid, u -> new DailyEarnings(LocalDate.now(), 0));
        if (!progress.date.equals(LocalDate.now())) {
            progress.date = LocalDate.now();
            progress.earnedToday = 0;
            warnedToday.remove(uuid);
        }

        if (progress.earnedToday >= dailyLimit) {
            if (!warnedToday.contains(uuid)) {
                player.sendSystemMessage(message(dailyLimit));
                warnedToday.add(uuid);
            }
            return;
        }

        int earned = 0;
        Item billToDrop = null;

        double baseChance = 0.0;

        if (type == EntityType.ZOMBIE || type == EntityType.CREEPER || type == EntityType.SPIDER) {
            baseChance = Config.ZOM_SPI_CRE_DROP.get();
        } else if (type == EntityType.SKELETON) {
            baseChance = Config.SKELETON_DROP.get();
        } else if (type == EntityType.WITHER_SKELETON) {
            baseChance = Config.WITHER_SKELETON_DROP.get();
        } else if (type == EntityType.BLAZE) {
            baseChance = Config.BLAZE_DROP.get();
        }

        int effectiveLevel = Math.min(enchantmentLevel, 3);
        switch (effectiveLevel) {
            case 1 -> baseChance += 5.0;
            case 2 -> baseChance += 8.0;
            case 3 -> baseChance += 10.0;
        }

        if(ThreadLocalRandom.current().nextDouble() < (baseChance / 100.0)){
            earned = 1;
            billToDrop = CreateringtonCurrency.BILL_1.get();
        }

        final boolean isFiveDollarMob =
                type == EntityType.SKELETON ||
                        type == EntityType.WITHER_SKELETON ||
                        type == EntityType.BLAZE;

        if (isFiveDollarMob && ThreadLocalRandom.current().nextDouble() < 0.02) {
            earned = 5;
            billToDrop = CreateringtonCurrency.BILL_5.get();
        }

        if (earned > 0) {
            if (progress.earnedToday + earned > dailyLimit) {
                int allowed = dailyLimit - progress.earnedToday;
                if (allowed > 0) {
                    progress.earnedToday += allowed;
                    dropBill(dead, billToDrop);
                    dirty.set(true);
                } else {
                    if (!warnedToday.contains(uuid)) {
                        player.sendSystemMessage(message(dailyLimit));
                        warnedToday.add(uuid);
                    }
                }
                return;
            }

            progress.earnedToday += earned;
            dropBill(dead, billToDrop);

            if (progress.earnedToday >= dailyLimit) {
                player.sendSystemMessage(message(dailyLimit));
                warnedToday.add(uuid);
            }

            dirty.set(true);
        }
    }

    private static void loadDailyEarnings() {
        dailyEarnings.clear();
        if (dataFile == null || !Files.exists(dataFile)) return;

        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Map<String, DailyEarningsData> raw = GSON.fromJson(reader, EARNINGS_MAP_TYPE);
            if (raw == null) return;

            LocalDate today = LocalDate.now();
            for (Map.Entry<String, DailyEarningsData> entry : raw.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    DailyEarningsData data = entry.getValue();
                    LocalDate date = LocalDate.parse(data.date);
                    if (date.equals(today)) {
                        dailyEarnings.put(uuid, new DailyEarnings(date, data.earnedToday));
                    }
                } catch (Exception e) {
                    LOGGER.warn("Skipping invalid mob earnings entry '{}': {}", entry.getKey(), e.getMessage());
                }
            }
            LOGGER.info("Loaded mob daily earnings for {} players", dailyEarnings.size());
        } catch (IOException e) {
            LOGGER.warn("Failed to load mob daily earnings: {}", e.getMessage());
        }
    }

    private static void saveDailyEarnings() {
        if (dataFile == null) return;

        Map<String, DailyEarningsData> raw = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (Map.Entry<UUID, DailyEarnings> entry : dailyEarnings.entrySet()) {
            DailyEarnings de = entry.getValue();
            if (de.date.equals(today)) {
                raw.put(entry.getKey().toString(), new DailyEarningsData(de.date.toString(), de.earnedToday));
            }
        }

        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(raw, EARNINGS_MAP_TYPE, writer);
        } catch (IOException e) {
            LOGGER.warn("Failed to save mob daily earnings: {}", e.getMessage());
        }
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

    private static class DailyEarningsData {
        String date;
        int earnedToday;

        public DailyEarningsData(String date, int earnedToday) {
            this.date = date;
            this.earnedToday = earnedToday;
        }
    }

    private static Component message(int limit) {
        return Component.literal("\u26A0 You've reached today's mob farming limit ($" + limit + ").").withStyle(ChatFormatting.RED);
    }
}
