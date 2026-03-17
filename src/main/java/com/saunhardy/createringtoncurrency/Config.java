package com.saunhardy.createringtoncurrency;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DISABLE_CASH_COMMANDS = BUILDER
            .comment("If true, the /deposit and /withdraw commands will NOT be registered")
            .define("disableCashCommands", false);

    public static final ModConfigSpec.ConfigValue<String> API_BASE_URL = BUILDER
            .comment("Base URL for the currency API, eg. 'http://127.0.0.1:5000/")
            .define("apiBaseUrl", "http://127.0.0.1:5000/");

    public static final ModConfigSpec.ConfigValue<String> API_BALANCE_URL = BUILDER
            .comment("API URL for getting a balance of a player, eg. '/currency/balance'")
            .define("apiBalanceUrl", "api/currency/balance");

    public static final ModConfigSpec.ConfigValue<String> API_PAY_URL = BUILDER
            .comment("API URL for managing payments between players, eg. '/currency/pay'")
            .define("apiPayUrl", "api/currency/pay");

    public static final ModConfigSpec.ConfigValue<String> API_DEPOSIT_URL = BUILDER
            .comment("API URL for depositing money to player's account, eg. '/currency/deposit'")
            .define("apiDepositUrl", "api/currency/deposit");

    public static final ModConfigSpec.ConfigValue<String> API_WITHDRAW_URL = BUILDER
            .comment("API URL for withdrawing money from player's account, eg. '/currency/withdraw'")
            .define("apiWithdrawUrl","api/currency/withdraw");

    public static final ModConfigSpec.ConfigValue<String> API_TOP_URL = BUILDER
            .comment("API URL for /baltop command, eg. '/currency/top'.")
            .define("apiTopUrl", "api/currency/top");

    public static final ModConfigSpec.ConfigValue<Long> COMMAND_COOLDOWN_MS = BUILDER
            .comment("Global cooldown for all currency commands in milliseconds")
            .define("commandCooldownMs", 5000L);

    public static final ModConfigSpec.IntValue API_TIMEOUT_MS = BUILDER
            .comment("HTTP connect and read timeout for all API calls in milliseconds")
            .defineInRange("apiTimeoutMs", 5000, 1000, 30000);

    public static final ModConfigSpec.ConfigValue<String> API_LOGIN_URL = BUILDER
            .comment("API URL for a safe login to your back-end (should prevent Unauthorized access. Works on JWT verification every 10 mins, eg. '/currency/login")
            .define("apiLoginUrl", "api/currency/login");

    public static final ModConfigSpec.IntValue MOB_DAILY_LIMIT = BUILDER
            .comment("Maximum amount of currency a player can earn from mob kills per day (0 = no mob drops)")
            .defineInRange("mobDailyLimit", 1000, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> API_DAILY_URL = BUILDER
            .comment("API URL for daily reward, eg. '/currency/daily")
            .define("apiDailyUrl", "api/currency/daily");

    public static final ModConfigSpec.ConfigValue<String> API_START_LOTTERY_URL = BUILDER
            .comment("API URL for starting a lottery, eg. '/currency/lottery/start', don't use this if you are not using integrated discord bots")
            .define("apiStartLotteryUrl", "api/currency/lottery/start");

    public static final ModConfigSpec.ConfigValue<String> API_JOIN_LOTTERY_URL = BUILDER
            .comment("API URL for joining a lottery, eg. '/currency/lottery/join', don't use this if you are not using integrated discord bots")
            .define("apiJoinLotteryUrl", "api/currency/lottery/join");

    public static final ModConfigSpec.ConfigValue<String> API_HISTORY_URL = BUILDER
            .comment("API URL for transaction history, eg. '/currency/history'")
            .define("apiHistoryUrl", "api/currency/history");

    public static final ModConfigSpec.ConfigValue<Double> ZOM_SPI_CRE_DROP = BUILDER
            .comment("Drop chance of 1$ bills from zombies, spiders, creepers, eg. '50.0' = 50% chance")
            .define("zomSpiCreDrop", 2.0);

    public static final ModConfigSpec.ConfigValue<Double> SKELETON_DROP = BUILDER
            .comment("Drop chance of 1$ bills from skeletons, eg. '50.0' = 50% chance")
            .define("skeletonDrop", 3.0);

    public static final ModConfigSpec.ConfigValue<Double> WITHER_SKELETON_DROP = BUILDER
            .comment("Drop chance of 1$ bills from wither skeletons, eg. '50.0' = 50% chance")
            .define("witherSkeletonDrop", 3.5);

    public static final ModConfigSpec.ConfigValue<Double> BLAZE_DROP = BUILDER
            .comment("Drop chance of 1$ bills from wither blazes, eg. '50.0' = 50% chance")
            .define("blazeDrop", 3.5);

    public static final ModConfigSpec.IntValue LOTTERY_COOLDOWN_MINUTES = BUILDER
            .comment("Cooldown duration for /lottery in minutes, don't use this if you are not using integrated discord bots")
            .defineInRange("lotteryCooldownMinutes", 15, 0, 1440);

    public static final ModConfigSpec.BooleanValue TRAIN_CRASH_REPORTING_ENABLED = BUILDER
            .comment("Enable reporting Create train crashes to the API (requires Create mod)")
            .define("trainCrashReportingEnabled", true);

    public static final ModConfigSpec.ConfigValue<String> API_TRAIN_CRASH_URL = BUILDER
            .comment("API URL for reporting train crashes, eg. 'api/trains/crash'")
            .define("apiTrainCrashUrl", "api/trains/crash");

    public static final ModConfigSpec SPEC = BUILDER.build();
}