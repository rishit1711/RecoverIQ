package com.recoveriq.synthetic;

/** Count and rate for a labelled group of historical records. */
public record OutcomeStatistics(long total, long succeeded, long failed) {
    public double successRate() {
        return total == 0 ? 0.0 : succeeded / (double) total;
    }
}
