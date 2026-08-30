package com.saunhardy.createringtoncurrency.util;

import java.time.Duration;
import java.time.Instant;

public final class TransactionFormat {
    private TransactionFormat() {}

    public static String type(String type) {
        if (type == null) return "";
        return switch (type) {
            case "transfer_send" -> "Sent";
            case "transfer_receive" -> "Received";
            case "deposit" -> "Deposit";
            case "withdraw" -> "Withdraw";
            case "admin_grant" -> "Admin Grant";
            case "admin_deduct" -> "Admin Deduct";
            case "reward" -> "Reward";
            case "daily_reward" -> "Daily";
            case "lottery_entry" -> "Lottery Entry";
            case "lottery_win" -> "Lottery Win";
            case "lottery_refund" -> "Lottery Refund";
            case "crypto_buy" -> "Crypto Buy";
            case "crypto_sell" -> "Crypto Sell";
            default -> type;
        };
    }

    public static String relativeDate(String iso) {
        if (iso == null) return "";
        try {
            Instant then = Instant.parse(iso);
            long seconds = Duration.between(then, Instant.now()).getSeconds();
            if (seconds < 0) return "now";
            if (seconds < 60) return seconds + "s ago";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "m ago";
            long hours = minutes / 60;
            if (hours < 24) return hours + "h ago";
            long days = hours / 24;
            if (days < 30) return days + "d ago";
            return days / 30 + "mo ago";
        } catch (Exception e) {
            return iso.length() >= 10 ? iso.substring(0, 10) : iso;
        }
    }
}
