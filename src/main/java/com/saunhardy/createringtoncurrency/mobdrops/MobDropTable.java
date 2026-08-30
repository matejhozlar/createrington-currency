package com.saunhardy.createringtoncurrency.mobdrops;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MobDropTable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern FORMAT = Pattern.compile("^(#?[a-z0-9_.\\-]+:[a-z0-9_./\\-]+)=(\\d+):(\\d+(?:\\.\\d+)?)$");

    public record Entry(ResourceLocation id, @Nullable TagKey<EntityType<?>> tag, int denomination, double chance) {
        boolean matches(EntityType<?> type) {
            return tag != null ? type.is(tag) : id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        }
    }

    private static volatile List<Entry> entries;
    private static final Map<EntityType<?>, List<Entry>> BY_TYPE = new ConcurrentHashMap<>();

    private MobDropTable() {}

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        invalidate(event);
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        invalidate(event);
    }

    private static void invalidate(ModConfigEvent event) {
        if (event.getConfig().getSpec() != Config.SPEC) return;
        entries = null;
        BY_TYPE.clear();
    }

    public static List<Entry> entriesFor(EntityType<?> type) {
        return BY_TYPE.computeIfAbsent(type, t -> entries().stream().filter(e -> e.matches(t)).toList());
    }

    public static double bonusFor(int level) {
        if (level <= 0) return 0.0;
        List<? extends Number> bonus = Config.CAPITALIST_GREED_BONUS.get();
        if (bonus.isEmpty()) return 0.0;
        return bonus.get(Math.min(level, bonus.size()) - 1).doubleValue();
    }

    private static List<Entry> entries() {
        List<Entry> current = entries;
        if (current != null) return current;
        List<Entry> parsed = new ArrayList<>();
        for (String raw : Config.MOB_DROPS.get()) {
            Entry entry = parse(raw);
            if (entry == null) {
                LOGGER.warn("[MOBDROPS] Ignoring malformed drop entry '{}'", raw);
                continue;
            }
            if (entry.tag() == null && !BuiltInRegistries.ENTITY_TYPE.containsKey(entry.id())) {
                LOGGER.warn("[MOBDROPS] Drop entry '{}' names an unknown entity type; it will never match", raw);
            }
            parsed.add(entry);
        }
        current = List.copyOf(parsed);
        entries = current;
        return current;
    }

    @Nullable
    static Entry parse(String raw) {
        Matcher matcher = FORMAT.matcher(raw.trim());
        if (!matcher.matches()) return null;
        String key = matcher.group(1);
        boolean tag = key.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? key.substring(1) : key);
        if (id == null) return null;
        int denomination;
        double chance;
        try {
            denomination = Integer.parseInt(matcher.group(2));
            chance = Double.parseDouble(matcher.group(3));
        } catch (NumberFormatException e) {
            return null;
        }
        if (Bills.indexOfDenomination(denomination) < 0 || chance < 0.0 || chance > 100.0) return null;
        return new Entry(id, tag ? TagKey.create(Registries.ENTITY_TYPE, id) : null, denomination, chance);
    }
}
