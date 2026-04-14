package com.saunhardy.createringtoncurrency;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DISABLE_CASH_COMMANDS = BUILDER
            .comment("If true, the /deposit and /withdraw commands will NOT be registered")
            .define("disableCashCommands", false);

    public static final ModConfigSpec.ConfigValue<String> API_BASE_URL = BUILDER
            .comment("Base URL for the Createrington backend, eg. 'http://127.0.0.1:5000'")
            .define("apiBaseUrl", "http://127.0.0.1:5000");

    public static final ModConfigSpec.ConfigValue<Long> COMMAND_COOLDOWN_MS = BUILDER
            .comment("Global cooldown for all currency commands in milliseconds")
            .define("commandCooldownMs", 5000L);

    public static final ModConfigSpec.IntValue MOB_DAILY_LIMIT = BUILDER
            .comment("Maximum amount of currency a player can earn from mob kills per day (0 = no mob drops)")
            .defineInRange("mobDailyLimit", 1000, 0, Integer.MAX_VALUE);

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

    public static final ModConfigSpec SPEC = BUILDER.build();
}
