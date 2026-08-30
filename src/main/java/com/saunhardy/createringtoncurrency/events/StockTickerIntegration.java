package com.saunhardy.createringtoncurrency.events;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.util.Bills;
import com.saunhardy.createringtoncurrency.util.Withdrawals;
import com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler;
import com.simibubi.create.content.logistics.tableCloth.ShoppingListItem;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StockTickerIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        if (StockTickerInteractionHandler.getStockTickerPosition(event.getTarget()) != null && handle(event)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (StockTickerInteractionHandler.getStockTickerPosition(event.getTarget()) != null && handle(event)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BlazeBurnerBlock && handle(event)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        IN_FLIGHT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        IN_FLIGHT.clear();
    }

    private static <T extends PlayerInteractEvent & ICancellableEvent> boolean handle(T event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return false;
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) return false;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ShoppingListItem) || !player.getOffhandItem().is(CreateringtonCurrency.BANK_CARD.get())) return false;

        int[] missing = missingBills(player, held);
        if (missing == null || Bills.isEmpty(missing)) return false;

        event.setCanceled(true);
        if (!IN_FLIGHT.add(player.getUUID())) return true;

        Withdrawals.withdraw(player, missing, "stock_ticker", new Withdrawals.Reporter() {
            @Override
            public void succeeded(ServerPlayer recipient, long amount) {
                IN_FLIGHT.remove(recipient.getUUID());
                recipient.displayClientMessage(Component.translatable("message.createringtoncurrency.stock_ticker.withdrawn", Bills.fmt(amount))
                        .withStyle(ChatFormatting.GREEN), true);
            }

            @Override
            public void failed(ServerPlayer recipient, String text) {
                IN_FLIGHT.remove(recipient.getUUID());
                recipient.displayClientMessage(Component.translatable("message.createringtoncurrency.stock_ticker.failed", text)
                        .withStyle(ChatFormatting.RED), true);
            }
        });
        return true;
    }

    @Nullable
    private static int[] missingBills(ServerPlayer player, ItemStack listStack) {
        var list = ShoppingListItem.getList(listStack);
        if (list == null) return null;

        int[] required = Bills.none();
        try {
            Method bakeEntries = list.getClass().getMethod("bakeEntries", LevelAccessor.class, BlockPos.class);
            Object baked = bakeEntries.invoke(list, player.level(), null);
            if (baked == null) return null;
            Object payment = baked.getClass().getMethod("getSecond").invoke(baked);
            List<?> stacks = (List<?>) payment.getClass().getMethod("getStacksByCount").invoke(payment);
            for (Object entry : stacks) {
                ItemStack stack = (ItemStack) entry.getClass().getField("stack").get(entry);
                int count = entry.getClass().getField("count").getInt(entry);
                int index = Bills.indexOf(stack);
                if (index >= 0) required[index] += count;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Could not read the payment of {}'s shopping list: {}", player.getName().getString(), e.toString());
            return null;
        }
        return Bills.missing(required, Bills.count(player.getInventory()));
    }
}
