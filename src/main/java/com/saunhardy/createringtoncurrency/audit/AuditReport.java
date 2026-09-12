package com.saunhardy.createringtoncurrency.audit;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AuditReport {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String REPORT_DIR = "createringtoncurrency-audits";
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter READABLE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static List<Component> chat(CashCensus census, int[] previousTotals, long previousAt, int limit, Path reportFile) {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.literal("━━━━━━━━━ Cash audit ━━━━━━━━━").withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("Physical cash: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("$" + Bills.fmt(census.total())).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                .append(Component.literal(" across " + Bills.fmt(census.pieces()) + " bills").withStyle(ChatFormatting.GRAY)));

        for (Map.Entry<String, int[]> source : census.bySource().entrySet()) {
            lines.add(Component.literal("  " + source.getKey() + ": ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("$" + Bills.fmt(Bills.value(source.getValue()))).withStyle(ChatFormatting.WHITE)));
        }

        if (previousTotals != null) {
            long delta = census.total() - Bills.value(previousTotals);
            ChatFormatting colour = delta > 0 ? ChatFormatting.YELLOW : delta < 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY;
            lines.add(Component.literal("Change since " + ago(previousAt) + ": ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal((delta > 0 ? "+$" : delta < 0 ? "-$" : "$") + Bills.fmt(Math.abs(delta)))
                            .withStyle(colour)));
        }

        lines.add(Component.literal(coverage(census)).withStyle(ChatFormatting.DARK_GRAY));

        List<CashSite> sites = census.sites();
        if (sites.isEmpty()) {
            lines.add(Component.literal("No bills found anywhere.").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.literal("Where it is:").withStyle(ChatFormatting.GOLD));
            for (int i = 0; i < Math.min(limit, sites.size()); i++) {
                lines.add(row(i + 1, sites.get(i)));
            }
            if (sites.size() > limit) {
                lines.add(Component.literal("  ...and " + Bills.fmt(sites.size() - limit) + " more in the full report")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        for (String warning : census.warnings()) {
            lines.add(Component.literal("! " + warning).withStyle(ChatFormatting.RED));
        }

        if (reportFile != null) {
            lines.add(Component.literal("Full report: " + REPORT_DIR + "/" + reportFile.getFileName())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        return lines;
    }

    private static MutableComponent row(int index, CashSite site) {
        MutableComponent go = Component.literal("[go]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/createringtoncurrency audit goto " + index))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Teleport to " + site.coords() + " in " + site.shortDimension()))));

        MutableComponent copy = Component.literal("[copy]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, site.teleportCommand()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Copy " + site.teleportCommand()))));

        return Component.literal(" " + index + ". ").withStyle(ChatFormatting.DARK_GRAY)
                .append(go)
                .append(Component.literal(" "))
                .append(copy)
                .append(Component.literal(" $" + Bills.fmt(site.value())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" " + site.label()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" " + site.shortDimension() + " " + site.coords()).withStyle(ChatFormatting.DARK_GRAY))
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(site.breakdown()))));
    }

    public static Path write(MinecraftServer server, CashCensus census) {
        Path dir = server.getServerDirectory().resolve(REPORT_DIR);
        Path file = dir.resolve("audit-" + LocalDateTime.now().format(FILE_STAMP) + ".txt");

        StringBuilder sb = new StringBuilder();
        sb.append("Createrington Currency cash audit\n");
        sb.append("Taken ").append(LocalDateTime.now().format(READABLE_STAMP)).append('\n');
        sb.append(census.isFull() ? "Scope: full world scan\n" : "Scope: players only\n");
        sb.append(coverage(census)).append("\n\n");

        sb.append("Total: $").append(Bills.fmt(census.total()))
                .append(" across ").append(Bills.fmt(census.pieces())).append(" bills\n");
        for (int i = 0; i < Bills.DENOMINATIONS.length; i++) {
            sb.append("  $").append(Bills.DENOMINATIONS[i]).append(" x ").append(Bills.fmt(census.totals()[i])).append('\n');
        }

        sb.append("\nBy source\n");
        for (Map.Entry<String, int[]> source : census.bySource().entrySet()) {
            sb.append("  ").append(source.getKey()).append(": $").append(Bills.fmt(Bills.value(source.getValue())))
                    .append(" (").append(Bills.fmt(Bills.pieces(source.getValue()))).append(" bills)\n");
        }

        sb.append("\nSites (").append(Bills.fmt(census.sites().size())).append(")\n");
        for (CashSite site : census.sites()) {
            sb.append(String.format("%14s  %-28s %-24s %s%n",
                    "$" + Bills.fmt(site.value()), site.label(),
                    site.shortDimension() + " " + site.coords(), site.breakdown()));
            sb.append("                ").append(site.teleportCommand()).append('\n');
        }

        if (!census.warnings().isEmpty()) {
            sb.append("\nWarnings\n");
            for (String warning : census.warnings()) sb.append("  ").append(warning).append('\n');
        }

        try {
            Files.createDirectories(dir);
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            LOGGER.error("Could not write the cash audit report: {}", e.toString());
            census.warn("Could not write the report file: " + e);
            return null;
        }
    }

    private static String coverage(CashCensus census) {
        StringBuilder sb = new StringBuilder("Scanned ");
        sb.append(Bills.fmt(census.playersScanned())).append(" players");
        if (census.isFull()) {
            sb.append(", ").append(Bills.fmt(census.regionsScanned())).append(" region files")
                    .append(", ").append(Bills.fmt(census.chunksScanned())).append(" chunks");
        }
        sb.append(" in ").append(Bills.fmt(census.durationMs() / 1000.0)).append("s");
        if (!census.isFull()) sb.append(" (containers and entities not included)");
        return sb.toString();
    }

    private static String ago(long epochMillis) {
        Duration since = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.now());
        long days = since.toDays();
        if (days > 0) return days + (days == 1 ? " day ago" : " days ago");

        long hours = since.toHours();
        if (hours > 0) return hours + (hours == 1 ? " hour ago" : " hours ago");

        long minutes = Math.max(1, since.toMinutes());
        return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
    }

    private AuditReport() {}
}
