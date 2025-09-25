package com.saunhardy.createringtoncurrency.client;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ClientOnlyHooks {
    private ClientOnlyHooks() {}

    public static void registerScreens(RegisterMenuScreensEvent e) {
        e.register(CreateringtonCurrency.ATM_MENU.get(), ATMScreen::new);
    }
}
