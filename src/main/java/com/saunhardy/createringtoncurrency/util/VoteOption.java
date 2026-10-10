package com.saunhardy.createringtoncurrency.util;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record VoteOption(String id, String group, String label, String command) {
    public static final int MAX_DAYS = 7;

    private static final Pattern NAME = Pattern.compile("[a-z0-9_.+-]+");
    private static final Pattern DAYS = Pattern.compile("\\{days(?::(\\d+(?:\\.\\d+)?))?}");
    private static final Set<String> RESERVED = Set.of("yes", "no");
    private static final String DEFAULT_DAYS = "1";

    @Nullable
    public static VoteOption parse(String raw) {
        String[] parts = raw.split("\\|", 4);
        if (parts.length != 4) return null;

        String id = parts[0].trim().toLowerCase(Locale.ROOT);
        String group = parts[1].trim().toLowerCase(Locale.ROOT);
        String label = parts[2].trim();
        String command = parts[3].trim();
        if (command.startsWith("/")) command = command.substring(1).trim();

        if (!NAME.matcher(id).matches() || RESERVED.contains(id)) return null;
        if (!NAME.matcher(group).matches()) return null;
        if (label.isEmpty() || command.isEmpty()) return null;
        return new VoteOption(id, group, label, command);
    }

    public boolean takesDays() {
        return DAYS.matcher(command).find();
    }

    public String commandFor(int days) {
        return DAYS.matcher(command).replaceAll(match -> {
            if (days > 0) return Integer.toString(days);
            String fallback = match.group(1);
            return fallback != null ? fallback : DEFAULT_DAYS;
        });
    }
}
