package com.saunhardy.createringtoncurrency.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionReaderTest {
    private static final int INDEX = 5 + 3 * 32;

    @TempDir
    Path folder;

    @Test
    void readsADeflatedChunk() throws IOException {
        Path file = write(RegionFiles.payload(chunk("hello"), RegionFileVersion.VERSION_DEFLATE), null);

        try (RegionReader reader = RegionReader.open(file)) {
            assertTrue(reader.has(INDEX));
            CompoundTag tag = reader.read(INDEX, 5, 3);
            assertNotNull(tag);
            assertEquals("hello", tag.getString("marker"));
        }
    }

    @Test
    void readsAnUncompressedChunk() throws IOException {
        Path file = write(RegionFiles.payload(chunk("plain"), RegionFileVersion.VERSION_NONE), null);

        try (RegionReader reader = RegionReader.open(file)) {
            assertEquals("plain", reader.read(INDEX, 5, 3).getString("marker"));
        }
    }

    @Test
    void readsAnExternalChunk() throws IOException {
        byte[] external = RegionFiles.compress(chunk("external"), RegionFileVersion.VERSION_DEFLATE);
        Path file = write(RegionFiles.stub(RegionFileVersion.VERSION_DEFLATE.getId() | 0x80), null);
        Files.write(folder.resolve("c.5.3.mcc"), external);

        try (RegionReader reader = RegionReader.open(file)) {
            assertEquals("external", reader.read(INDEX, 5, 3).getString("marker"));
        }
    }

    @Test
    void rawBytesParseToTheSameChunk() throws IOException {
        Path file = write(RegionFiles.payload(chunk("raw"), RegionFileVersion.VERSION_DEFLATE), null);

        try (RegionReader reader = RegionReader.open(file)) {
            byte[] raw = reader.readRaw(INDEX, 5, 3);
            assertNotNull(raw);
            assertEquals("raw", RegionReader.parse(raw).getString("marker"));
        }
    }

    @Test
    void reportsMissingChunks() throws IOException {
        Path file = write(RegionFiles.payload(chunk("hello"), RegionFileVersion.VERSION_DEFLATE), null);

        try (RegionReader reader = RegionReader.open(file)) {
            assertFalse(reader.has(0));
            assertNull(reader.read(0, 0, 0));
        }
    }

    @Test
    void refusesASectorPastTheEndOfTheFile() throws IOException {
        Path file = write(RegionFiles.payload(chunk("hello"), RegionFileVersion.VERSION_DEFLATE), (1 << 20 << 8) | 1);

        try (RegionReader reader = RegionReader.open(file)) {
            assertTrue(reader.has(INDEX));
            assertThrows(IOException.class, () -> reader.read(INDEX, 5, 3));
        }
    }

    @Test
    void refusesASectorInsideTheHeader() throws IOException {
        Path file = write(RegionFiles.payload(chunk("hello"), RegionFileVersion.VERSION_DEFLATE), (1 << 8) | 1);

        try (RegionReader reader = RegionReader.open(file)) {
            assertThrows(IOException.class, () -> reader.read(INDEX, 5, 3));
        }
    }

    @Test
    void refusesAnUnknownCompression() throws IOException {
        Path file = write(RegionFiles.stub(99), null);

        try (RegionReader reader = RegionReader.open(file)) {
            assertThrows(IOException.class, () -> reader.read(INDEX, 5, 3));
        }
    }

    @Test
    void refusesAMissingExternalFile() throws IOException {
        Path file = write(RegionFiles.stub(RegionFileVersion.VERSION_DEFLATE.getId() | 0x80), null);

        try (RegionReader reader = RegionReader.open(file)) {
            assertThrows(IOException.class, () -> reader.read(INDEX, 5, 3));
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

    private Path write(byte[] payload, Integer forcedOffset) throws IOException {
        return RegionFiles.write(folder, "r.0.0.mca", INDEX, payload, forcedOffset);
    }
}
