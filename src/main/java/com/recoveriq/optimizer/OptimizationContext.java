package com.recoveriq.optimizer;

import com.recoveriq.synthetic.FailureType;

/** Current recovery case data required for Day 4 action selection. */
public record OptimizationContext(String paymentId, int recoverableAmount, FailureType failureType, int retryCount) {
    public OptimizationContext {
        if (paymentId == null || paymentId.isBlank() || recoverableAmount <= 0 || failureType == null || retryCount < 0) {
            throw new IllegalArgumentException("payment, amount, failure type and retry count must be valid");
        }
    }
}
