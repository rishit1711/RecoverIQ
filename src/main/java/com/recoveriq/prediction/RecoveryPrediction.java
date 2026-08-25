package com.recoveriq.prediction;

import com.recoveriq.synthetic.RecoveryAction;

/** Explainable probability estimate for one candidate recovery action. */
public record RecoveryPrediction(
        RecoveryAction action,
        double predictedSuccessProbability,
        double observedSuccessRate,
        int sampleSize,
        EvidenceLevel evidenceLevel,
        boolean usedAttemptAndAmountContext,
        String reason) { }
