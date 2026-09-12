package com.saunhardy.createringtoncurrency.audit;

import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

public final class RegionSweep {
    public static final String SOURCE_CONTAINERS = "Containers";
    public static final String SOURCE_ENTITIES = "Entities";

    private static final int REGION_CHUNKS = 32;

    public record Dimension(String id, Path region, Path entities) {}

    public static int countRegionFiles(List<Dimension> dimensions) {
        int total = 0;
        for (Dimension dimension : dimensions) {
            total += listRegions(dimension.region(), null).size() + listRegions(dimension.entities(), null).size();
        }
        return total;
    }

    public static void sweep(List<Dimension> dimensions, CashCensus census, int threads, IntConsumer progress) {
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger done = new AtomicInteger();

        for (Dimension dimension : dimensions) {
            for (Path file : listRegions(dimension.region(), census)) {
                tasks.add(() -> {
                    scan(dimension, file, true, census);
                    progress.accept(done.incrementAndGet());
                });
            }
            for (Path file : listRegions(dimension.entities(), census)) {
                tasks.add(() -> {
                    scan(dimension, file, false, census);
                    progress.accept(done.incrementAndGet());
                });
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads), runnable -> {
            Thread thread = new Thread(runnable, "createringtoncurrency-audit-region");
            thread.setDaemon(true);
            return thread;
        });

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : tasks) futures.add(pool.submit(task));

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    census.markIncomplete();
                    census.warn("A region file scan failed: " + e.getCause());
                } catch (InterruptedException e) {
                    census.markIncomplete();
                    census.warn("The scan was interrupted before every region file was read");
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void scan(Dimension dimension, Path file, boolean blockEntities, CashCensus census) {
        int[] origin = originOf(file);
        if (origin == null) return;

        try (RegionReader region = RegionReader.open(file)) {
            int chunks = 0;
            for (int index = 0; index < RegionReader.CHUNKS; index++) {
                if (!region.has(index)) continue;

                int chunkX = origin[0] + (index & 31);
                int chunkZ = origin[1] + (index >> 5);

                try {
                    byte[] raw = region.readRaw(index, chunkX, chunkZ);
                    if (raw == null) continue;

                    chunks++;
                    if (!NbtCash.mightHoldBills(raw)) continue;

                    CompoundTag chunk = RegionReader.parse(raw);
                    if (blockEntities) blockEntities(chunk, dimension, census);
                    else entities(chunk, dimension, census);
                } catch (IOException | RuntimeException e) {
                    census.warn("Skipped chunk " + chunkX + ", " + chunkZ + " of " + file.getFileName() + ": " + e.getMessage());
                }
            }
            census.countRegion(chunks);
        } catch (IOException | RuntimeException e) {
            census.warn("Could not open " + file.getFileName() + ": " + e);
        }
    }

    private static void blockEntities(CompoundTag chunk, Dimension dimension, CashCensus census) {
        ListTag list = chunk.getList("block_entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            int[] counts = Bills.none();
            boolean truncated = NbtCash.count(entry, counts);
            if (Bills.isEmpty(counts)) continue;

            CashSite site = new CashSite(prettyId(entry.getString("id")), dimension.id(),
                    entry.getInt("x"), entry.getInt("y"), entry.getInt("z"), counts);
            if (truncated) census.warn("Stopped at the nesting limit inside " + site.label() + " at " + site.coords());
            census.add(SOURCE_CONTAINERS, site);
        }
    }

    private static void entities(CompoundTag chunk, Dimension dimension, CashCensus census) {
        ListTag list = chunk.getList("Entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            int[] counts = Bills.none();
            boolean truncated = NbtCash.count(entry, counts);
            if (Bills.isEmpty(counts)) continue;

            if (truncated) census.warn("Stopped at the nesting limit inside " + prettyId(entry.getString("id")));

            ListTag pos = entry.getList("Pos", Tag.TAG_DOUBLE);
            int x = pos.size() == 3 ? (int) Math.floor(pos.getDouble(0)) : 0;
            int y = pos.size() == 3 ? (int) Math.floor(pos.getDouble(1)) : 0;
            int z = pos.size() == 3 ? (int) Math.floor(pos.getDouble(2)) : 0;

            census.add(SOURCE_ENTITIES, new CashSite(prettyId(entry.getString("id")), dimension.id(), x, y, z, counts));
        }
    }

    private static List<Path> listRegions(Path folder, CashCensus census) {
        if (!Files.isDirectory(folder)) return List.of();

        try (Stream<Path> stream = Files.list(folder)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".mca")).sorted().toList();
        } catch (IOException e) {
            if (census != null) census.warn("Could not list " + folder.getFileName() + ": " + e);
            return List.of();
        }
    }

    private static int[] originOf(Path file) {
        String[] parts = file.getFileName().toString().split("\\.");
        if (parts.length != 4) return null;

        try {
            return new int[]{Integer.parseInt(parts[1]) * REGION_CHUNKS, Integer.parseInt(parts[2]) * REGION_CHUNKS};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String prettyId(String id) {
        String path = id == null ? "" : id.substring(id.indexOf(':') + 1);
        if (path.isEmpty()) return "Unknown";

        StringBuilder sb = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return sb.toString();
    }

    private RegionSweep() {}
}
