package com.saunhardy.createringtoncurrency.audit;

import com.saunhardy.createringtoncurrency.util.Bills;

public record CashSite(String label, String dimension, int x, int y, int z, int[] counts) {

    public long value() {
        return Bills.value(counts);
    }

    public String coords() {
        return x + " " + y + " " + z;
    }

    public String teleportCommand() {
        return "/execute in " + dimension + " run tp @s " + x + " " + y + " " + z;
    }

    public String shortDimension() {
        int colon = dimension.indexOf(':');
        return colon < 0 ? dimension : dimension.substring(colon + 1);
    }

    public String breakdown() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Bills.DENOMINATIONS.length; i++) {
            if (counts[i] <= 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(counts[i]).append("x $").append(Bills.DENOMINATIONS[i]);
        }
        return sb.length() == 0 ? "nothing" : sb.toString();
    }
}
