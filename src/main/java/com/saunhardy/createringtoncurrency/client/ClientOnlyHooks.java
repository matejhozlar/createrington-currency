package com.saunhardy.createringtoncurrency.client;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.block.DepositorTerminalBlock;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ClientOnlyHooks {
    private ClientOnlyHooks() {}

    public static void registerScreens(RegisterMenuScreensEvent e) {
        e.register(CreateringtonCurrency.DEPOSITOR_MENU.get(), DepositorScreen::new);
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block e) {
        e.register((state, level, pos, tintIndex) -> {
            if (tintIndex != DepositorTerminalBlock.LED_TINT_INDEX) return -1;
            return state.getValue(DepositorTerminalBlock.POWERED)
                    ? DepositorTerminalBlock.LED_FLASH_COLOR
                    : state.getValue(DepositorTerminalBlock.LIGHT).color();
        }, CreateringtonCurrency.DEPOSITOR_TERMINAL_BLOCK.get());
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item e) {
        e.register((stack, tintIndex) -> tintIndex == DepositorTerminalBlock.LED_TINT_INDEX
                        ? DepositorTerminalBlock.Light.READY.color()
                        : -1,
                CreateringtonCurrency.DEPOSITOR_TERMINAL_ITEM.get());
    }
}
