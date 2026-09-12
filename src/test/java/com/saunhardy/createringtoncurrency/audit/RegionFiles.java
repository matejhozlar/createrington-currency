package com.saunhardy.createringtoncurrency.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

final class RegionFiles {
    static final int SECTOR = 4096;
    static final int HEADER = 2 * SECTOR;

    static byte[] compress(CompoundTag tag, RegionFileVersion version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream wrapped = version.wrap(bytes);
             DataOutputStream data = new DataOutputStream(wrapped)) {
            NbtIo.write(tag, data);
        }
        return bytes.toByteArray();
    }

    static byte[] payload(CompoundTag tag, RegionFileVersion version) throws IOException {
        byte[] compressed = compress(tag, version);
        ByteBuffer buffer = ByteBuffer.allocate(5 + compressed.length);
        buffer.putInt(compressed.length + 1);
        buffer.put((byte) version.getId());
        buffer.put(compressed);
        return buffer.array();
    }

    static byte[] stub(int compression) {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.putInt(1);
        buffer.put((byte) compression);
        return buffer.array();
    }

    static Path write(Path folder, String name, int index, byte[] payload, Integer forcedOffset) throws IOException {
        int sectors = Math.max(1, (payload.length + SECTOR - 1) / SECTOR);
        byte[] region = new byte[HEADER + sectors * SECTOR];

        ByteBuffer buffer = ByteBuffer.wrap(region);
        buffer.putInt(index * 4, forcedOffset != null ? forcedOffset : (2 << 8) | sectors);
        System.arraycopy(payload, 0, region, HEADER, payload.length);

        Path file = folder.resolve(name);
        Files.write(file, region);
        return file;
    }

    private RegionFiles() {}
}
