package com.saunhardy.createringtoncurrency.audit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

import java.io.ByteArrayInputStream;
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
            if (channel.read(header, 0L) >= SECTOR) {
                header.flip();
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
        int offset = offsets[index];
        if (offset == 0) return null;

        long start = (long) (offset >>> 8) * SECTOR;
        int sectors = offset & 0xFF;
        if (offset >>> 8 < 2 || sectors == 0 || start + (long) sectors * SECTOR > size) return null;

        ByteBuffer prefix = ByteBuffer.allocate(5);
        if (channel.read(prefix, start) != 5) return null;
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
            if (channel.read(buffer, start + 5) != bytes) return null;
            payload = buffer.array();
        }

        try (InputStream stream = version.wrap(new ByteArrayInputStream(payload));
             DataInputStream data = new DataInputStream(stream)) {
            return NbtIo.read(data, NbtAccounter.unlimitedHeap());
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
