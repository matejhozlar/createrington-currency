package com.saunhardy.createringtoncurrency.network;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
import com.saunhardy.createringtoncurrency.block.DepositorTerminalBlock;
import com.saunhardy.createringtoncurrency.block.DepositorTerminalBlockEntity;
import com.saunhardy.createringtoncurrency.util.Bills;
import com.saunhardy.crnet.http.ApiResponse;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.text.NumberFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DepositorNetworking {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int KIND_INFO = 0;
    public static final int KIND_SUCCESS = 1;
    public static final int KIND_ERROR = 2;

    /** A price higher than this could never be paid: the bills would not fit even into an empty terminal. */
    public static final int MAX_PRICE_COUNT = DepositorTerminalBlockEntity.MAX_BILLS;

    private static final long PAY_COOLDOWN_MS = 750;

    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_PAYMENT = new ConcurrentHashMap<>();

    private DepositorNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");
        reg.playToServer(DepositorSetPricePayload.TYPE, DepositorSetPricePayload.STREAM_CODEC, DepositorNetworking::handleSetPrice);
        reg.playToServer(DepositorTakeAllPayload.TYPE, DepositorTakeAllPayload.STREAM_CODEC, DepositorNetworking::handleTakeAll);
        reg.playToClient(DepositorResultPayload.TYPE, DepositorResultPayload.STREAM_CODEC, DepositorNetworking::handleResultClient);
    }

    /** Game-bus listener: forget per-player payment state so the maps don't grow for the lifetime of the process. */
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        IN_FLIGHT.remove(id);
        LAST_PAYMENT.remove(id);
    }

    public static boolean hasBankCard(Container inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(CreateringtonCurrency.BANK_CARD.get())) return true;
        }
        return false;
    }

    public static String describe(int denomination, int count) {
        return count + " × $" + fmt(denomination) + " ($" + fmt(denomination * count) + ")";
    }

    private static void handleSetPrice(final DepositorSetPricePayload pkt, final IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        DepositorTerminalBlockEntity be = terminalNear(player, pkt.pos());
        if (be == null) {
            sendResult(player, KIND_ERROR, "The terminal is out of reach.");
            return;
        }

        if (!be.canConfigure(player)) {
            sendResult(player, KIND_ERROR, "Only the owner can change the price.");
            return;
        }
        if (Bills.indexOfDenomination(pkt.denomination()) < 0 || pkt.count() < 0) {
            sendResult(player, KIND_ERROR, "Invalid price.");
            return;
        }
        if (pkt.count() > MAX_PRICE_COUNT) {
            sendResult(player, KIND_ERROR, "A terminal holds at most " + MAX_PRICE_COUNT + " bills.");
            return;
        }
        int max = Config.DEPOSITOR_MAX_PRICE.get();
        if ((long) pkt.denomination() * pkt.count() > max) {
            sendResult(player, KIND_ERROR, "The price can't exceed $" + fmt(max) + ".");
            return;
        }

        be.setPrice(pkt.denomination(), pkt.count());
        if (pkt.count() == 0) {
            sendResult(player, KIND_SUCCESS, "Price cleared.");
        } else {
            sendResult(player, KIND_SUCCESS, "Price set to " + describe(pkt.denomination(), pkt.count()));
        }
        LOGGER.info("[DEPOSITOR] {} ({}) set the price of the terminal at {} to {} x ${}",
                player.getName().getString(), player.getUUID(), pkt.pos().toShortString(), pkt.count(), pkt.denomination());
    }

    private static void handleTakeAll(final DepositorTakeAllPayload pkt, final IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        DepositorTerminalBlockEntity be = terminalNear(player, pkt.pos());
        if (be == null) {
            sendResult(player, KIND_ERROR, "The terminal is out of reach.");
            return;
        }

        if (!be.canConfigure(player)) {
            sendResult(player, KIND_ERROR, "Only the owner can take bills out.");
            return;
        }

        ItemStackHandler storage = be.getStorage();
        int taken = 0;
        boolean leftBehind = false;
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.getStackInSlot(slot);
            int denomIndex = Bills.indexOf(stack);
            if (denomIndex < 0) continue;
            ItemStack moving = stack.copy();
            int before = moving.getCount();
            player.getInventory().add(moving);
            taken += (before - moving.getCount()) * Bills.DENOMINATIONS[denomIndex];
            storage.setStackInSlot(slot, moving.isEmpty() ? ItemStack.EMPTY : moving);
            if (!moving.isEmpty()) leftBehind = true;
        }

        if (taken == 0) {
            if (leftBehind) sendResult(player, KIND_ERROR, "Your inventory is full.");
            else sendResult(player, KIND_INFO, "The storage is empty.");
            return;
        }
        sendResult(player, KIND_SUCCESS, "Took $" + fmt(taken) + (leftBehind ? " — inventory full, the rest stayed inside." : ""));
    }

    public static void hint(ServerPlayer player, DepositorTerminalBlockEntity be) {
        if (be.getOwner() == null || be.getOwner().equals(player.getUUID())) return;
        if (!be.hasPrice()) {
            actionBar(player, KIND_INFO, "This terminal isn't set up yet.");
            return;
        }
        actionBar(player, KIND_INFO,
                "Hold " + describe(be.getPriceDenomination(), be.getPriceCount()) + " or a Bank Card, then right-click to pay.");
    }

    public static void pay(ServerPlayer player, DepositorTerminalBlockEntity be, boolean card) {
        UUID owner = be.getOwner();
        if (owner == null) {
            actionBar(player, KIND_ERROR, "This terminal has no owner.");
            return;
        }
        if (!be.hasPrice()) {
            actionBar(player, KIND_ERROR, "This terminal has no price set.");
            return;
        }
        if (owner.equals(player.getUUID())) {
            actionBar(player, KIND_ERROR, "You own this terminal.");
            return;
        }

        // The cooldown only starts on a successful payment (see completePayment), so a failed attempt can be retried
        // right away; its job is to swallow the repeat clicks of a held right mouse button after a payment went through.
        Long last = LAST_PAYMENT.get(player.getUUID());
        if (last != null && System.currentTimeMillis() - last < PAY_COOLDOWN_MS) return;
        if (IN_FLIGHT.contains(player.getUUID())) return;

        int denomIndex = Bills.indexOfDenomination(be.getPriceDenomination());
        int count = be.getPriceCount();
        if (!Bills.fits(be.getStorage(), Bills.only(denomIndex, count))) {
            actionBar(player, KIND_ERROR, "The terminal's storage is full.");
            return;
        }

        if (card) payByCard(player, be, owner, denomIndex, count);
        else payInCash(player, be, owner, denomIndex, count);
    }

    private static void payInCash(ServerPlayer player, DepositorTerminalBlockEntity be, UUID owner, int denomIndex, int count) {
        int denomination = Bills.DENOMINATIONS[denomIndex];
        int[] available = Bills.count(player.getInventory());
        if (available[denomIndex] < count) {
            actionBar(player, KIND_ERROR, "You need " + count + " × $" + fmt(denomination) + " bills but only have " + available[denomIndex] + ".");
            return;
        }

        int[] payment = Bills.only(denomIndex, count);
        Bills.extract(player.getInventory(), payment);
        Bills.insert(be.getStorage(), payment);
        player.inventoryMenu.sendAllDataToRemote();

        completePayment(player, be, owner, "cash", "Paid $" + fmt(denomination * count) + " in cash");
    }

    private static void payByCard(ServerPlayer player, DepositorTerminalBlockEntity be, UUID owner, int denomIndex, int count) {
        if (!hasBankCard(player.getInventory())) {
            actionBar(player, KIND_ERROR, "You need a Bank Card in your inventory to pay by card.");
            return;
        }

        IN_FLIGHT.add(player.getUUID());
        final BlockPos pos = be.getBlockPos();
        final ServerLevel level = player.serverLevel();
        final int denomination = Bills.DENOMINATIONS[denomIndex];

        CurrencyApi.withdraw(player.getUUID(), denomination, count).whenComplete((resp, ex) -> {
            IN_FLIGHT.remove(player.getUUID());
            if (ex != null) {
                LOGGER.error("Depositor card payment failed for {}: {}", player.getName().getString(), ex.getMessage());
                actionBar(player, KIND_ERROR, "Something went wrong. Please try again.");
                return;
            }
            if (!resp.isSuccess()) {
                actionBar(player, KIND_ERROR, errorText(resp, "Card payment failed. Please try again."));
                return;
            }
            player.server.execute(() -> finishCardPayment(player, level, pos, owner, denomIndex, count));
        });
    }

    private static void finishCardPayment(ServerPlayer player, ServerLevel level, BlockPos pos, UUID owner, int denomIndex, int count) {
        int[] bills = Bills.only(denomIndex, count);
        DepositorTerminalBlockEntity be = level.getBlockEntity(pos) instanceof DepositorTerminalBlockEntity found ? found : null;
        if (be == null || !Bills.fits(be.getStorage(), bills)) {
            Bills.give(player, bills);
            player.inventoryMenu.sendAllDataToRemote();
            actionBar(player, KIND_ERROR, be == null
                    ? "The terminal is gone — the withdrawn bills were handed to you."
                    : "The terminal filled up — the withdrawn bills were handed to you.");
            LOGGER.warn("[DEPOSITOR] Terminal at {} {} before the card payment from {} completed; bills handed to the player",
                    pos.toShortString(), be == null ? "was removed" : "filled up", player.getName().getString());
            return;
        }

        Bills.insert(be.getStorage(), bills);
        completePayment(player, be, owner, "card", "Paid $" + fmt(Bills.DENOMINATIONS[denomIndex] * count) + " by card");
    }

    private static void completePayment(ServerPlayer payer, DepositorTerminalBlockEntity be, UUID owner, String how, String payerMessage) {
        LAST_PAYMENT.put(payer.getUUID(), System.currentTimeMillis());
        actionBar(payer, KIND_SUCCESS, payerMessage);

        // The terminal pulses in its own level; the payer may have changed dimension while a card payment was in flight.
        BlockPos pos = be.getBlockPos();
        be.pulse();

        String priced = describe(be.getPriceDenomination(), be.getPriceCount());
        LOGGER.info("[DEPOSITOR] {} ({}) paid {} by {} at {} (owner {} / {})",
                payer.getName().getString(), payer.getUUID(), priced, how, pos.toShortString(), be.getOwnerName(), owner);

        ServerPlayer ownerPlayer = payer.server.getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) {
            ownerPlayer.sendSystemMessage(Component.literal("💸 " + payer.getName().getString() + " paid $" + fmt(be.getPrice())
                    + " at your depositor terminal (" + pos.toShortString() + ")").withStyle(ChatFormatting.GOLD));
        }
    }

    @Nullable
    private static DepositorTerminalBlockEntity terminalNear(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > DepositorTerminalBlock.MAX_USE_DISTANCE_SQ) return null;
        return player.level().getBlockEntity(pos) instanceof DepositorTerminalBlockEntity be ? be : null;
    }

    private static String errorText(ApiResponse<?> resp, String fallback) {
        if (resp.getPlayerMessage() != null) return resp.getPlayerMessage();
        if (resp.getMessage() != null) return resp.getMessage();
        return fallback;
    }

    private static String fmt(int amount) {
        return NumberFormat.getInstance().format(amount);
    }

    private static ChatFormatting colorFor(int kind) {
        return switch (kind) {
            case KIND_SUCCESS -> ChatFormatting.GREEN;
            case KIND_ERROR -> ChatFormatting.RED;
            default -> ChatFormatting.WHITE;
        };
    }

    private static void actionBar(ServerPlayer player, int kind, String msg) {
        player.displayClientMessage(Component.literal(msg).withStyle(colorFor(kind)), true);
    }

    private static void sendResult(ServerPlayer player, int kind, String msg) {
        player.connection.send(new ClientboundCustomPayloadPacket(new DepositorResultPayload(kind, msg)));
    }

    private static void handleResultClient(final DepositorResultPayload pkt, final IPayloadContext ctx) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof com.saunhardy.createringtoncurrency.client.DepositorScreen scr) {
                scr.onResult(pkt.kind(), pkt.message());
            } else if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(pkt.message()), false);
            }
        });
    }
}
