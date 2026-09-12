package com.saunhardy.createringtoncurrency.audit;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.util.Bills;
import com.saunhardy.createringtoncurrency.util.PendingBillsData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class CashAudit {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PERMISSION_LEVEL = 2;
    private static final int PROGRESS_EVERY = 64;
    private static final int MAX_GOTO_SITES = 200;

    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final Map<UUID, List<CashSite>> LAST_SITES = new ConcurrentHashMap<>();

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "createringtoncurrency-audit");
        thread.setDaemon(true);
        return thread;
    });

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("createringtoncurrency")
                        .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                        .then(Commands.literal("audit")
                                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                                .executes(ctx -> start(ctx.getSource(), true))
                                .then(Commands.literal("players")
                                        .executes(ctx -> start(ctx.getSource(), false)))
                                .then(Commands.literal("goto")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(ctx -> goTo(ctx.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(ctx, "index")))))));
    }

    private static int start(CommandSourceStack source, boolean full) {
        MinecraftServer server = source.getServer();
        UUID initiator = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;

        if (!RUNNING.compareAndSet(false, true)) {
            source.sendFailure(Component.literal("A cash audit is already running."));
            return 0;
        }

        CashCensus census = new CashCensus(full);
        Set<UUID> online;
        List<RegionSweep.Dimension> dimensions;
        int regionFiles;
        int threads;

        try {
            online = PlayerSweep.online(server, census);
            dimensions = full ? dimensions(server) : List.of();
            regionFiles = full ? RegionSweep.countRegionFiles(dimensions) : 0;
            threads = full ? threads() : 0;

            if (full) {
                source.sendSystemMessage(Component.literal("Saving the world, then reading " + Bills.fmt(regionFiles)
                        + " region files. This can take a while.").withStyle(ChatFormatting.GRAY));
                server.saveEverything(true, true, true);
            }
        } catch (RuntimeException e) {
            RUNNING.set(false);
            LOGGER.error("Cash audit could not start", e);
            source.sendFailure(Component.literal("The audit could not start: " + e));
            return 0;
        }

        try {
            WORKER.execute(sweep(server, initiator, census, full, online, dimensions, regionFiles, threads));
        } catch (RuntimeException e) {
            RUNNING.set(false);
            LOGGER.error("Cash audit could not be queued", e);
            source.sendFailure(Component.literal("The audit could not start: " + e));
            return 0;
        }

        return 1;
    }

    private static Runnable sweep(MinecraftServer server, UUID initiator, CashCensus census, boolean full,
                                  Set<UUID> online, List<RegionSweep.Dimension> dimensions, int regionFiles, int threads) {
        return () -> {
            try {
                PlayerSweep.offline(server, online, census);

                if (full) {
                    RegionSweep.sweep(dimensions, census, threads, done -> {
                        if (done % PROGRESS_EVERY == 0) {
                            tell(server, initiator, Component.literal("Audit: " + Bills.fmt(done) + " / "
                                    + Bills.fmt(regionFiles) + " region files").withStyle(ChatFormatting.DARK_GRAY));
                        }
                    });
                }
            } catch (RuntimeException e) {
                LOGGER.error("Cash audit failed", e);
                census.markIncomplete();
                census.warn("The scan stopped early: " + e);
            } finally {
                census.finish();
                server.execute(() -> finish(server, initiator, census));
            }
        };
    }

    private static void finish(MinecraftServer server, UUID initiator, CashCensus census) {
        try {
            census.setPending(PendingBillsData.get(server).total());

            LastAuditData last = LastAuditData.get(server);
            boolean baseline = census.isFull() && census.isComplete();
            int[] previousTotals = baseline ? last.totals() : null;
            long previousAt = last.takenAt();

            Path report = AuditReport.write(server, census);
            for (Component line : AuditReport.chat(census, previousTotals, previousAt,
                    Config.AUDIT_CHAT_SITES.get(), report)) {
                tell(server, initiator, line);
            }

            if (baseline) last.record(census.totals(), System.currentTimeMillis());
            if (initiator != null) {
                List<CashSite> sites = census.sites();
                LAST_SITES.put(initiator, List.copyOf(sites.subList(0, Math.min(MAX_GOTO_SITES, sites.size()))));
            }

            LOGGER.info("Cash audit ({}): ${} across {} bills in {} sites, {} players, {} chunks",
                    census.isFull() ? "full" : "players", Bills.fmt(census.total()), Bills.fmt(census.pieces()),
                    census.sites().size(), census.playersScanned(), census.chunksScanned());
        } finally {
            RUNNING.set(false);
        }
    }

    private static int goTo(ServerPlayer player, int index) {
        List<CashSite> sites = LAST_SITES.get(player.getUUID());
        if (sites == null || sites.isEmpty()) {
            player.sendSystemMessage(Component.literal("Run /createringtoncurrency audit first.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (index > sites.size()) {
            player.sendSystemMessage(Component.literal("Your last audit kept only " + sites.size()
                    + " locations for teleporting; the rest are in the written report.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        CashSite site = sites.get(index - 1);
        ResourceLocation id = ResourceLocation.tryParse(site.dimension());
        ServerLevel level = id == null ? null : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            player.sendSystemMessage(Component.literal("The dimension " + site.dimension() + " is no longer loaded.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        player.teleportTo(level, site.x() + 0.5, site.y() + 1.0, site.z() + 0.5, player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("Teleported to " + site.label() + " holding $"
                + Bills.fmt(site.value())).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SITES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_SITES.clear();
        RUNNING.set(false);
    }

    private static int threads() {
        int configured = Config.AUDIT_THREADS.get();
        if (configured > 0) return configured;
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    }

    private static List<RegionSweep.Dimension> dimensions(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        List<RegionSweep.Dimension> dimensions = new ArrayList<>();

        for (ServerLevel level : server.getAllLevels()) {
            Path folder = DimensionType.getStorageFolder(level.dimension(), root);
            dimensions.add(new RegionSweep.Dimension(level.dimension().location().toString(),
                    folder.resolve("region"), folder.resolve("entities")));
        }

        return dimensions;
    }

    private static void tell(MinecraftServer server, UUID initiator, Component line) {
        server.execute(() -> {
            ServerPlayer player = initiator == null ? null : server.getPlayerList().getPlayer(initiator);
            if (player != null) player.sendSystemMessage(line);
            else server.sendSystemMessage(line);
        });
    }
}
