package com.saunhardy.createringtoncurrency.audit;

import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

public class LastAuditData extends SavedData {
    private static final String DATA_NAME = "createringtoncurrency_last_audit";
    private static final String TAG_TOTALS = "Totals";
    private static final String TAG_TAKEN_AT = "TakenAt";

    private int[] totals;
    private long takenAt;

    public static LastAuditData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(LastAuditData::new, LastAuditData::load, null), DATA_NAME);
    }

    public static LastAuditData load(CompoundTag tag, HolderLookup.Provider registries) {
        LastAuditData data = new LastAuditData();
        int[] stored = tag.getIntArray(TAG_TOTALS);
        if (stored.length == Bills.DENOMINATIONS.length) {
            data.totals = stored;
            data.takenAt = tag.getLong(TAG_TAKEN_AT);
        }
        return data;
    }

    public int[] totals() {
        return totals;
    }

    public long takenAt() {
        return takenAt;
    }

    public void record(int[] newTotals, long when) {
        this.totals = newTotals.clone();
        this.takenAt = when;
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        if (totals != null) {
            tag.putIntArray(TAG_TOTALS, totals);
            tag.putLong(TAG_TAKEN_AT, takenAt);
        }
        return tag;
    }
}
