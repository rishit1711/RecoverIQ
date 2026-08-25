package com.recoveriq.synthetic;

import java.time.Instant;

/** A labelled, attempt-level observation for future prediction-model training. */
public record HistoricalRecoveryRecord(
        String customerId,
        String paymentId,
        int amount,
        String currency,
        CustomerArchetype customerBehaviour,
        FailureType failureType,
        int attemptNumber,
        RecoveryAction actionTaken,
        AttemptStatus outcome,
        Instant timestamp) { }
