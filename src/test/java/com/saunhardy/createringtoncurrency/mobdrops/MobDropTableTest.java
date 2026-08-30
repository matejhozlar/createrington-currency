package com.saunhardy.createringtoncurrency.mobdrops;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MobDropTableTest {

    @Test
    void parsesAnEntityId() {
        MobDropTable.Entry entry = MobDropTable.parse("minecraft:wither_skeleton=5:3.5");

        assertNotNull(entry);
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "wither_skeleton"), entry.id());
        assertNull(entry.tag());
        assertEquals(5, entry.denomination());
        assertEquals(3.5, entry.chance());
    }

    @Test
    void parsesAnEntityTypeTag() {
        MobDropTable.Entry entry = MobDropTable.parse("#minecraft:skeletons=1:3");

        assertNotNull(entry);
        assertNotNull(entry.tag());
        assertEquals(Registries.ENTITY_TYPE, entry.tag().registry());
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "skeletons"), entry.tag().location());
        assertEquals(entry.id(), entry.tag().location());
        assertEquals(1, entry.denomination());
        assertEquals(3.0, entry.chance());
    }

    @Test
    void defaultsTheNamespaceToMinecraft() {
        MobDropTable.Entry entity = MobDropTable.parse("zombie=1:2.0");
        MobDropTable.Entry tag = MobDropTable.parse("#undead=1:2.0");

        assertNotNull(entity);
        assertNotNull(tag);
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"), entity.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "undead"), tag.id());
    }

    @Test
    void acceptsModdedIdsAndSurroundingWhitespace() {
        MobDropTable.Entry entry = MobDropTable.parse("  create:some_mob/variant-2=1000:100  ");

        assertNotNull(entry);
        assertEquals(ResourceLocation.fromNamespaceAndPath("create", "some_mob/variant-2"), entry.id());
        assertEquals(1000, entry.denomination());
        assertEquals(100.0, entry.chance());
    }

    @Test
    void acceptsEveryDenominationAndTheChanceBounds() {
        for (int denomination : new int[]{1, 5, 10, 20, 50, 100, 500, 1000}) {
            assertNotNull(MobDropTable.parse("minecraft:zombie=" + denomination + ":0"), "denomination " + denomination);
        }
        assertEquals(0.0, MobDropTable.parse("minecraft:zombie=1:0.0").chance());
        assertEquals(100.0, MobDropTable.parse("minecraft:zombie=1:100").chance());
    }

    @Test
    void rejectsAmountsThatAreNotDenominations() {
        for (String amount : new String[]{"0", "2", "3", "25", "200", "10000"}) {
            assertNull(MobDropTable.parse("minecraft:zombie=" + amount + ":2.0"), "amount " + amount);
        }
    }

    @Test
    void rejectsChancesOutsideZeroToHundred() {
        assertNull(MobDropTable.parse("minecraft:zombie=1:100.5"));
        assertNull(MobDropTable.parse("minecraft:zombie=1:-1"));
        assertNull(MobDropTable.parse("minecraft:zombie=1:1e2"));
    }

    @Test
    void rejectsMalformedEntries() {
        List<String> garbage = List.of(
                "",
                "zombie",
                "zombie=1",
                "zombie=1:",
                "=1:2.0",
                "#=1:2.0",
                "minecraft:Zombie=1:2.0",
                "minecraft:zombie 1:2.0",
                "minecraft:zombie=1:2.0:3",
                "minecraft:zombie=x:2.0",
                "minecraft:zombie=1:.5",
                "minecraft:zombie=1:2,0",
                "minecraft:zombie=1:2.0%",
                "minecraft:zombie=-1:2.0",
                "minecraft:zom bie=1:2.0",
                "mine craft:zombie=1:2.0",
                "#minecraft:skeletons==1:2.0"
        );
        for (String raw : garbage) {
            assertNull(MobDropTable.parse(raw), "'" + raw + "' should be rejected");
        }
    }
}
