package com.saunhardy.createringtoncurrency;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.network.VoteOpenPayload;
import com.saunhardy.createringtoncurrency.network.VoteResultPayload;
import com.saunhardy.createringtoncurrency.network.VoteTallyPayload;
import com.saunhardy.createringtoncurrency.util.AfkStatus;
import com.saunhardy.createringtoncurrency.util.VoteQuorum;
import com.saunhardy.createringtoncurrency.util.VoteQuorum.Tally;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoteCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int VOTE_DURATION_TICKS = 30 * 20;
    private static final int TALLY_INTERVAL_TICKS = 20;
    private static final long COOLDOWN_SUCCESS_MS = 577_100L;
    private static final long COOLDOWN_FAIL_MS = 3 * 60_000L;
    private static final long COOLDOWN_NO_TURNOUT_MS = 60_000L;
    private static final List<String> VOTE_TYPES = List.of("day", "night", "clear", "thunder", "rain");

    private static final Set<String> WEATHER_TYPES = Set.of("clear", "rain", "thunder");

    private static final int MINECRAFT_DAY_TICKS = 24000;
    private static final int DEFAULT_WEATHER_TICKS = 6000;
    private static final int MAX_WEATHER_DAYS = 7;

    private static volatile ActiveVote activeVote = null;
    private static long weatherCooldownUntil = 0L;
    private static long timeCooldownUntil = 0L;

    private static class ActiveVote {
        final String type;
        final int durationDays;
        final UUID initiator;
        final String initiatorName;
        final boolean testMode;
        final Set<UUID> yesVotes = ConcurrentHashMap.newKeySet();
        final Set<UUID> noVotes = ConcurrentHashMap.newKeySet();
        int ticksRemaining;
        int maxNeeded = Integer.MAX_VALUE;

        ActiveVote(String type, int durationDays, UUID initiator, String initiatorName, boolean testMode) {
            this.type = type;
            this.durationDays = durationDays;
            this.initiator = initiator;
            this.initiatorName = initiatorName;
            this.testMode = testMode;
            this.ticksRemaining = VOTE_DURATION_TICKS;
            if (!testMode) this.yesVotes.add(initiator);
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
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(VOTE_TYPES, builder))
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

        if (player.isSpectator()) {
            player.sendSystemMessage(Component.literal("❌ Spectators cannot start a vote.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean testMode = AdminMode.isActive(player);

        long now = System.currentTimeMillis();
        long cooldownUntil = WEATHER_TYPES.contains(type) ? weatherCooldownUntil : timeCooldownUntil;
        if (!testMode && now < cooldownUntil) {
            long secsLeft = (cooldownUntil - now) / 1000;
            String category = WEATHER_TYPES.contains(type) ? "Weather" : "Time";
            player.sendSystemMessage(Component.literal("❌ " + category + " vote is on cooldown! " + secsLeft + "s remaining")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (activeVote != null) {
            player.sendSystemMessage(Component.literal("❌ A vote is already in progress!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return 0;

        ActiveVote vote = new ActiveVote(type, durationDays, player.getUUID(), player.getName().getString(), testMode);
        Tally tally = tally(server, vote);

        if (tally.decided()) {
            resolveVote(server, vote, tally);
            return 1;
        }

        vote.maxNeeded = tally.needed();
        activeVote = vote;
        LOGGER.info("Vote started by {} for '{}'{}, {} of {} eligible players needed{}", player.getName().getString(), type,
                durationDays > 0 ? " (" + durationDays + " day" + (durationDays == 1 ? "" : "s") + ")" : "",
                tally.needed(), tally.eligible(), testMode ? " (admin test vote: phantom voter, no cooldown)" : "");

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            send(p, openPayload(vote, tally, p));
        }

        return 1;
    }

    public static int castVote(ServerPlayer player, boolean yes) {
        ActiveVote vote = activeVote;
        if (vote == null) {
            player.sendSystemMessage(Component.literal("❌ No vote is currently active. Start one with /vote <type>")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (player.isSpectator()) {
            player.sendSystemMessage(Component.literal("❌ Spectators cannot vote.")
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

        MinecraftServer server = player.getServer();
        if (server != null) {
            Tally tally = tally(server, vote);
            if (tally.decided()) {
                finishVote(server, vote, tally);
            } else {
                broadcastTally(server, vote, tally);
            }
        }

        return 1;
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ActiveVote vote = activeVote;
        if (vote == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        send(player, openPayload(vote, tally(server, vote), player));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ActiveVote vote = activeVote;
        if (vote == null) return;

        vote.ticksRemaining--;

        if (vote.ticksRemaining <= 0) {
            finishVote(event.getServer(), vote, tally(event.getServer(), vote));
            return;
        }

        if (vote.ticksRemaining % TALLY_INTERVAL_TICKS == 0) {
            Tally tally = tally(event.getServer(), vote);
            if (tally.decided()) {
                finishVote(event.getServer(), vote, tally);
            } else {
                broadcastTally(event.getServer(), vote, tally);
            }
        }
    }

    private static Tally tally(MinecraftServer server, ActiveVote vote) {
        Set<UUID> voted = new HashSet<>(vote.yesVotes);
        voted.addAll(vote.noVotes);

        Set<UUID> eligible = eligibleVoters(server, voted);
        int electorate = eligible.size() + (vote.testMode ? 1 : 0);
        return VoteQuorum.tally(electorate, countIn(vote.yesVotes, eligible), countIn(vote.noVotes, eligible),
                Config.VOTE_APPROVAL_PERCENT.get(), vote.maxNeeded);
    }

    private static Set<UUID> eligibleVoters(MinecraftServer server, Set<UUID> voted) {
        boolean ignoreAfk = Config.VOTE_IGNORE_AFK.get() && AfkStatus.isInstalled();
        Set<UUID> eligible = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;

            UUID uuid = player.getUUID();
            if (voted.contains(uuid) || !(ignoreAfk && AfkStatus.isAfk(player))) {
                eligible.add(uuid);
            }
        }

        return eligible;
    }

    private static int countIn(Set<UUID> votes, Set<UUID> eligible) {
        int count = 0;
        for (UUID uuid : votes) {
            if (eligible.contains(uuid)) count++;
        }
        return count;
    }

    private static void finishVote(MinecraftServer server, ActiveVote vote, Tally tally) {
        if (activeVote != vote) return;
        activeVote = null;
        resolveVote(server, vote, tally);
    }

    private static void resolveVote(MinecraftServer server, ActiveVote vote, Tally tally) {
        boolean passed = tally.passed();

        int reason = VoteResultPayload.REASON_NONE;
        if (!passed) {
            reason = VoteQuorum.outvoted(tally) ? VoteResultPayload.REASON_OUTVOTED : VoteResultPayload.REASON_TURNOUT;
        }
        VoteResultPayload result = new VoteResultPayload(passed, tally.yes(), tally.no(), tally.needed(), tally.eligible(), reason);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            send(p, result);
        }

        long cooldown;
        if (passed) {
            cooldown = COOLDOWN_SUCCESS_MS;
        } else if (VoteQuorum.rejected(tally)) {
            cooldown = COOLDOWN_FAIL_MS;
        } else {
            cooldown = COOLDOWN_NO_TURNOUT_MS;
        }
        if (!vote.testMode) setCooldown(vote.type, cooldown);

        if (passed) {
            applyVote(vote.type, vote.durationDays, server);
        }

        LOGGER.info("Vote for '{}' by {} {} ({} yes, {} no, {} of {} needed)",
                vote.type, vote.initiatorName, passed ? "passed" : "failed",
                tally.yes(), tally.no(), tally.needed(), tally.eligible());
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
            case "day" -> overworld.setDayTime(1000);
            case "night" -> overworld.setDayTime(13000);
            case "clear" -> overworld.setWeatherParameters(ticks, 0, false, false);
            case "rain" -> overworld.setWeatherParameters(0, ticks, true, false);
            case "thunder" -> overworld.setWeatherParameters(0, ticks, true, true);
        }
    }

    private static VoteOpenPayload openPayload(ActiveVote vote, Tally tally, ServerPlayer player) {
        return new VoteOpenPayload(vote.initiatorName, vote.type, vote.durationDays, tallyPayload(vote, tally, player));
    }

    private static VoteTallyPayload tallyPayload(ActiveVote vote, Tally tally, ServerPlayer player) {
        int status;
        if (player.isSpectator()) {
            status = VoteTallyPayload.STATUS_SPECTATOR;
        } else if (vote.yesVotes.contains(player.getUUID())) {
            status = VoteTallyPayload.STATUS_VOTED_YES;
        } else if (vote.noVotes.contains(player.getUUID())) {
            status = VoteTallyPayload.STATUS_VOTED_NO;
        } else {
            status = VoteTallyPayload.STATUS_OPEN;
        }
        return new VoteTallyPayload(tally.yes(), tally.no(), tally.needed(), tally.eligible(), vote.ticksRemaining, status);
    }

    private static void broadcastTally(MinecraftServer server, ActiveVote vote, Tally tally) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            send(p, tallyPayload(vote, tally, p));
        }
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }
}
