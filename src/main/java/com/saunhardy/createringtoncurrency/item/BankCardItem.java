package com.saunhardy.createringtoncurrency.item;

import com.saunhardy.createrington.api.currency.Transaction;
import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.MoneyCommands;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
import com.saunhardy.createringtoncurrency.util.BillDelivery;
import com.saunhardy.createringtoncurrency.util.Bills;
import com.saunhardy.createringtoncurrency.util.TransactionFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public class BankCardItem extends Item {
    private static final int HISTORY_LIMIT = 5;
    private static final int MIN_COOLDOWN_TICKS = 20;

    public BankCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.OFF_HAND && !player.getMainHandItem().isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(stack);
        if (Config.DISABLE_BANK_CARD_USE.get()) {
            serverPlayer.displayClientMessage(MoneyCommands.message("💳",
                    "Bank Card balance checks are disabled on this server.", ChatFormatting.RED), true);
            return InteractionResultHolder.pass(stack);
        }

        int cooldownTicks = Math.max(MIN_COOLDOWN_TICKS, Config.COMMAND_COOLDOWN_MS.get() / 50);
        serverPlayer.getCooldowns().addCooldown(this, cooldownTicks);

        if (serverPlayer.isShiftKeyDown()) {
            requestHistory(serverPlayer);
        } else {
            requestBalance(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    private static void requestBalance(ServerPlayer player) {
        MinecraftServer server = player.server;
        UUID uuid = player.getUUID();
        CurrencyApi.balance(uuid)
                .thenAccept(resp -> BillDelivery.whenOnline(server, uuid, p -> {
                    if (resp.isSuccess() && resp.getData() != null) {
                        p.displayClientMessage(MoneyCommands.message("💳",
                                "Balance: $" + Bills.fmt(resp.getData().balance()), ChatFormatting.GREEN), true);
                    } else {
                        MoneyCommands.sendApiError(p, "Bank card balance", resp);
                    }
                }))
                .exceptionally(ex -> {
                    BillDelivery.whenOnline(server, uuid, p -> MoneyCommands.sendException(p, "Bank card balance", ex));
                    return null;
                });
    }

    private static void requestHistory(ServerPlayer player) {
        MinecraftServer server = player.server;
        UUID uuid = player.getUUID();
        CurrencyApi.history(uuid, 1, HISTORY_LIMIT)
                .thenAccept(resp -> BillDelivery.whenOnline(server, uuid, p -> {
                    if (!resp.isSuccess() || resp.getData() == null) {
                        MoneyCommands.sendApiError(p, "Bank card history", resp);
                        return;
                    }
                    List<Transaction> transactions = resp.getData().transactions();
                    if (transactions == null || transactions.isEmpty()) {
                        p.sendSystemMessage(MoneyCommands.message("💳", "No transactions yet.", ChatFormatting.GRAY));
                        return;
                    }
                    p.sendSystemMessage(MoneyCommands.message("💳",
                            "Your last " + transactions.size() + " transactions:", ChatFormatting.GREEN));
                    for (Transaction t : transactions) {
                        p.sendSystemMessage(describe(t));
                    }
                }))
                .exceptionally(ex -> {
                    BillDelivery.whenOnline(server, uuid, p -> MoneyCommands.sendException(p, "Bank card history", ex));
                    return null;
                });
    }

    private static Component describe(Transaction t) {
        String raw = t.amount() == null ? "0" : t.amount();
        boolean negative = raw.startsWith("-");
        String amount = negative ? "-$" + raw.substring(1) : "+$" + raw;
        MutableComponent line = Component.literal(" " + amount)
                .withStyle(negative ? ChatFormatting.RED : ChatFormatting.GREEN)
                .append(Component.literal(" " + TransactionFormat.type(t.transactionType())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" · " + TransactionFormat.relativeDate(t.createdAt())).withStyle(ChatFormatting.GRAY));
        if (t.description() != null && !t.description().isBlank()) {
            line.append(Component.literal(" — " + t.description()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.translatable("item.createringtoncurrency.bank_card.tooltip.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.createringtoncurrency.bank_card.tooltip.sneak_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Required for Stock Ticker shopping list integration").withStyle(ChatFormatting.GRAY));

        if (flag.isAdvanced()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Usage Instructions:").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("1. Hold shopping list in main hand").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("2. Hold bank card in offhand").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("3. Right-click the Stock Keeper or its Blaze Burner").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("4. Missing bills are withdrawn; right-click again to pay").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Requirements:").withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.literal("• Shopping list must contain currency bills").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• Sufficient account balance required").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• Enough inventory space for bills").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Press F3+H for detailed usage instructions").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
