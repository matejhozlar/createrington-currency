package com.saunhardy.createringtoncurrency.datagen;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.block.DecorativeATMBlock;
import com.saunhardy.createringtoncurrency.block.DepositorTerminalBlock;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ModPonderSchematics implements DataProvider {
    private static final int PLATE_SIZE = 5;

    private final PackOutput.PathProvider paths;

    public ModPonderSchematics(PackOutput output) {
        this.paths = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "ponder");
    }

    @Override
    public @NotNull CompletableFuture<?> run(@NotNull CachedOutput cache) {
        BlockState terminal = CreateringtonCurrency.DEPOSITOR_TERMINAL_BLOCK.get().defaultBlockState()
                .setValue(DepositorTerminalBlock.FACING, Direction.NORTH);
        BlockState pricedTerminal = terminal.setValue(DepositorTerminalBlock.LIGHT, DepositorTerminalBlock.Light.READY);
        BlockState atm = CreateringtonCurrency.ATM_BLUE_BLOCK.get().defaultBlockState()
                .setValue(DecorativeATMBlock.FACING, Direction.SOUTH);

        return CompletableFuture.allOf(
                save(cache, "depositor_terminal/payment", new Schematic()
                        .set(2, 1, 2, terminal)),
                save(cache, "depositor_terminal/storage", new Schematic()
                        .set(2, 1, 2, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST))
                        .set(3, 1, 2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH))
                        .set(2, 2, 2, pricedTerminal)),
                save(cache, "depositor_terminal/redstone", new Schematic()
                        .set(2, 1, 2, pricedTerminal)
                        .set(2, 1, 3, Blocks.REDSTONE_LAMP.defaultBlockState())
                        .set(1, 1, 2, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.EAST))),
                save(cache, "atm", new Schematic()
                        .set(2, 1, 2, atm.setValue(DecorativeATMBlock.HALF, DoubleBlockHalf.LOWER))
                        .set(2, 2, 2, atm.setValue(DecorativeATMBlock.HALF, DoubleBlockHalf.UPPER))));
    }

    @SuppressWarnings({"UnstableApiUsage", "deprecation"})
    private CompletableFuture<?> save(CachedOutput cache, String name, Schematic schematic) {
        Path path = paths.file(ResourceLocation.fromNamespaceAndPath(CreateringtonCurrency.MODID, name), "nbt");
        return CompletableFuture.runAsync(() -> {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                HashingOutputStream hashing = new HashingOutputStream(Hashing.sha1(), bytes);
                NbtIo.writeCompressed(schematic.toTag(), hashing);
                cache.writeIfNeeded(path, bytes.toByteArray(), hashing.hash());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Util.backgroundExecutor());
    }

    @Override
    public @NotNull String getName() { return "CreateringtonCurrency Ponder Schematics"; }

    private static final class Schematic {
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

        Schematic() {
            for (int z = 0; z < PLATE_SIZE; z++) {
                for (int x = 0; x < PLATE_SIZE; x++) {
                    set(x, 0, z, ((x + z) % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.SNOW_BLOCK).defaultBlockState());
                }
            }
        }

        Schematic set(int x, int y, int z, BlockState state) {
            blocks.put(new BlockPos(x, y, z), state);
            return this;
        }

        CompoundTag toTag() {
            List<BlockState> palette = new ArrayList<>();
            ListTag blockList = new ListTag();
            int height = 0;
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                int state = palette.indexOf(entry.getValue());
                if (state < 0) {
                    state = palette.size();
                    palette.add(entry.getValue());
                }
                CompoundTag block = new CompoundTag();
                block.put("pos", ints(pos.getX(), pos.getY(), pos.getZ()));
                block.putInt("state", state);
                blockList.add(block);
                height = Math.max(height, pos.getY() + 1);
            }

            ListTag paletteList = new ListTag();
            palette.forEach(state -> paletteList.add(NbtUtils.writeBlockState(state)));

            CompoundTag tag = new CompoundTag();
            tag.put("size", ints(PLATE_SIZE, height, PLATE_SIZE));
            tag.put("palette", paletteList);
            tag.put("blocks", blockList);
            tag.put("entities", new ListTag());
            return NbtUtils.addCurrentDataVersion(tag);
        }

        private static ListTag ints(int... values) {
            ListTag list = new ListTag();
            for (int value : values) list.add(IntTag.valueOf(value));
            return list;
        }
    }
}
