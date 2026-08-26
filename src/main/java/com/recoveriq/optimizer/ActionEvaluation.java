package com.recoveriq.optimizer;

import com.recoveriq.synthetic.RecoveryAction;

/** Complete calculation and eligibility outcome for a candidate action. */
public record ActionEvaluation(
        RecoveryAction action,
        double predictedSuccessProbability,
        double expectedRecoveryValue,
        double actionCost,
        double frictionPenalty,
        double score,
        boolean eligible,
        String reason) { }
