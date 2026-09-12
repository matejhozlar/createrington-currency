package com.saunhardy.createringtoncurrency.audit;

import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtCashTest {

    @Test
    void mapsBillIdsToDenominations() {
        assertEquals(0, NbtCash.denominationIndex("createringtoncurrency:bill_1000"));
        assertEquals(7, NbtCash.denominationIndex("createringtoncurrency:bill_1"));
        assertEquals(-1, NbtCash.denominationIndex("createringtoncurrency:bill_7"));
        assertEquals(-1, NbtCash.denominationIndex("createringtoncurrency:bank_card"));
        assertEquals(-1, NbtCash.denominationIndex("minecraft:paper"));
        assertEquals(-1, NbtCash.denominationIndex(null));
    }

    @Test
    void countsAPlainChest() {
        CompoundTag chest = new CompoundTag();
        chest.put("Items", items(bill(100, 3), stack("minecraft:cobblestone", 64), bill(1, 7)));

        int[] counts = Bills.none();
        NbtCash.countHolder(chest, counts);

        assertEquals(3, counts[Bills.indexOfDenomination(100)]);
        assertEquals(7, counts[Bills.indexOfDenomination(1)]);
        assertEquals(3 * 100 + 7, Bills.value(counts));
    }

    @Test
    void treatsAMissingCountAsOne() {
        CompoundTag bill = new CompoundTag();
        bill.putString("id", NbtCash.BILL_PREFIX + "500");

        int[] counts = Bills.none();
        NbtCash.countStack(bill, counts);

        assertEquals(500, Bills.value(counts));
    }

    @Test
    void looksInsideShulkerBoxes() {
        CompoundTag shulker = stack("minecraft:shulker_box", 1);
        CompoundTag components = new CompoundTag();
        ListTag slots = new ListTag();
        CompoundTag slot = new CompoundTag();
        slot.putInt("slot", 0);
        slot.put("item", bill(1000, 2));
        slots.add(slot);
        components.put("minecraft:container", slots);
        shulker.put("components", components);

        CompoundTag chest = new CompoundTag();
        chest.put("Items", items(shulker, bill(50, 1)));

        int[] counts = Bills.none();
        NbtCash.countHolder(chest, counts);

        assertEquals(2 * 1000 + 50, Bills.value(counts));
    }

    @Test
    void looksInsideBundles() {
        CompoundTag bundle = stack("minecraft:bundle", 1);
        CompoundTag components = new CompoundTag();
        components.put("minecraft:bundle_contents", items(bill(20, 4)));
        bundle.put("components", components);

        int[] counts = Bills.none();
        NbtCash.countStack(bundle, counts);

        assertEquals(80, Bills.value(counts));
    }

    @Test
    void looksInsideBlockEntityData() {
        CompoundTag barrelItem = stack("minecraft:barrel", 1);
        CompoundTag components = new CompoundTag();
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.put("Items", items(bill(10, 6)));
        components.put("minecraft:block_entity_data", blockEntity);
        barrelItem.put("components", components);

        int[] counts = Bills.none();
        NbtCash.countStack(barrelItem, counts);

        assertEquals(60, Bills.value(counts));
    }

    @Test
    void countsPlayerInventoryAndEnderChest() {
        CompoundTag player = new CompoundTag();
        player.put("Inventory", items(bill(100, 1)));
        player.put("EnderItems", items(bill(1000, 1)));

        int[] counts = Bills.none();
        NbtCash.countHolder(player, counts);

        assertEquals(1100, Bills.value(counts));
    }

    @Test
    void countsDroppedItemEntities() {
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:item");
        item.put("Item", bill(500, 2));

        int[] counts = Bills.none();
        NbtCash.countHolder(item, counts);

        assertEquals(1000, Bills.value(counts));
    }

    @Test
    void countsPassengerInventories() {
        CompoundTag minecart = new CompoundTag();
        minecart.put("Items", items(bill(5, 1)));

        CompoundTag boat = new CompoundTag();
        ListTag passengers = new ListTag();
        passengers.add(minecart);
        boat.put("Passengers", passengers);

        int[] counts = Bills.none();
        NbtCash.countHolder(boat, counts);

        assertEquals(5, Bills.value(counts));
    }

    @Test
    void ignoresEverythingThatIsNotABill() {
        CompoundTag chest = new CompoundTag();
        chest.put("Items", items(stack("minecraft:diamond", 64), stack("createringtoncurrency:bank_card", 1)));

        int[] counts = Bills.none();
        NbtCash.countHolder(chest, counts);

        assertArrayEquals(Bills.none(), counts);
        assertTrue(Bills.isEmpty(counts));
    }

    @Test
    void stopsDescendingBeforeItRunsOutOfStack() {
        CompoundTag nested = bill(1, 1);
        for (int i = 0; i < 64; i++) {
            CompoundTag shulker = stack("minecraft:shulker_box", 1);
            CompoundTag components = new CompoundTag();
            ListTag slots = new ListTag();
            CompoundTag slot = new CompoundTag();
            slot.put("item", nested);
            slots.add(slot);
            components.put("minecraft:container", slots);
            shulker.put("components", components);
            nested = shulker;
        }

        int[] counts = Bills.none();
        NbtCash.countStack(nested, counts);

        assertTrue(Bills.isEmpty(counts));
    }

    private static CompoundTag bill(int denomination, int count) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", NbtCash.BILL_PREFIX + denomination);
        tag.putInt("count", count);
        return tag;
    }

    private static CompoundTag stack(String id, int count) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("count", count);
        return tag;
    }

    private static ListTag items(CompoundTag... stacks) {
        ListTag list = new ListTag();
        for (CompoundTag stack : stacks) list.add(stack);
        return list;
    }
}
