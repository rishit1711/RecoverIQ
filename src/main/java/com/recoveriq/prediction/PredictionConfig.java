package com.recoveriq.prediction;

/** Controls evidence thresholds and Bayesian smoothing without introducing ML infrastructure. */
public record PredictionConfig(int minimumEvidenceSamples, int minimumContextualSamples,
                               double priorSuccessProbability, double priorStrength) {
    public PredictionConfig {
        if (minimumEvidenceSamples < 1 || minimumContextualSamples < 1 || priorSuccessProbability < 0
                || priorSuccessProbability > 1 || priorStrength <= 0) {
            throw new IllegalArgumentException("invalid prediction configuration");
        }
    }

    public static PredictionConfig defaults() {
        return new PredictionConfig(3, 3, 0.50, 4.0);
    }
}
