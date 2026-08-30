package com.saunhardy.createringtoncurrency.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.block.DepositorTerminalBlock;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ClientOnlyHooks {
    private ClientOnlyHooks() {}

    private static final String KEY_CATEGORY = "key.categories." + CreateringtonCurrency.MODID;

    public static final KeyMapping VOTE_YES = new KeyMapping(
            "key." + CreateringtonCurrency.MODID + ".vote_yes",
            InputConstants.UNKNOWN.getValue(),
            KEY_CATEGORY
    );

    public static final KeyMapping VOTE_NO = new KeyMapping(
            "key." + CreateringtonCurrency.MODID + ".vote_no",
            InputConstants.UNKNOWN.getValue(),
            KEY_CATEGORY
    );

    public static void registerScreens(RegisterMenuScreensEvent e) {
        e.register(CreateringtonCurrency.ATM_MENU.get(), ATMScreen::new);
        e.register(CreateringtonCurrency.DEPOSITOR_MENU.get(), DepositorScreen::new);
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent e) {
        e.register(VOTE_YES);
        e.register(VOTE_NO);
    }

    /** Depositor LED: a brass flash while the redstone pulse runs, otherwise the colour of the {@code LIGHT} state. */
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

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        while (VOTE_YES.consumeClick()) {
            player.connection.sendCommand("vote yes");
        }
        while (VOTE_NO.consumeClick()) {
            player.connection.sendCommand("vote no");
        }
    }
}
