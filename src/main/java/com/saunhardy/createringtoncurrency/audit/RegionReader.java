package com.saunhardy.createringtoncurrency.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class RegionReader implements AutoCloseable {
    public static final int CHUNKS = 1024;

    private static final int SECTOR = 4096;
    private static final int HEADER = 2 * SECTOR;
    private static final int EXTERNAL_FLAG = 0x80;
    private static final int MAX_STORED_BYTES = 64 * 1024 * 1024;
    private static final int MAX_INFLATED_BYTES = 64 * 1024 * 1024;
    private static final long MAX_TAG_BYTES = 256L * 1024 * 1024;

    private final Path folder;
    private final FileChannel channel;
    private final long size;
    private final int[] offsets = new int[CHUNKS];

    private RegionReader(Path folder, FileChannel channel, long size) {
        this.folder = folder;
        this.channel = channel;
        this.size = size;
    }

    public static RegionReader open(Path file) throws IOException {
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
        try {
            RegionReader reader = new RegionReader(file.getParent(), channel, channel.size());
            ByteBuffer header = ByteBuffer.allocate(HEADER);
            reader.fill(header, 0L);
            if (header.position() >= SECTOR) {
                for (int i = 0; i < CHUNKS; i++) reader.offsets[i] = header.getInt(i * 4);
            }
            return reader;
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    public boolean has(int index) {
        return offsets[index] != 0;
    }

    public CompoundTag read(int index, int chunkX, int chunkZ) throws IOException {
        byte[] raw = readRaw(index, chunkX, chunkZ);
        return raw == null ? null : parse(raw);
    }

    public static CompoundTag parse(byte[] raw) throws IOException {
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(raw))) {
            return NbtIo.read(data, NbtAccounter.create(MAX_TAG_BYTES));
        }
    }

    public byte[] readRaw(int index, int chunkX, int chunkZ) throws IOException {
        int offset = offsets[index];
        if (offset == 0) return null;

        int sector = offset >>> 8;
        int sectors = offset & 0xFF;
        long start = (long) sector * SECTOR;
        if (sector < 2 || sectors == 0 || start + (long) sectors * SECTOR > size) {
            throw new IOException(describe(chunkX, chunkZ) + " points outside the file");
        }

        ByteBuffer prefix = ByteBuffer.allocate(5);
        if (!fill(prefix, start)) throw new IOException(describe(chunkX, chunkZ) + " is truncated");
        prefix.flip();

        int length = prefix.getInt();
        int compression = prefix.get() & 0xFF;
        int compressionId = compression & ~EXTERNAL_FLAG;

        RegionFileVersion version = RegionFileVersion.fromId(compressionId);
        if (version == null) throw new IOException(describe(chunkX, chunkZ) + " uses unknown compression " + compressionId);

        byte[] stored;
        if ((compression & EXTERNAL_FLAG) != 0) {
            Path external = folder.resolve("c." + chunkX + "." + chunkZ + ".mcc");
            if (!Files.isRegularFile(external)) {
                throw new IOException(describe(chunkX, chunkZ) + " is missing its external file " + external.getFileName());
            }
            if (Files.size(external) > MAX_STORED_BYTES) {
                throw new IOException(external.getFileName() + " is larger than " + MAX_STORED_BYTES + " bytes");
            }
            stored = Files.readAllBytes(external);
        } else {
            int bytes = length - 1;
            if (bytes <= 0 || bytes > MAX_STORED_BYTES || start + 5 + bytes > size) {
                throw new IOException(describe(chunkX, chunkZ) + " has an invalid length " + length);
            }
            ByteBuffer buffer = ByteBuffer.allocate(bytes);
            if (!fill(buffer, start + 5)) throw new IOException(describe(chunkX, chunkZ) + " is truncated");
            stored = buffer.array();
        }

        try (InputStream stream = version.wrap(new ByteArrayInputStream(stored))) {
            return inflate(stream, chunkX, chunkZ);
        }
    }

    private static byte[] inflate(InputStream stream, int chunkX, int chunkZ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            if (out.size() + read > MAX_INFLATED_BYTES) {
                throw new IOException(describe(chunkX, chunkZ) + " inflates past " + MAX_INFLATED_BYTES + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private boolean fill(ByteBuffer buffer, long position) throws IOException {
        long at = position;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, at);
            if (read < 0) return false;
            at += read;
        }
        return true;
    }

    private static String describe(int chunkX, int chunkZ) {
        return "chunk " + chunkX + ", " + chunkZ;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
