package com.recoveriq.synthetic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record PaymentHistory(List<Customer> customers, List<Payment> payments) {
    public PaymentHistory {
        customers = List.copyOf(customers);
        payments = List.copyOf(payments);
    }

    /** Writes one payment, with its ordered attempts, on each JSONL line. */
    public void writeJsonLines(Path output) throws IOException {
        try (var writer = Files.newBufferedWriter(output)) {
            for (Payment payment : payments) {
                writer.write(toJson(payment));
                writer.newLine();
            }
        }
    }

    private static String toJson(Payment payment) {
        String attempts = payment.attempts().stream().map(attempt -> """
                {"attemptNumber":%d,"attemptedAt":"%s","status":"%s","failureType":"%s","paymentMethod":"%s","customerEngaged":%s}"""
                .formatted(attempt.attemptNumber(), attempt.attemptedAt(), attempt.status(), attempt.failureType(),
                        escape(attempt.paymentMethod()), attempt.customerEngaged()))
                .reduce((left, right) -> left + "," + right).orElse("");
        return """
                {"paymentId":"%s","customerId":"%s","amount":%d,"currency":"%s","recurring":%s,"dueAt":"%s","attempts":[%s],"eventualOutcome":"%s"}"""
                .formatted(escape(payment.paymentId()), escape(payment.customerId()), payment.amount(),
                        escape(payment.currency()), payment.recurring(), payment.dueAt(), attempts, payment.eventualOutcome());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
