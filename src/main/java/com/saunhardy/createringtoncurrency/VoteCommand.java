package com.saunhardy.createringtoncurrency;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoteCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int VOTE_DURATION_TICKS = 30 * 20; // 30 seconds
    private static final long COOLDOWN_SUCCESS_MS = 577_100L;
    private static final long COOLDOWN_FAIL_MS = 3 * 60_000L;
    private static final List<String> VOTE_TYPES = List.of("day", "night", "clear", "thunder", "rain");

    private static final Set<String> WEATHER_TYPES = Set.of("clear", "rain", "thunder");
    private static final Set<String> TIME_TYPES = Set.of("day", "night");

    private static final int MINECRAFT_DAY_TICKS = 24000;
    private static final int DEFAULT_WEATHER_TICKS = 6000;
    private static final int MAX_WEATHER_DAYS = 7;

    private static volatile ActiveVote activeVote = null;
    private static long weatherCooldownUntil = 0L;
    private static long timeCooldownUntil = 0L;

    private static class ActiveVote {
        final String type;
        final int durationDays; // -1 when unspecified (non-weather or default)
        final UUID initiator;
        final String initiatorName;
        final Set<UUID> yesVotes = ConcurrentHashMap.newKeySet();
        final Set<UUID> noVotes = ConcurrentHashMap.newKeySet();
        int ticksRemaining;

        ActiveVote(String type, int durationDays, UUID initiator, String initiatorName) {
            this.type = type;
            this.durationDays = durationDays;
            this.initiator = initiator;
            this.initiatorName = initiatorName;
            this.ticksRemaining = VOTE_DURATION_TICKS;
            this.yesVotes.add(initiator); // initiator votes yes automatically
        }
    }

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        if (Config.DISABLE_VOTE_COMMAND.get()) return;

        event.getDispatcher().register(
                Commands.literal("vote")
                        .then(Commands.literal("yes").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            return castVote(player, true);
                        }))
                        .then(Commands.literal("no").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            return castVote(player, false);
                        }))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        VOTE_TYPES, builder))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String type = StringArgumentType.getString(context, "type").toLowerCase();
                                    return startVote(player, type, -1);
                                })
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, MAX_WEATHER_DAYS))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String type = StringArgumentType.getString(context, "type").toLowerCase();
                                            int days = IntegerArgumentType.getInteger(context, "days");
                                            return startVote(player, type, days);
                                        })
                                )
                        )
        );
    }

    private static int startVote(ServerPlayer player, String type, int durationDays) {
        if (!VOTE_TYPES.contains(type)) {
            player.sendSystemMessage(Component.literal("❌ Invalid vote type. Use: " + String.join(", ", VOTE_TYPES))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (durationDays > 0 && !WEATHER_TYPES.contains(type)) {
            player.sendSystemMessage(Component.literal("❌ Duration only applies to weather votes (clear, rain, thunder).")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = WEATHER_TYPES.contains(type) ? weatherCooldownUntil : timeCooldownUntil;
        if (now < cooldownUntil) {
            long secsLeft = (cooldownUntil - now) / 1000;
            String category = WEATHER_TYPES.contains(type) ? "Weather" : "Time";
            player.sendSystemMessage(Component.literal("❌ " + category + " vote is on cooldown! " + secsLeft + "s remaining")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (activeVote != null) {
            player.sendSystemMessage(Component.literal("❌ A vote is already in progress! Use /vote yes or /vote no")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return 0;

        if (server.getPlayerList().getPlayers().size() <= 1) {
            player.sendSystemMessage(Component.literal("✅ Vote passed!")
                    .withStyle(ChatFormatting.GREEN));
            applyVote(type, durationDays, server);
            setCooldown(type, COOLDOWN_SUCCESS_MS);
            return 1;
        }

        // Start the vote
        activeVote = new ActiveVote(type, durationDays, player.getUUID(), player.getName().getString());
        LOGGER.info("Vote started by {} for '{}'{}", player.getName().getString(), type,
                durationDays > 0 ? " (" + durationDays + " day" + (durationDays == 1 ? "" : "s") + ")" : "");

        // Broadcast to all players
        broadcastVoteStart(server, player.getName().getString(), type, durationDays);

        return 1;
    }

    private static int castVote(ServerPlayer player, boolean yes) {
        ActiveVote vote = activeVote;
        if (vote == null) {
            player.sendSystemMessage(Component.literal("❌ No vote is currently active. Start one with /vote <type>")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        UUID uuid = player.getUUID();
        if (vote.yesVotes.contains(uuid) || vote.noVotes.contains(uuid)) {
            player.sendSystemMessage(Component.literal("❌ You have already voted!")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        if (yes) {
            vote.yesVotes.add(uuid);
        } else {
            vote.noVotes.add(uuid);
        }

        player.sendSystemMessage(Component.literal("✅ Vote recorded!")
                .withStyle(ChatFormatting.GREEN));

        return 1;
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (activeVote == null) return;

        String msg = event.getRawText();
        if (msg.equals("1")) {
            castVote(event.getPlayer(), true);
            event.setCanceled(true);
        } else if (msg.equals("2")) {
            castVote(event.getPlayer(), false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ActiveVote vote = activeVote;
        if (vote == null) return;

        vote.ticksRemaining--;

        if (vote.ticksRemaining <= 0) {
            resolveVote(event.getServer(), vote);
            activeVote = null;
        }
    }

    private static void resolveVote(MinecraftServer server, ActiveVote vote) {
        int yes = vote.yesVotes.size();
        int no = vote.noVotes.size();
        boolean passed = yes > no;

        MutableComponent result = Component.literal(passed ? "✅ Vote passed! " : "❌ Vote failed! ")
                .withStyle(passed ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(Component.literal(yes + " Yes").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(no + " No").withStyle(ChatFormatting.RED));
        broadcastToAll(server, result);

        setCooldown(vote.type, passed ? COOLDOWN_SUCCESS_MS : COOLDOWN_FAIL_MS);

        if (passed) {
            applyVote(vote.type, vote.durationDays, server);
        }

        LOGGER.info("Vote for '{}' by {} {} ({} yes, {} no)",
                vote.type, vote.initiatorName, passed ? "passed" : "failed", yes, no);
    }

    private static void setCooldown(String type, long durationMs) {
        long until = System.currentTimeMillis() + durationMs;
        if (WEATHER_TYPES.contains(type)) {
            weatherCooldownUntil = until;
        } else {
            timeCooldownUntil = until;
        }
    }

    private static void applyVote(String type, int durationDays, MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        int ticks = durationDays > 0 ? durationDays * MINECRAFT_DAY_TICKS : DEFAULT_WEATHER_TICKS;
        switch (type) {
            case "day" -> overworld.setDayTime(1000); // morning
            case "night" -> overworld.setDayTime(13000); // night
            case "clear" -> overworld.setWeatherParameters(ticks, 0, false, false);
            case "rain" -> overworld.setWeatherParameters(0, ticks, true, false);
            case "thunder" -> overworld.setWeatherParameters(0, ticks, true, true);
        }
    }

    private static void broadcastVoteStart(MinecraftServer server, String playerName, String type, int durationDays) {
        MutableComponent header = Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .withStyle(ChatFormatting.GOLD);

        MutableComponent body = Component.literal("🗳 ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(playerName).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" started a vote to set ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(type).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        if (durationDays > 0) {
            body.append(Component.literal(" for ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(durationDays + " day" + (durationDays == 1 ? "" : "s"))
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        }
        body.append(Component.literal("!").withStyle(ChatFormatting.GOLD));

        MutableComponent buttons = Component.literal("   ")
                .append(clickableButton("[ ✔ YES ]", "/vote yes", ChatFormatting.GREEN))
                .append(Component.literal("    ").withStyle(ChatFormatting.RESET))
                .append(clickableButton("[ ✘ NO ]", "/vote no", ChatFormatting.RED));

        MutableComponent timer = Component.literal("⏳ You have 30 seconds to vote!")
                .withStyle(ChatFormatting.GRAY);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(header);
            p.sendSystemMessage(body);
            p.sendSystemMessage(buttons);
            p.sendSystemMessage(timer);
            p.sendSystemMessage(header);
        }
    }

    private static MutableComponent clickableButton(String label, String command, ChatFormatting color) {
        return Component.literal(label)
                .withStyle(style -> style
                        .withColor(color)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to " + command))));
    }

    private static void broadcastToAll(MinecraftServer server, Component message) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(message);
        }
    }
}
