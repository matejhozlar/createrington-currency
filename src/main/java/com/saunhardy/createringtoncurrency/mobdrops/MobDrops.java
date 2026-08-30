package com.saunhardy.createringtoncurrency.mobdrops;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.enchantment.ModEnchantments;
import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class MobDrops {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> warnedToday = new HashSet<>();
    private static final int PRUNE_INTERVAL_TICKS = 24000;

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
        List<MobDropTable.Entry> drops = MobDropTable.entriesFor(dead.getType());
        if (drops.isEmpty()) return;

        UUID uuid = player.getUUID();
        LocalDate today = cachedToday;
        int earnedSoFar = earningsData.getEarned(uuid, today);

        if (earnedSoFar >= dailyLimit) {
            if (warnedToday.add(uuid)) {
                player.sendSystemMessage(message(dailyLimit));
            }
            return;
        }

        double bonus = MobDropTable.bonusFor(greedLevel(player));
        int earned = 0;
        for (MobDropTable.Entry drop : drops) {
            if (ThreadLocalRandom.current().nextDouble() * 100.0 < drop.chance() + bonus) {
                earned += drop.denomination();
            }
        }
        if (earned <= 0) return;

        int allowed = Math.min(earned, dailyLimit - earnedSoFar);
        earningsData.addEarnings(uuid, allowed, today);
        dropBills(dead, Bills.breakdown(allowed));

        if (earnedSoFar + allowed >= dailyLimit) {
            player.sendSystemMessage(message(dailyLimit));
            warnedToday.add(uuid);
        }
    }

    private static int greedLevel(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return 0;
        return player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(ModEnchantments.CAPITALIST_GREED)
                .map(stack::getEnchantmentLevel)
                .orElse(0);
    }

    private static void dropBills(LivingEntity dead, int[] counts) {
        for (int i = 0; i < counts.length; i++) {
            int remaining = counts[i];
            while (remaining > 0) {
                ItemStack stack = new ItemStack(Bills.itemFor(Bills.DENOMINATIONS[i]));
                int size = Math.min(remaining, stack.getMaxStackSize());
                stack.setCount(size);
                dead.spawnAtLocation(stack);
                remaining -= size;
            }
        }
    }

    private static Component message(int limit) {
        return Component.literal("⚠ You've reached today's mob farming limit ($" + limit + ").").withStyle(ChatFormatting.RED);
    }
}
