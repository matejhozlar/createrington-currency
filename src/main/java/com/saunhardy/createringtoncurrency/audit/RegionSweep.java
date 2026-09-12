package com.saunhardy.createringtoncurrency.audit;

import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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

    public static void sweep(List<Dimension> dimensions, CashCensus census, IntConsumer progress) {
        int done = 0;
        for (Dimension dimension : dimensions) {
            done = scan(dimension, dimension.region(), true, census, progress, done);
            done = scan(dimension, dimension.entities(), false, census, progress, done);
        }
    }

    private static int scan(Dimension dimension, Path folder, boolean blockEntities,
                            CashCensus census, IntConsumer progress, int done) {
        List<Path> files = listRegions(folder, census);
        if (files.isEmpty()) return done;

        for (Path file : files) {
            long[] origin = originOf(file);
            if (origin == null) continue;

            try (RegionReader region = RegionReader.open(file)) {
                int chunks = 0;
                for (int index = 0; index < RegionReader.CHUNKS; index++) {
                    if (!region.has(index)) continue;

                    int chunkX = (int) origin[0] + (index & 31);
                    int chunkZ = (int) origin[1] + (index >> 5);

                    CompoundTag chunk = region.read(index, chunkX, chunkZ);
                    if (chunk == null) continue;

                    chunks++;
                    if (blockEntities) blockEntities(chunk, dimension, census);
                    else entities(chunk, dimension, census);
                }
                census.countRegion(chunks);
            } catch (IOException | RuntimeException e) {
                census.warn("Could not read " + file.getFileName() + ": " + e);
            }

            progress.accept(++done);
        }

        return done;
    }

    private static void blockEntities(CompoundTag chunk, Dimension dimension, CashCensus census) {
        ListTag list = chunk.getList("block_entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            int[] counts = Bills.none();
            NbtCash.countHolder(entry, counts);
            if (Bills.isEmpty(counts)) continue;

            census.add(SOURCE_CONTAINERS, new CashSite(prettyId(entry.getString("id")), dimension.id(),
                    entry.getInt("x"), entry.getInt("y"), entry.getInt("z"), counts));
        }
    }

    private static void entities(CompoundTag chunk, Dimension dimension, CashCensus census) {
        ListTag list = chunk.getList("Entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            int[] counts = Bills.none();
            NbtCash.countHolder(entry, counts);
            if (Bills.isEmpty(counts)) continue;

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

    private static long[] originOf(Path file) {
        String[] parts = file.getFileName().toString().split("\\.");
        if (parts.length != 4) return null;

        try {
            return new long[]{Long.parseLong(parts[1]) * REGION_CHUNKS, Long.parseLong(parts[2]) * REGION_CHUNKS};
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
