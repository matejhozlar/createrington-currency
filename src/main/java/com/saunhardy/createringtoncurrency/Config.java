package com.saunhardy.createringtoncurrency;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<String> API_BASE_URL = BUILDER
            .comment("Base URL for the currency API, eg. 'http://127.0.0.1:5000/'")
            .define("apiBaseUrl", "http://127.0.0.1:5000/");

    public static final ForgeConfigSpec.ConfigValue<String> API_BALANCE_URL = BUILDER
            .comment("API URL for getting a balance of a player, eg. '/currency/balance'")
            .define("apiBalanceUrl", "api/currency/balance");

    public static final ForgeConfigSpec.ConfigValue<String> API_PAY_URL = BUILDER
            .comment("API URL for managing payments between players, eg. '/currency/pay'")
            .define("apiPayUrl", "api/currency/pay");

    public static final ForgeConfigSpec.ConfigValue<String> API_DEPOSIT_URL = BUILDER
            .comment("API URL for depositing money to player's account, eg. '/currency/deposit'")
            .define("apiDepositUrl", "api/currency/deposit");

    public static final ForgeConfigSpec.ConfigValue<String> API_WITHDRAW_URL = BUILDER
            .comment("API URL for withdrawing money from player's account, eg. '/currency/withdraw'")
            .define("apiWithdrawUrl","api/currency/withdraw");

    public static final ForgeConfigSpec.ConfigValue<String> API_TOP_URL = BUILDER
            .comment("API URL for /baltop command, eg. '/currency/top'.")
            .define("apiTopUrl", "api/currency/top");

    public static final ForgeConfigSpec.ConfigValue<Long> COMMAND_COOLDOWN_MS = BUILDER
            .comment("Global cooldown for all currency commands in milliseconds")
            .define("commandCooldownMs", 5000L);

    public static final ForgeConfigSpec.ConfigValue<String> API_LOGIN_URL = BUILDER
            .comment("API URL for a safe login to your back-end (JWT verification), eg. '/currency/login'")
            .define("apiLoginUrl", "api/currency/login");

    public static final ForgeConfigSpec.ConfigValue<String> API_MOB_LIMIT_URL = BUILDER
            .comment("API URL for mob limit earnings, eg. '/currency/mob-limit'")
            .define("apiMobLimitUrl", "api/currency/mob-limit");

    public static final ForgeConfigSpec.ConfigValue<String> API_DAILY_URL = BUILDER
            .comment("API URL for daily reward, eg. '/currency/daily'")
            .define("apiDailyUrl", "api/currency/daily");

    public static final ForgeConfigSpec.ConfigValue<String> API_START_LOTTERY_URL = BUILDER
            .comment("API URL for starting a lottery, eg. '/currency/lottery/start' (for integrated Discord bots)")
            .define("apiStartLotteryUrl", "api/currency/lottery/start");

    public static final ForgeConfigSpec.ConfigValue<String> API_JOIN_LOTTERY_URL = BUILDER
            .comment("API URL for joining a lottery, eg. '/currency/lottery/join' (for integrated Discord bots)")
            .define("apiJoinLotteryUrl", "api/currency/lottery/join");

    public static final ForgeConfigSpec.ConfigValue<String> API_START_VOTE_URL = BUILDER
            .comment("API URL for starting a voting process, eg. '/currency/vote/start' (for integrated Discord bots)")
            .define("apiStartVoteUrl", "api/currency/vote/start");

    public static final ForgeConfigSpec.ConfigValue<Double> ZOM_SPI_CRE_DROP = BUILDER
            .comment("Drop chance of $1 bills from zombies/spiders/creepers, e.g. 50.0 = 50%")
            .define("zomSpiCreDrop", 2.0);

    public static final ForgeConfigSpec.ConfigValue<Double> SKELETON_DROP = BUILDER
            .comment("Drop chance of $1 bills from skeletons, e.g. 50.0 = 50%")
            .define("skeletonDrop", 3.0);

    public static final ForgeConfigSpec.IntValue LOTTERY_COOLDOWN_MINUTES = BUILDER
            .comment("Cooldown for /lottery in minutes (for integrated Discord bots)")
            .defineInRange("lotteryCooldownMinutes", 15, 0, 1440);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
