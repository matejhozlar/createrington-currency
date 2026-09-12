package com.saunhardy.createringtoncurrency.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionReaderTest {
    private static final int SECTOR = 4096;
    private static final int HEADER = 2 * SECTOR;
    private static final int INDEX = 5 + 3 * 32;

    @TempDir
    Path folder;

    @Test
    void readsADeflatedChunk() throws IOException {
        Path file = write(INDEX, payload(chunk("hello"), RegionFileVersion.VERSION_DEFLATE, false), null);

        try (RegionReader reader = RegionReader.open(file)) {
            assertTrue(reader.has(INDEX));
            CompoundTag tag = reader.read(INDEX, 5, 3);
            assertNotNull(tag);
            assertEquals("hello", tag.getString("marker"));
        }
    }

    @Test
    void readsAnUncompressedChunk() throws IOException {
        Path file = write(INDEX, payload(chunk("plain"), RegionFileVersion.VERSION_NONE, false), null);

        try (RegionReader reader = RegionReader.open(file)) {
            assertEquals("plain", reader.read(INDEX, 5, 3).getString("marker"));
        }
    }

    @Test
    void readsAnExternalChunk() throws IOException {
        byte[] external = compress(chunk("external"), RegionFileVersion.VERSION_DEFLATE);
        Path file = write(INDEX, stub(RegionFileVersion.VERSION_DEFLATE.getId() | 0x80), null);
        Files.write(folder.resolve("c.5.3.mcc"), external);

        try (RegionReader reader = RegionReader.open(file)) {
            assertEquals("external", reader.read(INDEX, 5, 3).getString("marker"));
        }
    }

    @Test
    void rawBytesRevealWhetherAChunkCanHoldBills() throws IOException {
        byte[] needle = NbtCash.BILL_PREFIX.getBytes(StandardCharsets.UTF_8);

        CompoundTag withBill = chunk("hello");
        withBill.putString("SomeItemId", NbtCash.BILL_PREFIX + "100");
        Path file = write(INDEX, payload(withBill, RegionFileVersion.VERSION_DEFLATE, false), null);
        try (RegionReader reader = RegionReader.open(file)) {
            byte[] raw = reader.readRaw(INDEX, 5, 3);
            assertTrue(RegionReader.contains(raw, needle));
            assertEquals("hello", RegionReader.parse(raw).getString("marker"));
        }

        file = write(INDEX, payload(chunk("hello"), RegionFileVersion.VERSION_DEFLATE, false), null);
        try (RegionReader reader = RegionReader.open(file)) {
            assertFalse(RegionReader.contains(reader.readRaw(INDEX, 5, 3), needle));
        }
    }

    @Test
    void findsANeedleAtEitherEndOfTheHaystack() {
        byte[] needle = "bill_".getBytes(StandardCharsets.UTF_8);
        assertTrue(RegionReader.contains("bill_xyz".getBytes(StandardCharsets.UTF_8), needle));
        assertTrue(RegionReader.contains("xyzbill_".getBytes(StandardCharsets.UTF_8), needle));
        assertFalse(RegionReader.contains("bill".getBytes(StandardCharsets.UTF_8), needle));
        assertFalse(RegionReader.contains("bil_bil_".getBytes(StandardCharsets.UTF_8), needle));
    }

    @Test
    void reportsMissingChunks() throws IOException {
        Path file = write(INDEX, payload(chunk("hello"), RegionFileVersion.VERSION_DEFLATE, false), null);

        try (RegionReader reader = RegionReader.open(file)) {
            assertFalse(reader.has(0));
            assertNull(reader.read(0, 0, 0));
        }
    }

    @Test
    void refusesASectorPastTheEndOfTheFile() throws IOException {
        Path file = write(INDEX, payload(chunk("hello"), RegionFileVersion.VERSION_DEFLATE, false), (1 << 20 << 8) | 1);

        try (RegionReader reader = RegionReader.open(file)) {
            assertTrue(reader.has(INDEX));
            assertNull(reader.read(INDEX, 5, 3));
        }
    }

    @Test
    void refusesASectorInsideTheHeader() throws IOException {
        Path file = write(INDEX, payload(chunk("hello"), RegionFileVersion.VERSION_DEFLATE, false), (1 << 8) | 1);

        try (RegionReader reader = RegionReader.open(file)) {
            assertNull(reader.read(INDEX, 5, 3));
        }
    }

    @Test
    void refusesAnUnknownCompression() throws IOException {
        Path file = write(INDEX, stub(99), null);

        try (RegionReader reader = RegionReader.open(file)) {
            assertNull(reader.read(INDEX, 5, 3));
        }
    }

    @Test
    void survivesAnEmptyFile() throws IOException {
        Path file = folder.resolve("r.0.0.mca");
        Files.write(file, new byte[0]);

        try (RegionReader reader = RegionReader.open(file)) {
            assertFalse(reader.has(0));
        }
    }

    private static CompoundTag chunk(String marker) {
        CompoundTag tag = new CompoundTag();
        tag.putString("marker", marker);
        return tag;
    }

    private static byte[] compress(CompoundTag tag, RegionFileVersion version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream wrapped = version.wrap(bytes);
             DataOutputStream data = new DataOutputStream(wrapped)) {
            NbtIo.write(tag, data);
        }
        return bytes.toByteArray();
    }

    private static byte[] payload(CompoundTag tag, RegionFileVersion version, boolean external) throws IOException {
        byte[] compressed = compress(tag, version);
        ByteBuffer buffer = ByteBuffer.allocate(5 + compressed.length);
        buffer.putInt(compressed.length + 1);
        buffer.put((byte) (external ? version.getId() | 0x80 : version.getId()));
        buffer.put(compressed);
        return buffer.array();
    }

    private static byte[] stub(int compression) {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.putInt(1);
        buffer.put((byte) compression);
        return buffer.array();
    }

    private Path write(int index, byte[] payload, Integer forcedOffset) throws IOException {
        int sectors = Math.max(1, (payload.length + SECTOR - 1) / SECTOR);
        byte[] region = new byte[HEADER + sectors * SECTOR];

        ByteBuffer buffer = ByteBuffer.wrap(region);
        buffer.putInt(index * 4, forcedOffset != null ? forcedOffset : (2 << 8) | sectors);
        System.arraycopy(payload, 0, region, HEADER, payload.length);

        Path file = folder.resolve("r.0.0.mca");
        Files.write(file, region);
        return file;
    }
}
