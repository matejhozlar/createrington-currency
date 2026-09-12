package com.saunhardy.createringtoncurrency.audit;

import com.saunhardy.createringtoncurrency.util.Bills;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CashCensus {
    private final List<CashSite> sites = new ArrayList<>();
    private final Map<String, int[]> bySource = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final int[] totals = Bills.none();

    private final boolean full;
    private final long startedAt = System.currentTimeMillis();
    private long finishedAt;

    private int[] pending = Bills.none();
    private int playersScanned;
    private int regionsScanned;
    private int chunksScanned;

    public CashCensus(boolean full) {
        this.full = full;
    }

    public synchronized void add(String source, CashSite site) {
        if (Bills.isEmpty(site.counts())) return;

        sites.add(site);
        int[] sourceTotals = bySource.computeIfAbsent(source, key -> Bills.none());
        for (int i = 0; i < totals.length; i++) {
            totals[i] += site.counts()[i];
            sourceTotals[i] += site.counts()[i];
        }
    }

    public synchronized void warn(String warning) {
        if (warnings.size() < 20 && !warnings.contains(warning)) warnings.add(warning);
    }

    public synchronized void countPlayer() {
        playersScanned++;
    }

    public synchronized void countRegion(int chunks) {
        regionsScanned++;
        chunksScanned += chunks;
    }

    public synchronized void finish() {
        finishedAt = System.currentTimeMillis();
        sites.sort(Comparator.comparingLong(CashSite::value).reversed());
    }

    public synchronized void setPending(int[] counts) {
        pending = counts.clone();
    }

    public int[] pending() {
        return pending;
    }

    public boolean isFull() {
        return full;
    }

    public List<CashSite> sites() {
        return sites;
    }

    public Map<String, int[]> bySource() {
        return bySource;
    }

    public List<String> warnings() {
        return warnings;
    }

    public int[] totals() {
        return totals;
    }

    public long total() {
        return Bills.value(totals);
    }

    public long pieces() {
        return Bills.pieces(totals);
    }

    public int playersScanned() {
        return playersScanned;
    }

    public int regionsScanned() {
        return regionsScanned;
    }

    public int chunksScanned() {
        return chunksScanned;
    }

    public long durationMs() {
        return Math.max(0, finishedAt - startedAt);
    }
}
