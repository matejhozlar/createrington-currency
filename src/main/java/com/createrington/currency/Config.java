package com.createrington.currency;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = CreateringtonCurrency.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<String> API_BASE_URL = BUILDER
            .comment("Base URL for the currency API. Must end with a slash '/'")
            .define("apiBaseUrl", "http://127.0.0.1:5000/");

    private static final ModConfigSpec.ConfigValue<String> API_BALANCE_URL = BUILDER
            .comment("API URL for getting a balance of a player, eg. 'currency/balance'. Cannot start with a slash '/'")
            .define("apiBalanceUrl", "api/currency/balance");

    private static final ModConfigSpec.ConfigValue<String> API_PAY_URL = BUILDER
            .comment("API URL for managing payments between players, eg. 'currency/pay'. Cannot start with a slash'/'")
            .define("apiPayUrl", "api/currency/pay");

    private static final ModConfigSpec.ConfigValue<String> API_DEPOSIT_URL = BUILDER
            .comment("API URL for depositing money to player's account, eg. 'currency/deposit'. Cannot start with a slash '/'")
            .define("apiDepositUrl", "api/currency/deposit");

    private static final ModConfigSpec.ConfigValue<String> API_WITHDRAW_URL = BUILDER
            .comment("API URL for withdrawing money from player's account, eg. 'currency/withdraw'. Cannot start with a slash '/'")
            .define("apiWithdrawUrl","api/currency/withdraw");

    private static final ModConfigSpec.ConfigValue<String> API_TOP_URL = BUILDER
            .comment("API URL for /baltop command, eg. 'currency/top'. Cannot start with a slash '/'")
            .define("apiTopUrl", "api/currency/top");

    private static final ModConfigSpec.ConfigValue<Long> COMMAND_COOLDOWN_MS = BUILDER
            .comment("Global cooldown for all currency commands in milliseconds")
            .define("commandCooldownMs", 5000L);

    private static final ModConfigSpec.ConfigValue<String> API_KEY = BUILDER
            .comment("Secret API key to authorize requests to the backend")
            .define("apiKey", "changeme-secret-key");

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static long commandCooldownMs;
    public static String apiBaseUrl;
    public static String apiBalanceUrl;
    public static String apiPayUrl;
    public static String apiDepositUrl;
    public static String apiWithdrawUrl;
    public static String apiTopUrl;

    // api key
    public static String apiKey;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        commandCooldownMs = COMMAND_COOLDOWN_MS.get();
        apiBaseUrl = API_BASE_URL.get();
        apiBalanceUrl = API_BALANCE_URL.get();
        apiPayUrl = API_PAY_URL.get();
        apiDepositUrl = API_DEPOSIT_URL.get();
        apiWithdrawUrl = API_WITHDRAW_URL.get();
        apiTopUrl = API_TOP_URL.get();
        apiKey = API_KEY.get();
    }
}