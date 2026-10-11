package com.saunhardy.createringtoncurrency.client.ponder;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.block.DecorativeATMBlock;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredBlock;

public final class ATMScenes {
    private static final int COLOUR_TICKS = 12;

    private ATMScenes() {}

    public static void banking(SceneBuilder scene, SceneBuildingUtil util, DecorativeATMBlock variant) {
        scene.title("atm", "Banking at an ATM");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos lower = util.grid().at(2, 1, 2);
        BlockPos upper = lower.above();
        Selection atm = util.select().fromTo(lower, upper);
        Vec3 screen = util.vector().of(2.5, 2.45, 2.4);
        Vec3 slot = util.vector().of(2.5, 1.7, 2.1);
        Vec3 top = util.vector().topOf(upper);

        recolour(scene, atm, variant);
        scene.world().showSection(atm, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(70)
                .text("An ATM connects the bills you carry to your bank account")
                .attachKeyFrame()
                .pointAt(screen)
                .placeNearTarget();
        scene.idle(80);

        scene.overlay().showControls(top, Pointing.DOWN, 60).rightClick();
        scene.idle(10);
        scene.overlay().showText(70)
                .text("Right-click it to see your balance and choose what to do")
                .attachKeyFrame()
                .pointAt(screen)
                .placeNearTarget();
        scene.idle(80);

        ItemStack bill = new ItemStack(CreateringtonCurrency.BILL_100.get());
        scene.overlay().showControls(slot, Pointing.RIGHT, 80).withItem(bill);
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Deposit pays bills into your account: everything you carry, or an exact amount")
                .attachKeyFrame()
                .colored(PonderPalette.INPUT)
                .pointAt(screen)
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(slot, Pointing.RIGHT, 80).withItem(bill);
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Withdraw hands out bills from your balance, picked bill by bill or as one sum")
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .pointAt(screen)
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(60)
                .text("History lists your recent transactions")
                .pointAt(screen)
                .placeNearTarget();
        scene.idle(70);

        int others = CreateringtonCurrency.DECORATIVE_ATMS.size() - 1;
        scene.overlay().showText(others * COLOUR_TICKS)
                .text("ATMs come in eight colours that all work the same")
                .attachKeyFrame()
                .pointAt(screen)
                .placeNearTarget();
        for (DeferredBlock<DecorativeATMBlock> other : CreateringtonCurrency.DECORATIVE_ATMS) {
            if (other.get() == variant) continue;
            recolour(scene, atm, other.get());
            scene.idle(COLOUR_TICKS);
        }
        recolour(scene, atm, variant);
        scene.idle(20);
    }

    private static void recolour(SceneBuilder scene, Selection atm, Block variant) {
        scene.world().modifyBlocks(atm, variant::withPropertiesOf, false);
    }
}
