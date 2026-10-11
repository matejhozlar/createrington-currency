package com.saunhardy.createringtoncurrency.client.ponder;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.block.DepositorTerminalBlock;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DepositorScenes {
    private static final int PULSE_TICKS = 20;
    private static final double LED_RING_RADIUS = 0.14;
    private static final Vec3 LED_RING_PARALLAX = new Vec3(-0.05, 0.06, -0.07);

    private DepositorScenes() {}

    public static void payment(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("depositor_terminal", "Taking payments with a Depositor Terminal");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos terminal = util.grid().at(2, 1, 2);
        Selection terminalOnly = util.select().position(terminal);
        Vec3 front = util.vector().blockSurface(terminal, Direction.NORTH);
        Vec3 top = util.vector().topOf(terminal);

        scene.world().showSection(terminalOnly, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(70)
                .text("A Depositor Terminal collects payments for its owner")
                .attachKeyFrame()
                .pointAt(front)
                .placeNearTarget();
        scene.idle(80);

        scene.overlay().showControls(top, Pointing.DOWN, 60).rightClick();
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Whoever places it is the owner. Right-clicking your own terminal opens its menu")
                .attachKeyFrame()
                .pointAt(front)
                .placeNearTarget();
        scene.idle(90);

        setLight(scene, terminal, DepositorTerminalBlock.Light.READY);
        scene.effects().indicateSuccess(terminal);
        highlightLed(scene, terminal, PonderPalette.GREEN, 90);
        scene.overlay().showText(90)
                .text("Set a price there: a number of bills of one denomination. The light turns green once it is set")
                .colored(PonderPalette.GREEN)
                .pointAt(ledOf(terminal))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(70)
                .text("Looking at a terminal shows customers its price and its owner")
                .attachKeyFrame()
                .pointAt(front)
                .placeNearTarget();
        scene.idle(80);

        scene.overlay().showControls(top, Pointing.DOWN, 70)
                .rightClick()
                .withItem(new ItemStack(CreateringtonCurrency.BILL_20.get()));
        scene.idle(10);
        scene.overlay().showText(80)
                .text("They pay in cash by right-clicking it with those bills in hand")
                .attachKeyFrame()
                .pointAt(front)
                .placeNearTarget();
        scene.effects().indicateSuccess(terminal);
        pulse(scene, terminalOnly);
        scene.idle(70);

        scene.overlay().showControls(top, Pointing.DOWN, 70)
                .rightClick()
                .withItem(new ItemStack(CreateringtonCurrency.BANK_CARD.get()));
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Right-clicking with a Bank Card pays the same price out of their bank account instead")
                .attachKeyFrame()
                .pointAt(front)
                .placeNearTarget();
        scene.effects().indicateSuccess(terminal);
        pulse(scene, terminalOnly);
        scene.idle(80);
    }

    public static void storage(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("depositor_terminal_storage", "Collecting the takings");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos hopper = util.grid().at(2, 1, 2);
        BlockPos chest = util.grid().at(3, 1, 2);
        BlockPos terminal = hopper.above();
        Vec3 front = util.vector().blockSurface(hopper, Direction.NORTH);

        ElementLink<WorldSectionElement> lowered =
                scene.world().showIndependentSection(util.select().position(terminal), Direction.DOWN);
        scene.world().moveSection(lowered, util.vector().of(0, -1, 0), 0);
        scene.idle(20);

        scene.overlay().showText(70)
                .text("Paid bills stay inside the terminal, which holds up to nine stacks")
                .attachKeyFrame()
                .pointAt(front)
                .placeNearTarget();
        scene.idle(80);

        setLight(scene, terminal, DepositorTerminalBlock.Light.FULL);
        highlightLed(scene, hopper, PonderPalette.RED, 90);
        scene.overlay().showText(90)
                .text("When the next payment would not fit, the light turns red and the terminal stops taking payments")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .pointAt(ledOf(hopper))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showControls(util.vector().topOf(hopper), Pointing.DOWN, 60).rightClick();
        scene.idle(10);
        scene.overlay().showText(70)
                .text("As the owner, empty it with Take all in its menu")
                .attachKeyFrame()
                .pointAt(front)
                .placeNearTarget();
        scene.idle(30);
        setLight(scene, terminal, DepositorTerminalBlock.Light.READY);
        scene.effects().indicateSuccess(hopper);
        highlightLed(scene, hopper, PonderPalette.GREEN, 40);
        scene.idle(50);

        scene.world().moveSection(lowered, util.vector().of(0, 1, 0), 10);
        scene.idle(15);
        scene.world().showSection(util.select().position(hopper), Direction.SOUTH);
        scene.idle(5);
        scene.world().showSection(util.select().position(chest), Direction.WEST);
        scene.idle(20);

        scene.overlay().showOutlineWithText(util.select().position(hopper), 90)
                .text("Hoppers and funnels can pull the bills out as well, so the terminal never fills up")
                .attachKeyFrame()
                .colored(PonderPalette.OUTPUT)
                .pointAt(front)
                .placeNearTarget();
        scene.idle(20);
        scene.overlay().showControls(util.vector().topOf(chest), Pointing.DOWN, 60)
                .withItem(new ItemStack(CreateringtonCurrency.BILL_20.get()));
        scene.idle(80);
    }

    public static void redstone(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("depositor_terminal_redstone", "Redstone signals from a Depositor Terminal");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos terminal = util.grid().at(2, 1, 2);
        BlockPos lamp = util.grid().at(2, 1, 3);
        BlockPos comparator = util.grid().at(1, 1, 2);
        Selection circuit = util.select().fromTo(terminal, lamp);
        Vec3 top = util.vector().topOf(terminal);
        Vec3 lampTop = util.vector().topOf(lamp);

        scene.world().showSection(util.select().position(terminal), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(lamp), Direction.NORTH);
        scene.idle(20);

        scene.overlay().showControls(top, Pointing.DOWN, 50)
                .rightClick()
                .withItem(new ItemStack(CreateringtonCurrency.BILL_20.get()));
        scene.idle(10);
        scene.overlay().showOutlineWithText(util.select().position(lamp), 80)
                .text("Every completed payment sends a redstone pulse into the block behind the terminal")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .pointAt(lampTop)
                .placeNearTarget();
        scene.effects().indicateRedstone(lamp);
        pulse(scene, circuit);
        scene.idle(70);

        scene.overlay().showControls(top, Pointing.DOWN, 50)
                .rightClick()
                .withItem(new ItemStack(CreateringtonCurrency.BANK_CARD.get()));
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Card payments pulse it too: enough to open a door or fire a dispenser")
                .pointAt(lampTop)
                .placeNearTarget();
        scene.effects().indicateRedstone(lamp);
        pulse(scene, circuit);
        scene.idle(70);

        scene.world().showSection(util.select().position(comparator), Direction.EAST);
        scene.idle(20);
        scene.world().toggleRedstonePower(util.select().position(comparator));
        scene.overlay().showText(80)
                .text("A comparator reads how full the terminal's storage is")
                .attachKeyFrame()
                .pointAt(util.vector().blockSurface(comparator, Direction.DOWN, 2 / 16f))
                .placeNearTarget();
        scene.idle(90);
    }

    private static void pulse(SceneBuilder scene, Selection powered) {
        scene.world().toggleRedstonePower(powered);
        scene.idle(PULSE_TICKS);
        scene.world().toggleRedstonePower(powered);
    }

    private static void setLight(SceneBuilder scene, BlockPos terminal, DepositorTerminalBlock.Light light) {
        scene.world().modifyBlock(terminal, state -> state.setValue(DepositorTerminalBlock.LIGHT, light), false);
    }

    private static void highlightLed(SceneBuilder scene, BlockPos northFacingTerminal, PonderPalette colour, int duration) {
        Vec3 centre = ledOf(northFacingTerminal).add(LED_RING_PARALLAX);
        AABB ring = new AABB(centre, centre).inflate(LED_RING_RADIUS, LED_RING_RADIUS, 0);
        scene.overlay().chaseBoundingBoxOutline(colour, northFacingTerminal, ring, duration);
    }

    private static Vec3 ledOf(BlockPos northFacingTerminal) {
        return Vec3.atLowerCornerOf(northFacingTerminal).add(0.26, 0.35, 0.06);
    }
}
