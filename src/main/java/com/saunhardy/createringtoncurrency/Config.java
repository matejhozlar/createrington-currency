package com.saunhardy.createringtoncurrency;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DISABLE_MONEY_COMMAND;
    public static final ModConfigSpec.BooleanValue DISABLE_PAY_COMMAND;
    public static final ModConfigSpec.BooleanValue DISABLE_CASH_COMMANDS;
    public static final ModConfigSpec.BooleanValue DISABLE_BALTOP_COMMAND;
    public static final ModConfigSpec.BooleanValue DISABLE_DAILY_COMMAND;
    public static final ModConfigSpec.BooleanValue DISABLE_LOTTERY_COMMANDS;
    public static final ModConfigSpec.BooleanValue DISABLE_VOTE_COMMAND;
    public static final ModConfigSpec.BooleanValue DISABLE_ADMIN_MODE_COMMAND;
    public static final ModConfigSpec.BooleanValue DISABLE_BANK_CARD_USE;
    public static final ModConfigSpec.IntValue COMMAND_COOLDOWN_MS;

    public static final ModConfigSpec.ConfigValue<String> API_BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> JWT_SECRET;

    public static final ModConfigSpec.IntValue MOB_DAILY_LIMIT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOB_DROPS;
    public static final ModConfigSpec.ConfigValue<List<? extends Number>> CAPITALIST_GREED_BONUS;

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

        DISABLE_ADMIN_MODE_COMMAND = BUILDER
                .comment("If true, the /createringtoncurrency admin-mode command will NOT be registered; operators then always use depositor terminals as ordinary customers")
                .define("disableAdminModeCommand", false);

        DISABLE_BANK_CARD_USE = BUILDER
                .comment("If true, right-clicking a Bank Card will not show the balance or recent transactions")
                .define("disableBankCardUse", false);

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

        MOB_DROPS = BUILDER
                .comment("Bills dropped when a player kills a mob. One entry per line: '<entity>=<denomination>:<chance>'",
                        "<entity> is an entity id such as 'minecraft:zombie' or an entity type tag such as '#minecraft:skeletons'",
                        "<denomination> is one of 1, 5, 10, 20, 50, 100, 500, 1000; <chance> is a percentage, eg. '2.5' = 2.5% chance",
                        "Every matching entry is rolled on its own, so a mob can drop several bills from one kill")
                .defineListAllowEmpty("drops", List.of(
                                "minecraft:zombie=1:2.0",
                                "minecraft:spider=1:2.0",
                                "minecraft:creeper=1:2.0",
                                "minecraft:skeleton=1:3.0",
                                "minecraft:skeleton=5:2.0",
                                "minecraft:wither_skeleton=1:3.5",
                                "minecraft:wither_skeleton=5:2.0",
                                "minecraft:blaze=1:3.5",
                                "minecraft:blaze=5:2.0"),
                        () -> "minecraft:zombie=1:2.0", value -> value instanceof String);

        CAPITALIST_GREED_BONUS = BUILDER
                .comment("Percentage points added to every drop chance per level of Capitalist Greed (first entry = level I)")
                .<Number>defineListAllowEmpty("capitalistGreedBonus", List.of(5.0, 8.0, 10.0),
                        () -> 0.0, value -> value instanceof Number n && n.doubleValue() >= 0.0);

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
