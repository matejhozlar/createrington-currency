package com.saunhardy.createringtoncurrency.mobdrops;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MobEarningsData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "createringtoncurrency_mob_earnings";

    private final Map<UUID, DailyEarnings> earnings = new HashMap<>();

    public MobEarningsData() {}

    public static MobEarningsData load(CompoundTag tag, HolderLookup.Provider registries) {
        MobEarningsData data = new MobEarningsData();
        LocalDate today = LocalDate.now();
        CompoundTag earningsTag = tag.getCompound("earnings");
        for (String key : earningsTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                CompoundTag entry = earningsTag.getCompound(key);
                String dateStr = entry.getString("date");
                int earned = entry.getInt("earned");
                LocalDate date = LocalDate.parse(dateStr);
                if (date.equals(today)) {
                    data.earnings.put(uuid, new DailyEarnings(date, earned));
                }
            } catch (Exception e) {
                LOGGER.warn("Skipping invalid mob earnings entry '{}': {}", key, e.getMessage());
            }
        }
        LOGGER.info("Loaded mob daily earnings for {} players", data.earnings.size());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag earningsTag = new CompoundTag();
        LocalDate today = LocalDate.now();
        for (Map.Entry<UUID, DailyEarnings> entry : earnings.entrySet()) {
            DailyEarnings de = entry.getValue();
            if (de.date.equals(today)) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString("date", de.date.toString());
                entryTag.putInt("earned", de.earnedToday);
                earningsTag.put(entry.getKey().toString(), entryTag);
            }
        }
        tag.put("earnings", earningsTag);
        return tag;
    }

    public int getEarned(UUID uuid, LocalDate today) {
        DailyEarnings de = earnings.get(uuid);
        if (de == null) return 0;
        if (!de.date.equals(today)) {
            earnings.remove(uuid);
            return 0;
        }
        return de.earnedToday;
    }

    public void addEarnings(UUID uuid, int amount, LocalDate today) {
        DailyEarnings de = earnings.computeIfAbsent(uuid, u -> new DailyEarnings(today, 0));
        if (!de.date.equals(today)) {
            de.date = today;
            de.earnedToday = 0;
        }
        de.earnedToday += amount;
        setDirty();
    }

    public void pruneStaleEntries(LocalDate today) {
        if (earnings.entrySet().removeIf(e -> !e.getValue().date.equals(today))) {
            setDirty();
        }
    }

    public static SavedData.Factory<MobEarningsData> factory() {
        return new SavedData.Factory<>(MobEarningsData::new, MobEarningsData::load, null);
    }

    public static String dataName() {
        return DATA_NAME;
    }

    private static class DailyEarnings {
        LocalDate date;
        int earnedToday;

        DailyEarnings(LocalDate date, int earnedToday) {
            this.date = date;
            this.earnedToday = earnedToday;
        }
    }
}
