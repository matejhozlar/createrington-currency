package com.saunhardy.createringtoncurrency.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionSweepTest {

    @TempDir
    Path world;

    @Test
    void countsABillInsideABlockEntity() throws IOException {
        CompoundTag chest = blockEntity("minecraft:chest", 100, 64, -200);
        chest.put("Items", items(bill(100, 3)));

        RegionFiles.write(region(), "r.0.0.mca", 0, RegionFiles.payload(chunkWithBlockEntities(chest), RegionFileVersion.VERSION_DEFLATE), null);

        CashCensus census = sweep();

        assertEquals(300, census.total());
        assertEquals(1, census.chunksScanned());
        assertEquals(1, census.sites().size());

        CashSite site = census.sites().get(0);
        assertEquals("Chest", site.label());
        assertEquals("minecraft:overworld", site.dimension());
        assertEquals("100 64 -200", site.coords());
        assertTrue(census.isComplete());
        assertTrue(census.warnings().isEmpty());
    }

    @Test
    void countsAnItemHandlerNestedUnderItsOwnKey() throws IOException {
        CompoundTag handler = new CompoundTag();
        handler.putInt("Size", 9);
        handler.put("Items", items(bill(1000, 2)));
        CompoundTag depositor = blockEntity("createringtoncurrency:depositor_terminal", 1, 2, 3);
        depositor.put("Storage", handler);

        RegionFiles.write(region(), "r.0.0.mca", 0, RegionFiles.payload(chunkWithBlockEntities(depositor), RegionFileVersion.VERSION_DEFLATE), null);

        CashCensus census = sweep();

        assertEquals(2000, census.total());
        assertEquals("Depositor Terminal", census.sites().get(0).label());
    }

    @Test
    void countsADroppedBillInTheEntityFiles() throws IOException {
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:item");
        item.put("Pos", position(1.5, 64.0, -2.5));
        item.put("Item", bill(50, 2));

        ListTag entities = new ListTag();
        entities.add(item);
        CompoundTag chunk = new CompoundTag();
        chunk.put("Entities", entities);

        RegionFiles.write(entities(), "r.0.0.mca", 0, RegionFiles.payload(chunk, RegionFileVersion.VERSION_DEFLATE), null);

        CashCensus census = sweep();

        assertEquals(100, census.total());
        CashSite site = census.sites().get(0);
        assertEquals("Item", site.label());
        assertEquals("1 64 -3", site.coords());
        assertEquals(100, census.bySource().get(RegionSweep.SOURCE_ENTITIES)[3] * 50);
    }

    @Test
    void countsAChunkWithoutBillsWithoutFindingAnything() throws IOException {
        CompoundTag chest = blockEntity("minecraft:chest", 0, 0, 0);
        chest.put("Items", items(stack("minecraft:cobblestone", 64)));

        RegionFiles.write(region(), "r.0.0.mca", 0, RegionFiles.payload(chunkWithBlockEntities(chest), RegionFileVersion.VERSION_DEFLATE), null);

        CashCensus census = sweep();

        assertEquals(0, census.total());
        assertEquals(1, census.chunksScanned());
        assertTrue(census.sites().isEmpty());
    }

    @Test
    void warnsAboutAChunkItCannotReadInsteadOfDroppingItQuietly() throws IOException {
        RegionFiles.write(region(), "r.0.0.mca", 0, RegionFiles.payload(chunkWithBlockEntities(), RegionFileVersion.VERSION_DEFLATE), (1 << 20 << 8) | 1);

        CashCensus census = sweep();

        assertEquals(0, census.chunksScanned());
        assertEquals(1, census.regionsScanned());
        assertFalse(census.warnings().isEmpty());
        assertTrue(census.warnings().get(0).startsWith("Skipped chunk 0, 0"));
    }

    private CashCensus sweep() throws IOException {
        CashCensus census = new CashCensus(true);
        RegionSweep.sweep(List.of(new RegionSweep.Dimension("minecraft:overworld", region(), entities())), census, 1, done -> {});
        census.finish();
        return census;
    }

    private Path region() throws IOException {
        return Files.createDirectories(world.resolve("region"));
    }

    private Path entities() throws IOException {
        return Files.createDirectories(world.resolve("entities"));
    }

    private static CompoundTag chunkWithBlockEntities(CompoundTag... blockEntities) {
        ListTag list = new ListTag();
        for (CompoundTag blockEntity : blockEntities) list.add(blockEntity);
        CompoundTag chunk = new CompoundTag();
        chunk.put("block_entities", list);
        return chunk;
    }

    private static CompoundTag blockEntity(String id, int x, int y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    private static ListTag position(double x, double y, double z) {
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(x));
        pos.add(DoubleTag.valueOf(y));
        pos.add(DoubleTag.valueOf(z));
        return pos;
    }

    private static CompoundTag bill(int denomination, int count) {
        return stack(NbtCash.BILL_PREFIX + denomination, count);
    }

    private static CompoundTag stack(String id, int count) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("count", count);
        return tag;
    }

    private static ListTag items(CompoundTag... stacks) {
        ListTag list = new ListTag();
        for (CompoundTag stack : stacks) list.add(stack);
        return list;
    }
}
