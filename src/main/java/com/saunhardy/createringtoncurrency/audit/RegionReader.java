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
    private static final int MAX_CHUNK_BYTES = 64 * 1024 * 1024;
    private static final int MAX_INFLATED_BYTES = 256 * 1024 * 1024;

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
            return NbtIo.read(data, NbtAccounter.unlimitedHeap());
        }
    }

    public static boolean contains(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) return false;

        byte first = needle[0];
        int last = haystack.length - needle.length;
        outer:
        for (int i = 0; i <= last; i++) {
            if (haystack[i] != first) continue;
            for (int j = 1; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    public byte[] readRaw(int index, int chunkX, int chunkZ) throws IOException {
        int offset = offsets[index];
        if (offset == 0) return null;

        long start = (long) (offset >>> 8) * SECTOR;
        int sectors = offset & 0xFF;
        if (offset >>> 8 < 2 || sectors == 0 || start + (long) sectors * SECTOR > size) return null;

        ByteBuffer prefix = ByteBuffer.allocate(5);
        if (!fill(prefix, start)) return null;
        prefix.flip();

        int length = prefix.getInt();
        int compression = prefix.get() & 0xFF;

        RegionFileVersion version = RegionFileVersion.fromId(compression & ~EXTERNAL_FLAG);
        if (version == null) return null;

        byte[] payload;
        if ((compression & EXTERNAL_FLAG) != 0) {
            Path external = folder.resolve("c." + chunkX + "." + chunkZ + ".mcc");
            if (!Files.isRegularFile(external) || Files.size(external) > MAX_CHUNK_BYTES) return null;
            payload = Files.readAllBytes(external);
        } else {
            int bytes = length - 1;
            if (bytes <= 0 || bytes > MAX_CHUNK_BYTES || start + 5 + bytes > size) return null;

            ByteBuffer buffer = ByteBuffer.allocate(bytes);
            if (!fill(buffer, start + 5)) return null;
            payload = buffer.array();
        }

        try (InputStream stream = version.wrap(new ByteArrayInputStream(payload))) {
            return inflate(stream);
        }
    }

    private static byte[] inflate(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = stream.read(buffer)) > 0) {
            if (out.size() + read > MAX_INFLATED_BYTES) return null;
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

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
