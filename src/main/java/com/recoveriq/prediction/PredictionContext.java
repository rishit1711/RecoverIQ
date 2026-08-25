package com.recoveriq.prediction;

import com.recoveriq.synthetic.CustomerArchetype;
import com.recoveriq.synthetic.FailureType;

/** Current payment context supplied to the prediction engine. */
public record PredictionContext(
        String customerId,
        CustomerArchetype customerBehaviour,
        int amount,
        FailureType failureType,
        int attemptNumber) {
    public PredictionContext {
        if (customerId == null || customerId.isBlank() || customerBehaviour == null || failureType == null) {
            throw new IllegalArgumentException("customer, behaviour and failure type are required");
        }
        if (amount <= 0 || attemptNumber < 1) throw new IllegalArgumentException("amount and attempt number must be positive");
    }
}
