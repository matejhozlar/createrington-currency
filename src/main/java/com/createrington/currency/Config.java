package com.createrington.currency;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = CreateringtonCurrency.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<String> API_BASE_URL = BUILDER
            .comment("Base URL for the currency API, eg. 'http://127.0.0.1:5000/")
            .define("apiBaseUrl", "http://127.0.0.1:5000/");

    private static final ModConfigSpec.ConfigValue<String> API_BALANCE_URL = BUILDER
            .comment("API URL for getting a balance of a player, eg. '/currency/balance'")
            .define("apiBalanceUrl", "api/currency/balance");

    private static final ModConfigSpec.ConfigValue<String> API_PAY_URL = BUILDER
            .comment("API URL for managing payments between players, eg. '/currency/pay'")
            .define("apiPayUrl", "api/currency/pay");

    private static final ModConfigSpec.ConfigValue<String> API_DEPOSIT_URL = BUILDER
            .comment("API URL for depositing money to player's account, eg. '/currency/deposit'")
            .define("apiDepositUrl", "api/currency/deposit");

    private static final ModConfigSpec.ConfigValue<String> API_WITHDRAW_URL = BUILDER
            .comment("API URL for withdrawing money from player's account, eg. '/currency/withdraw'")
            .define("apiWithdrawUrl","api/currency/withdraw");

    private static final ModConfigSpec.ConfigValue<String> API_TOP_URL = BUILDER
            .comment("API URL for /baltop command, eg. '/currency/top'.")
            .define("apiTopUrl", "api/currency/top");

    private static final ModConfigSpec.ConfigValue<Long> COMMAND_COOLDOWN_MS = BUILDER
            .comment("Global cooldown for all currency commands in milliseconds")
            .define("commandCooldownMs", 5000L);

    private static final ModConfigSpec.ConfigValue<String> API_LOGIN_URL = BUILDER
            .comment("API URL for a safe login to your back-end (should prevent Unauthorized access. Works on JWT verification every 10 mins, eg. '/currency/login")
            .define("apiLoginUrl", "api/currency/login");

    private static final ModConfigSpec.ConfigValue<String> API_MOB_LIMIT_URL = BUILDER
            .comment("API URL for mob limit earnings, eg. '/currency/mob-limit")
            .define("apiMobLimitUrl", "api/currency/mob-limit");

    private static final ModConfigSpec.ConfigValue<Double> ZOM_SPI_CRE_DROP = BUILDER
            .comment("Drop chance of 1$ bills from zombies, spiders, creepers, eg. '50.0' = 50% chance")
            .define("zomSpiCreDrop", 2.0);

    private static final ModConfigSpec.ConfigValue<Double> SKELETON_DROP = BUILDER
            .comment("Drop chance of 1$ bills from skeletons, eg. '50.0' = 50% chance")
            .define("skeletonDrop", 3.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static long commandCooldownMs;
    public static String apiBaseUrl;
    public static String apiBalanceUrl;
    public static String apiPayUrl;
    public static String apiDepositUrl;
    public static String apiWithdrawUrl;
    public static String apiTopUrl;
    public static String apiLoginUrl;
    public static String apiMobLimitUrl;
    public static double zomSpiCreDrop;
    public static double skeletonDrop;

    @SubscribeEvent
    public static void onReloading(final ModConfigEvent.Reloading event) {
        reloadConfig();
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        reloadConfig();
    }

    private static void reloadConfig() {
        commandCooldownMs = COMMAND_COOLDOWN_MS.get();
        apiBaseUrl = API_BASE_URL.get();
        apiBalanceUrl = API_BALANCE_URL.get();
        apiPayUrl = API_PAY_URL.get();
        apiDepositUrl = API_DEPOSIT_URL.get();
        apiWithdrawUrl = API_WITHDRAW_URL.get();
        apiTopUrl = API_TOP_URL.get();
        apiLoginUrl = API_LOGIN_URL.get();
        apiMobLimitUrl = API_MOB_LIMIT_URL.get();
        zomSpiCreDrop = ZOM_SPI_CRE_DROP.get();
        skeletonDrop = SKELETON_DROP.get();
    }
}