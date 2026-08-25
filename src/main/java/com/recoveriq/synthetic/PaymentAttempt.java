package com.recoveriq.synthetic;

import java.time.Instant;

public record PaymentAttempt(
        int attemptNumber,
        Instant attemptedAt,
        AttemptStatus status,
        FailureType failureType,
        RecoveryAction actionTaken,
        String paymentMethod,
        boolean customerEngaged) { }
