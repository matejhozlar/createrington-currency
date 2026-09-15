package com.saunhardy.createringtoncurrency.util;

public final class VoteQuorum {

    public record Tally(int eligible, int needed, int yes, int no, boolean decided, boolean passed) {}

    public static Tally tally(int eligible, int yes, int no, int percent, int maxNeeded) {
        int needed = Math.min(requiredYes(eligible, percent), maxNeeded);
        int undecided = eligible - yes - no;
        boolean passed = carries(yes, no, needed);
        boolean decided = carries(yes, no + undecided, needed) || !carries(yes + undecided, no, needed);

        return new Tally(eligible, needed, yes, no, decided, passed);
    }

    public static int requiredYes(int eligible, int percent) {
        return Math.max(1, Math.min(eligible, Math.ceilDiv(eligible * percent, 100)));
    }

    public static boolean outvoted(Tally tally) {
        return tally.yes() >= tally.needed() && tally.yes() <= tally.no();
    }

    public static boolean rejected(Tally tally) {
        return tally.no() > 0 && !carries(tally.eligible() - tally.no(), tally.no(), tally.needed());
    }

    private static boolean carries(int yes, int no, int needed) {
        return yes >= needed && yes > no;
    }

    private VoteQuorum() {}
}
