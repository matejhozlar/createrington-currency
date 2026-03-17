package com.saunhardy.createringtoncurrency.mobdrops;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.enchantment.ModEnchantments;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MobDrops {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> warnedToday = new HashSet<>();
    private static final int PRUNE_INTERVAL_TICKS = 24000; // 20 minutes
    private static final Set<EntityType<?>> ALLOWED_MOB_TYPES = Set.of(
            EntityType.ZOMBIE,
            EntityType.CREEPER,
            EntityType.SPIDER,
            EntityType.SKELETON,
            EntityType.WITHER_SKELETON,
            EntityType.BLAZE
    );

    private static MobEarningsData earningsData;
    private static LocalDate cachedToday = LocalDate.now();
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        earningsData = server.overworld().getDataStorage()
                .computeIfAbsent(MobEarningsData.factory(), MobEarningsData.dataName());
        cachedToday = LocalDate.now();
        warnedToday.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        earningsData = null;
        warnedToday.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        LocalDate now = LocalDate.now();
        if (!now.equals(cachedToday)) {
            cachedToday = now;
            warnedToday.clear();
            if (earningsData != null) {
                earningsData.pruneStaleEntries(now);
            }
        }

        if (++tickCounter >= PRUNE_INTERVAL_TICKS) {
            tickCounter = 0;
            if (earningsData != null) {
                earningsData.pruneStaleEntries(cachedToday);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        warnedToday.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        warnedToday.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (player instanceof FakePlayer) return;
        if (player.isSpectator()) return;
        if (earningsData == null) return;

        int dailyLimit = Config.MOB_DAILY_LIMIT.get();
        if (dailyLimit <= 0) return;

        LivingEntity dead = event.getEntity();
        EntityType<?> type = dead.getType();
        if (!ALLOWED_MOB_TYPES.contains(type)) return;

        ItemStack stack = player.getMainHandItem();
        int enchantmentLevel = 0;
        if (!stack.isEmpty()) {
            var registryAccess = player.level().registryAccess();
            var enchantmentRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
            var lightningStrikerHolder = enchantmentRegistry.getHolderOrThrow(ModEnchantments.CAPITALIST_GREED);
            enchantmentLevel = stack.getEnchantmentLevel(lightningStrikerHolder);
        }

        UUID uuid = player.getUUID();
        LocalDate today = cachedToday;
        int earnedSoFar = earningsData.getEarned(uuid, today);

        if (earnedSoFar >= dailyLimit) {
            if (warnedToday.add(uuid)) {
                player.sendSystemMessage(message(dailyLimit));
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

        if (ThreadLocalRandom.current().nextDouble() < (baseChance / 100.0)) {
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
            int allowed = Math.min(earned, dailyLimit - earnedSoFar);
            if (allowed > 0) {
                earningsData.addEarnings(uuid, allowed, today);
                dropBill(dead, billToDrop);

                if (earnedSoFar + allowed >= dailyLimit) {
                    player.sendSystemMessage(message(dailyLimit));
                    warnedToday.add(uuid);
                }
            }
        }
    }

    private static void dropBill(LivingEntity dead, Item bill) {
        if (bill != null) {
            dead.spawnAtLocation(new ItemStack(bill, 1));
        }
    }

    private static Component message(int limit) {
        return Component.literal("\u26A0 You've reached today's mob farming limit ($" + limit + ").").withStyle(ChatFormatting.RED);
    }
}
