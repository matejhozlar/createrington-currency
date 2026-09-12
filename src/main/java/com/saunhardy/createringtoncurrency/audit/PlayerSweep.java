package com.saunhardy.createringtoncurrency.audit;

import com.mojang.authlib.GameProfile;
import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public final class PlayerSweep {
    public static final String SOURCE_ONLINE = "Online players";
    public static final String SOURCE_OFFLINE = "Offline players";

    public static Set<UUID> online(MinecraftServer server, CashCensus census) {
        Set<UUID> seen = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            seen.add(player.getUUID());
            census.countPlayer();

            int[] counts = Bills.none();
            boolean truncated = LiveCash.count(player.getInventory(), counts);
            truncated |= LiveCash.count(player.getEnderChestInventory(), counts);
            truncated |= craftingGrids(player, counts);
            if (player.containerMenu != null) truncated |= LiveCash.count(player.containerMenu.getCarried(), counts);

            if (truncated) census.warn("Stopped at the nesting limit inside " + player.getName().getString() + "'s items");

            census.add(SOURCE_ONLINE, new CashSite(
                    "Player " + player.getName().getString(),
                    player.level().dimension().location().toString(),
                    player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                    counts));
        }

        return seen;
    }

    public static void offline(MinecraftServer server, Set<UUID> skip, CashCensus census) {
        Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (!Files.isDirectory(dir)) return;

        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".dat")).sorted().toList();
        } catch (IOException e) {
            census.warn("Could not list player data: " + e);
            return;
        }

        for (Path file : files) {
            UUID uuid = uuidOf(file);
            if (uuid == null || skip.contains(uuid)) continue;

            try {
                CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                census.countPlayer();

                int[] counts = Bills.none();
                if (NbtCash.count(tag, counts)) census.warn("Stopped at the nesting limit inside " + file.getFileName());
                if (Bills.isEmpty(counts)) continue;

                ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
                int x = pos.size() == 3 ? (int) Math.floor(pos.getDouble(0)) : 0;
                int y = pos.size() == 3 ? (int) Math.floor(pos.getDouble(1)) : 0;
                int z = pos.size() == 3 ? (int) Math.floor(pos.getDouble(2)) : 0;
                String dimension = tag.contains("Dimension", Tag.TAG_STRING) ? tag.getString("Dimension") : "minecraft:overworld";

                census.add(SOURCE_OFFLINE, new CashSite(
                        "Player " + nameOf(server, uuid) + " (offline)", dimension, x, y, z, counts));
            } catch (IOException | RuntimeException e) {
                census.warn("Could not read " + file.getFileName() + ": " + e);
            }
        }
    }

    private static boolean craftingGrids(ServerPlayer player, int[] counts) {
        Set<Container> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean truncated = false;

        if (seen.add(player.inventoryMenu.getCraftSlots())) {
            truncated |= LiveCash.count(player.inventoryMenu.getCraftSlots(), counts);
        }

        if (player.containerMenu != null) {
            for (Slot slot : player.containerMenu.slots) {
                if (slot.container instanceof TransientCraftingContainer grid && seen.add(grid)) {
                    truncated |= LiveCash.count(grid, counts);
                }
            }
        }

        return truncated;
    }

    private static UUID uuidOf(Path file) {
        String name = file.getFileName().toString();
        try {
            return UUID.fromString(name.substring(0, name.length() - ".dat".length()));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    private static String nameOf(MinecraftServer server, UUID uuid) {
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            Optional<GameProfile> profile = cache.get(uuid);
            if (profile.isPresent()) return profile.get().getName();
        }
        return uuid.toString().substring(0, 8);
    }

    private PlayerSweep() {}
}
