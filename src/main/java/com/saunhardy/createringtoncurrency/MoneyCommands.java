package com.saunhardy.createringtoncurrency;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
import com.saunhardy.createringtoncurrency.util.Bills;
import com.saunhardy.createringtoncurrency.util.Deposits;
import com.saunhardy.createringtoncurrency.util.Withdrawals;
import com.saunhardy.crnet.http.ApiResponse;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.text.NumberFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MoneyCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static long lastLotteryStartTime = 0L;

    private static long getCooldownMs() {
        return Config.COMMAND_COOLDOWN_MS.get();
    }

    private static long getLotteryCooldownMs() {
        return Config.LOTTERY_COOLDOWN_MINUTES.get() * 60L * 1000L;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        COOLDOWNS.clear();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        COOLDOWNS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        registerUnlessDisabled(event, Config.DISABLE_MONEY_COMMAND.get(),
                Commands.literal("money")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (isOnCooldown(player)) return 0;
                            CurrencyApi.balance(player.getUUID())
                                    .thenAccept(resp -> handleBalance(player, resp))
                                    .exceptionally(ex -> { sendException(player, "Balance", ex); return null; });
                            return 1;
                        })
        );

        registerUnlessDisabled(event, Config.DISABLE_PAY_COMMAND.get(),
                Commands.literal("pay")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                                            if (isOnCooldown(sender)) return 0;
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            CurrencyApi.pay(sender.getUUID(), target.getUUID().toString(), amount)
                                                    .thenAccept(resp -> handlePay(sender, target, amount, resp))
                                                    .exceptionally(ex -> { sendException(sender, "Pay", ex); return null; });
                                            return 1;
                                        })))
        );

        registerUnlessDisabled(event, Config.DISABLE_CASH_COMMANDS.get(),
                Commands.literal("deposit")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (isOnCooldown(player)) return 0;
                            handleDepositAll(player);
                            return 1;
                        })
        );

        registerUnlessDisabled(event, Config.DISABLE_CASH_COMMANDS.get(),
                Commands.literal("withdraw")
                        .then(Commands.argument("input", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    if (isOnCooldown(player)) return 0;
                                    String input = StringArgumentType.getString(ctx, "input").trim();
                                    try {
                                        if (input.matches("^\\d+ \\d+$")) {
                                            String[] parts = input.split(" ");
                                            int denom = Integer.parseInt(parts[0]);
                                            int count = Integer.parseInt(parts[1]);
                                            if (denom <= 0 || count <= 0) {
                                                player.sendSystemMessage(message("[ERROR]", "Amount must be positive.", ChatFormatting.RED));
                                                return 0;
                                            }
                                            return withdrawFixed(player, denom, count);
                                        }
                                        if (input.contains(":")) {
                                            return withdrawCustomBundle(player, input);
                                        }
                                        if (input.matches("^\\d+$")) {
                                            int total = Integer.parseInt(input);
                                            if (total <= 0) {
                                                player.sendSystemMessage(message("[ERROR]", "Amount must be positive.", ChatFormatting.RED));
                                                return 0;
                                            }
                                            return withdrawOptimized(player, total);
                                        }
                                    } catch (NumberFormatException e) {
                                        player.sendSystemMessage(message("[ERROR]", "Number too large.", ChatFormatting.RED));
                                        return 0;
                                    }
                                    player.sendSystemMessage(message("[ERROR]", "Invalid command format.", ChatFormatting.RED));
                                    return 0;
                                })
                        )
        );

        registerUnlessDisabled(event, Config.DISABLE_BALTOP_COMMAND.get(),
                Commands.literal("baltop")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (isOnCooldown(player)) return 0;
                            CurrencyApi.top(player.getUUID())
                                    .thenAccept(resp -> handleTop(player, resp))
                                    .exceptionally(ex -> { sendException(player, "Baltop", ex); return null; });
                            return 1;
                        })
        );

        registerUnlessDisabled(event, Config.DISABLE_DAILY_COMMAND.get(),
                Commands.literal("daily")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (isOnCooldown(player)) return 0;
                            CurrencyApi.daily(player.getUUID())
                                    .thenAccept(resp -> handleDaily(player, resp))
                                    .exceptionally(ex -> { sendException(player, "Daily reward", ex); return null; });
                            return 1;
                        })
        );

        registerUnlessDisabled(event, Config.DISABLE_LOTTERY_COMMANDS.get(),
                Commands.literal("lottery")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(10))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    long now = System.currentTimeMillis();
                                    long cooldownMs = getLotteryCooldownMs();
                                    if (cooldownMs > 0 && now - lastLotteryStartTime < cooldownMs) {
                                        long seconds = (cooldownMs - (now - lastLotteryStartTime)) / 1000;
                                        player.sendSystemMessage(Component.literal("⏳ A lottery is already running or was recently started. Try again in " + seconds + "s.").withStyle(ChatFormatting.RED));
                                        return 1;
                                    }
                                    CurrencyApi.lotteryStart(player.getUUID(), amount)
                                            .thenAccept(resp -> handleLotteryStart(player, amount, resp))
                                            .exceptionally(ex -> { sendException(player, "Lottery start", ex); return null; });
                                    return 1;
                                })
                        )
        );

        registerUnlessDisabled(event, Config.DISABLE_LOTTERY_COMMANDS.get(),
                Commands.literal("join")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(10))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    CurrencyApi.lotteryJoin(player.getUUID(), amount)
                                            .thenAccept(resp -> handleLotteryJoin(player, amount, resp))
                                            .exceptionally(ex -> { sendException(player, "Join lottery", ex); return null; });
                                    return 1;
                                })
                        )
        );
    }

    private static void registerUnlessDisabled(RegisterCommandsEvent event,
                                               boolean disabled,
                                               LiteralArgumentBuilder<CommandSourceStack> command) {
        if (disabled) return;
        event.getDispatcher().register(command);
    }

    // ---- Response handlers -------------------------------------------------

    private static void handleBalance(ServerPlayer player, ApiResponse<?> resp) {
        if (resp.isSuccess()) {
            String text = resp.getPlayerMessage() != null
                    ? resp.getPlayerMessage()
                    : "Balance retrieved";
            player.sendSystemMessage(message("💰", text, ChatFormatting.GREEN));
        } else {
            sendApiError(player, "Balance", resp);
        }
    }

    private static void handlePay(ServerPlayer sender, ServerPlayer target, int amount, ApiResponse<?> resp) {
        if (resp.isSuccess()) {
            String formatted = NumberFormat.getInstance().format(amount);
            sender.sendSystemMessage(message("✅", "Sent $" + formatted + " to " + target.getName().getString(), ChatFormatting.GREEN));
            target.sendSystemMessage(message("💸", "You received $" + formatted + " from " + sender.getName().getString(), ChatFormatting.GOLD));
            LOGGER.info("[PAY] {} ({}) -> {} ({}): ${}", sender.getName().getString(), sender.getUUID(), target.getName().getString(), target.getUUID(), formatted);
        } else {
            sendApiError(sender, "Pay", resp);
        }
    }

    private static void handleTop(ServerPlayer player, ApiResponse<java.util.List<com.saunhardy.createrington.api.currency.TopEntry>> resp) {
        if (!resp.isSuccess()) {
            sendApiError(player, "Baltop", resp);
            return;
        }
        var entries = resp.getData();
        if (entries == null || entries.isEmpty()) {
            player.sendSystemMessage(message("[ERROR]", "No data found.", ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(message("🏆", "Top 10 Richest Players:", ChatFormatting.GREEN));
        int rank = 1;
        for (var entry : entries) {
            String formatted = NumberFormat.getInstance().format((long) entry.balance());
            player.sendSystemMessage(Component.literal(" " + rank + ". " + entry.name() + ": $" + formatted));
            rank++;
        }
    }

    private static void handleDaily(ServerPlayer player, ApiResponse<?> resp) {
        if (resp.isSuccess()) {
            String text = resp.getPlayerMessage() != null ? resp.getPlayerMessage() : "Reward claimed!";
            player.sendSystemMessage(message("✅", text, ChatFormatting.GREEN));
        } else {
            sendApiError(player, "Daily reward", resp);
        }
    }

    private static void handleLotteryStart(ServerPlayer player, int amount, ApiResponse<?> resp) {
        if (resp.isSuccess()) {
            lastLotteryStartTime = System.currentTimeMillis();
            String text = resp.getPlayerMessage() != null
                    ? resp.getPlayerMessage()
                    : "You successfully started a lottery with $" + amount + "!";
            player.sendSystemMessage(message("🎲", text, ChatFormatting.GREEN));
        } else {
            sendApiError(player, "Lottery start", resp);
        }
    }

    private static void handleLotteryJoin(ServerPlayer player, int amount, ApiResponse<?> resp) {
        if (resp.isSuccess()) {
            String text = resp.getPlayerMessage() != null
                    ? resp.getPlayerMessage()
                    : "You joined the lottery with $" + amount + ". Good luck!";
            player.sendSystemMessage(message("✅", text, ChatFormatting.GREEN));
        } else {
            sendApiError(player, "Join lottery", resp);
        }
    }

    // ---- Withdraw variants -------------------------------------------------

    private static int withdrawFixed(ServerPlayer player, int denomination, int count) {
        int index = Bills.indexOfDenomination(denomination);
        if (index < 0) {
            player.sendSystemMessage(message("[ERROR]", "Invalid denomination.", ChatFormatting.RED));
            return 0;
        }
        return submitWithdrawal(player, Bills.only(index, count));
    }

    private static int withdrawCustomBundle(ServerPlayer player, String input) {
        long[] totals = new long[Bills.DENOMINATIONS.length];
        long bills = 0;
        for (String part : input.split(" ")) {
            String[] pair = part.split(":");
            if (pair.length != 2) {
                player.sendSystemMessage(message("[ERROR]", "Invalid format: " + part, ChatFormatting.RED));
                return 0;
            }
            int denom, count;
            try {
                denom = Integer.parseInt(pair[0]);
                count = Integer.parseInt(pair[1]);
            } catch (NumberFormatException e) {
                player.sendSystemMessage(message("[ERROR]", "Invalid number in: " + part, ChatFormatting.RED));
                return 0;
            }
            if (count <= 0 || denom <= 0) {
                player.sendSystemMessage(message("[ERROR]", "Invalid denomination or count: " + part, ChatFormatting.RED));
                return 0;
            }
            int index = Bills.indexOfDenomination(denom);
            if (index < 0) {
                player.sendSystemMessage(message("[ERROR]", "Unsupported denomination: $" + denom, ChatFormatting.RED));
                return 0;
            }
            if (count > Withdrawals.MAX_BILLS) {
                player.sendSystemMessage(message("[ERROR]", "Too many bills in " + part + " (at most " + Bills.fmt(Withdrawals.MAX_BILLS) + " per withdrawal).", ChatFormatting.RED));
                return 0;
            }
            totals[index] += count;
            bills += count;
        }
        if (bills > Withdrawals.MAX_BILLS) {
            player.sendSystemMessage(message("[ERROR]", "Too many bills: at most " + Bills.fmt(Withdrawals.MAX_BILLS) + " per withdrawal.", ChatFormatting.RED));
            return 0;
        }
        int[] counts = Bills.none();
        for (int i = 0; i < counts.length; i++) counts[i] = (int) totals[i];
        return submitWithdrawal(player, counts);
    }

    private static int withdrawOptimized(ServerPlayer player, int totalAmount) {
        return submitWithdrawal(player, Bills.breakdown(totalAmount));
    }

    private static int submitWithdrawal(ServerPlayer player, int[] counts) {
        Withdrawals.withdraw(player, counts, "command", new Withdrawals.Reporter() {
            @Override
            public void succeeded(ServerPlayer recipient, long amount) {
                recipient.sendSystemMessage(message("✅", "Successfully withdrew $" + Bills.fmt(amount), ChatFormatting.GREEN));
            }

            @Override
            public void failed(ServerPlayer recipient, String text) {
                recipient.sendSystemMessage(message("❌", text, ChatFormatting.RED));
            }
        });
        return 1;
    }

    private static void handleDepositAll(ServerPlayer player) {
        Deposits.depositAll(player, "command", new Deposits.Reporter() {
            @Override
            public void started(ServerPlayer recipient, long amount) {
                recipient.sendSystemMessage(message("Processing deposit of", "$" + Bills.fmt(amount) + "...", ChatFormatting.YELLOW));
            }

            @Override
            public void succeeded(ServerPlayer recipient, long amount, String playerMessage) {
                String text = playerMessage != null
                        ? playerMessage
                        : "Deposited $" + Bills.fmt(amount) + " into your account!";
                recipient.sendSystemMessage(message("✅", text, ChatFormatting.GREEN));
            }

            @Override
            public void failed(ServerPlayer recipient, String text) {
                recipient.sendSystemMessage(message("❌", text, ChatFormatting.RED));
            }
        });
    }

    // ---- Shared helpers ----------------------------------------------------

    public static Component message(String emoji, String text, ChatFormatting color) {
        return Component.literal(emoji + " " + text).withStyle(color);
    }

    private static boolean isOnCooldown(ServerPlayer player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUUID();
        if (COOLDOWNS.containsKey(uuid)) {
            long lastUsed = COOLDOWNS.get(uuid);
            if (now - lastUsed < getCooldownMs()) {
                long secondsLeft = (getCooldownMs() - (now - lastUsed)) / 1000;
                player.sendSystemMessage(message("[COOLDOWN]", "Please wait " + secondsLeft + "s before using this command again.", ChatFormatting.RED));
                return true;
            }
        }
        COOLDOWNS.put(uuid, now);
        return false;
    }

    /** Surfaces {@code playerMessage} first, falls back to {@code message}, then a generic string. */
    public static void sendApiError(ServerPlayer player, String context, ApiResponse<?> resp) {
        String text = resp.getPlayerMessage() != null
                ? resp.getPlayerMessage()
                : (resp.getMessage() != null ? resp.getMessage() : "Something went wrong. Please try again.");
        player.sendSystemMessage(message("❌", text, ChatFormatting.RED));
        LOGGER.warn("{} failed for {}: {}", context, player.getName().getString(), resp.getMessage());
    }

    public static void sendException(ServerPlayer player, String context, Throwable ex) {
        LOGGER.error("{} error for {}: {}", context, player.getName().getString(), ex.getMessage());
        player.sendSystemMessage(message("❌", "Something went wrong. Please try again.", ChatFormatting.RED));
    }
}
