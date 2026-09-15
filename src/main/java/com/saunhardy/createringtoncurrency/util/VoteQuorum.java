package com.saunhardy.createringtoncurrency.util;

public final class VoteQuorum {

    public record Tally(int eligible, int needed, int yes, int no, boolean decided, boolean passed) {}

    public static Tally tally(int eligible, int yes, int no, int percent) {
        return tally(eligible, yes, no, percent, Integer.MAX_VALUE);
    }

    public static Tally tally(int eligible, int yes, int no, int percent, int maxNeeded) {
        int needed = Math.max(1, Math.min(requiredYes(eligible, percent), maxNeeded));
        int undecided = eligible - yes - no;
        boolean passed = yes >= needed;

        return new Tally(eligible, needed, yes, no, passed || yes + undecided < needed, passed);
    }

    public static int requiredYes(int eligible, int percent) {
        return Math.max(1, Math.min(eligible, (eligible * percent + 99) / 100));
    }

    public static boolean rejected(Tally tally) {
        return tally.no() > 0 && tally.no() >= tally.eligible() - tally.needed() + 1;
    }

    private VoteQuorum() {}
}
