package com.saunhardy.createringtoncurrency.util;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PendingBillsData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "createringtoncurrency_pending_bills";
    private static final String TAG_PENDING = "Pending";

    private final Map<UUID, int[]> pending = new HashMap<>();

    public static PendingBillsData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(PendingBillsData::new, PendingBillsData::load, null), DATA_NAME);
    }

    public static PendingBillsData load(CompoundTag tag, HolderLookup.Provider registries) {
        PendingBillsData data = new PendingBillsData();
        CompoundTag pendingTag = tag.getCompound(TAG_PENDING);
        for (String key : pendingTag.getAllKeys()) {
            try {
                int[] stored = pendingTag.getIntArray(key);
                int[] counts = Bills.none();
                System.arraycopy(stored, 0, counts, 0, Math.min(stored.length, counts.length));
                if (!Bills.isEmpty(counts)) data.pending.put(UUID.fromString(key), counts);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Skipping invalid pending bills entry '{}': {}", key, e.getMessage());
            }
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        CompoundTag pendingTag = new CompoundTag();
        pending.forEach((uuid, counts) -> pendingTag.putIntArray(uuid.toString(), counts));
        tag.put(TAG_PENDING, pendingTag);
        return tag;
    }

    public void add(UUID uuid, int[] counts) {
        int[] existing = pending.computeIfAbsent(uuid, u -> Bills.none());
        for (int i = 0; i < existing.length; i++) existing[i] += counts[i];
        setDirty();
    }

    @Nullable
    public int[] take(UUID uuid) {
        int[] counts = pending.remove(uuid);
        if (counts != null) setDirty();
        return counts;
    }
}
