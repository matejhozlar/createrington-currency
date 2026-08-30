package com.saunhardy.createringtoncurrency.events;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.util.Bills;
import com.saunhardy.createringtoncurrency.util.Withdrawals;
import com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler;
import com.simibubi.create.content.logistics.tableCloth.ShoppingListItem;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StockTickerIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private static Method getStockTicker;
    private static Method bakeEntries;
    private static Method getSecond;
    private static Method getStacksByCount;
    private static Field stackField;
    private static Field countField;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        if (StockTickerInteractionHandler.getStockTickerPosition(event.getTarget()) != null && handle(event)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof BlazeBurnerBlock) || state.getValue(BlazeBurnerBlock.HEAT_LEVEL) != BlazeBurnerBlock.HeatLevel.NONE) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof BlazeBurnerBlockEntity burner) || !burner.stockKeeper) return;
        if (!hasStockTicker(event.getLevel(), event.getPos())) return;
        if (handle(event)) event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static boolean hasStockTicker(LevelAccessor level, BlockPos pos) {
        try {
            if (getStockTicker == null) {
                getStockTicker = BlazeBurnerBlockEntity.class.getMethod("getStockTicker", LevelAccessor.class, BlockPos.class);
            }
            return getStockTicker.invoke(null, level, pos) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Could not resolve the Stock Ticker of the burner at {}: {}", pos.toShortString(), e.toString());
            return false;
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
        if (!IN_FLIGHT.add(player.getUUID())) {
            player.displayClientMessage(Component.literal("Withdrawal in progress...").withStyle(ChatFormatting.YELLOW), true);
            return true;
        }

        Withdrawals.withdraw(player, missing, "stock_ticker", new Withdrawals.Reporter() {
            @Override
            public void succeeded(ServerPlayer recipient, long amount) {
                IN_FLIGHT.remove(recipient.getUUID());
                recipient.sendSystemMessage(Component.literal("💵 Withdrew $" + Bills.fmt(amount)
                        + " for your shopping list. Right-click again to pay.").withStyle(ChatFormatting.GREEN));
            }

            @Override
            public void failed(ServerPlayer recipient, String text) {
                IN_FLIGHT.remove(recipient.getUUID());
                recipient.sendSystemMessage(Component.literal("❌ Could not withdraw the bills for your shopping list: " + text)
                        .withStyle(ChatFormatting.RED));
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
            if (bakeEntries == null) bakeEntries = list.getClass().getMethod("bakeEntries", LevelAccessor.class, BlockPos.class);
            Object baked = bakeEntries.invoke(list, player.level(), null);
            if (baked == null) return null;
            if (getSecond == null) getSecond = baked.getClass().getMethod("getSecond");
            Object payment = getSecond.invoke(baked);
            if (getStacksByCount == null) getStacksByCount = payment.getClass().getMethod("getStacksByCount");
            List<?> stacks = (List<?>) getStacksByCount.invoke(payment);
            for (Object entry : stacks) {
                if (stackField == null) {
                    stackField = entry.getClass().getField("stack");
                    countField = entry.getClass().getField("count");
                }
                ItemStack stack = (ItemStack) stackField.get(entry);
                int count = countField.getInt(entry);
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
