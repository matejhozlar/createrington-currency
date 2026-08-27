package com.saunhardy.createringtoncurrency;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DISABLE_MONEY_COMMAND;
    public static final ModConfigSpec.BooleanValue DISABLE_PAY_COMMAND;
    public static final ModConfigSpec.BooleanValue DISABLE_CASH_COMMANDS;
    public static final ModConfigSpec.BooleanValue DISABLE_BALTOP_COMMAND;
    public static final ModConfigSpec.BooleanValue DISABLE_DAILY_COMMAND;
    public static final ModConfigSpec.BooleanValue DISABLE_LOTTERY_COMMANDS;
    public static final ModConfigSpec.BooleanValue DISABLE_VOTE_COMMAND;
    public static final ModConfigSpec.IntValue COMMAND_COOLDOWN_MS;

    public static final ModConfigSpec.ConfigValue<String> API_BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> JWT_SECRET;

    public static final ModConfigSpec.IntValue MOB_DAILY_LIMIT;
    public static final ModConfigSpec.ConfigValue<Double> ZOM_SPI_CRE_DROP;
    public static final ModConfigSpec.ConfigValue<Double> SKELETON_DROP;
    public static final ModConfigSpec.ConfigValue<Double> WITHER_SKELETON_DROP;
    public static final ModConfigSpec.ConfigValue<Double> BLAZE_DROP;

    public static final ModConfigSpec.IntValue LOTTERY_COOLDOWN_MINUTES;

    public static final ModConfigSpec.BooleanValue TRAIN_CRASH_REPORTING_ENABLED;

    public static final ModConfigSpec.IntValue DEPOSITOR_PULSE_TICKS;
    public static final ModConfigSpec.IntValue DEPOSITOR_MAX_PRICE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("General command and gameplay settings").push("general");

        DISABLE_MONEY_COMMAND = BUILDER
                .comment("If true, the /money command will NOT be registered")
                .define("disableMoneyCommand", false);

        DISABLE_PAY_COMMAND = BUILDER
                .comment("If true, the /pay command will NOT be registered")
                .define("disablePayCommand", false);

        DISABLE_CASH_COMMANDS = BUILDER
                .comment("If true, the /deposit and /withdraw commands will NOT be registered")
                .define("disableCashCommands", false);

        DISABLE_BALTOP_COMMAND = BUILDER
                .comment("If true, the /baltop command will NOT be registered")
                .define("disableBaltopCommand", false);

        DISABLE_DAILY_COMMAND = BUILDER
                .comment("If true, the /daily command will NOT be registered")
                .define("disableDailyCommand", false);

        DISABLE_LOTTERY_COMMANDS = BUILDER
                .comment("If true, the /lottery and /join commands will NOT be registered")
                .define("disableLotteryCommands", false);

        DISABLE_VOTE_COMMAND = BUILDER
                .comment("If true, the /vote command will NOT be registered")
                .define("disableVoteCommand", false);

        COMMAND_COOLDOWN_MS = BUILDER
                .comment("Global cooldown for all currency commands in milliseconds")
                .defineInRange("commandCooldownMs", 5000, 0, Integer.MAX_VALUE);

        BUILDER.pop();

        BUILDER.comment("Backend API connection settings").push("api");

        API_BASE_URL = BUILDER
                .comment("Base URL for the Createrington backend, eg. 'http://127.0.0.1:5000'")
                .define("apiBaseUrl", "http://127.0.0.1:5000");

        JWT_SECRET = BUILDER
                .comment("Shared HMAC secret used to sign short-lived mod JWTs. Must match the backend's auth secret. Requires server restart to take effect.")
                .define("jwtSecret", "CHANGE-ME-must-be-at-least-32-chars");

        BUILDER.pop();

        BUILDER.comment("Currency drops from mob kills").push("mobDrops");

        MOB_DAILY_LIMIT = BUILDER
                .comment("Maximum amount of currency a player can earn from mob kills per day (0 = no mob drops)")
                .defineInRange("mobDailyLimit", 1000, 0, Integer.MAX_VALUE);

        ZOM_SPI_CRE_DROP = BUILDER
                .comment("Drop chance of 1$ bills from zombies, spiders, creepers, eg. '50.0' = 50% chance")
                .define("zomSpiCreDrop", 2.0);

        SKELETON_DROP = BUILDER
                .comment("Drop chance of 1$ bills from skeletons, eg. '50.0' = 50% chance")
                .define("skeletonDrop", 3.0);

        WITHER_SKELETON_DROP = BUILDER
                .comment("Drop chance of 1$ bills from wither skeletons, eg. '50.0' = 50% chance")
                .define("witherSkeletonDrop", 3.5);

        BLAZE_DROP = BUILDER
                .comment("Drop chance of 1$ bills from wither blazes, eg. '50.0' = 50% chance")
                .define("blazeDrop", 3.5);

        BUILDER.pop();

        BUILDER.comment("Lottery command settings").push("lottery");

        LOTTERY_COOLDOWN_MINUTES = BUILDER
                .comment("Cooldown duration for /lottery in minutes, don't use this if you are not using integrated discord bots")
                .defineInRange("lotteryCooldownMinutes", 15, 0, 1440);

        BUILDER.pop();

        BUILDER.comment("Integrations with other mods").push("integrations");

        TRAIN_CRASH_REPORTING_ENABLED = BUILDER
                .comment("Enable reporting Create train crashes to the API (requires Create mod)")
                .define("trainCrashReportingEnabled", true);

        BUILDER.pop();

        BUILDER.comment("Depositor terminal settings").push("depositor");

        DEPOSITOR_PULSE_TICKS = BUILDER
                .comment("How long a depositor terminal stays powered after a successful payment, in ticks (20 = 1 second)")
                .defineInRange("depositorPulseTicks", 20, 1, 1200);

        DEPOSITOR_MAX_PRICE = BUILDER
                .comment("Highest price an owner can set on a depositor terminal")
                .defineInRange("depositorMaxPrice", 1000000, 1, Integer.MAX_VALUE);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private Config() {}
}
