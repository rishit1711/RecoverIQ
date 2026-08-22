package com.recoveriq.synthetic;

import java.time.Instant;
import java.util.List;

public record Payment(
        String paymentId,
        String customerId,
        int amount,
        String currency,
        boolean recurring,
        Instant dueAt,
        List<PaymentAttempt> attempts,
        AttemptStatus eventualOutcome) {
    public Payment {
        attempts = List.copyOf(attempts);
    }
}
