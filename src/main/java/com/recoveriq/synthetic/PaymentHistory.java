package com.recoveriq.synthetic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Generated payment cases plus flat, labelled records suitable for Day 3. */
public record PaymentHistory(List<Customer> customers, List<Payment> payments) {
    public PaymentHistory {
        customers = List.copyOf(customers);
        payments = List.copyOf(payments);
    }

    public List<HistoricalRecoveryRecord> records() {
        Map<String, Customer> customerById = customers.stream().collect(Collectors.toMap(Customer::customerId, Function.identity()));
        return payments.stream().flatMap(payment -> payment.attempts().stream().map(attempt -> new HistoricalRecoveryRecord(
                payment.customerId(), payment.paymentId(), payment.amount(), payment.currency(),
                customerById.get(payment.customerId()).archetype(), attempt.failureType(), attempt.attemptNumber(),
                attempt.actionTaken(), attempt.status(), attempt.attemptedAt()))).toList();
    }

    public PaymentHistoryStatistics statistics() {
        List<HistoricalRecoveryRecord> records = records();
        Map<FailureType, Long> failureTypes = count(records, HistoricalRecoveryRecord::failureType, FailureType.class);
        Map<RecoveryAction, Long> actions = count(records, HistoricalRecoveryRecord::actionTaken, RecoveryAction.class);
        return new PaymentHistoryStatistics(records.size(), records.stream().map(HistoricalRecoveryRecord::customerId).distinct().count(),
                outcome(records), failureTypes, actions,
                groupedOutcome(records, HistoricalRecoveryRecord::actionTaken, RecoveryAction.class),
                groupedOutcome(records, HistoricalRecoveryRecord::failureType, FailureType.class),
                groupedOutcome(records, HistoricalRecoveryRecord::customerBehaviour, CustomerArchetype.class),
                records.stream().collect(Collectors.groupingBy(HistoricalRecoveryRecord::attemptNumber,
                        LinkedHashMap::new, Collectors.counting())));
    }

    /** Writes one labelled, attempt-level historical record per JSONL line. */
    public void writeJsonLines(Path output) throws IOException {
        try (var writer = Files.newBufferedWriter(output)) {
            for (HistoricalRecoveryRecord record : records()) {
                writer.write("""
                        {"customerId":"%s","paymentId":"%s","amount":%d,"currency":"%s","customerBehaviour":"%s","failureType":"%s","attemptNumber":%d,"actionTaken":"%s","outcome":"%s","timestamp":"%s"}"""
                        .formatted(escape(record.customerId()), escape(record.paymentId()), record.amount(), escape(record.currency()),
                                record.customerBehaviour(), record.failureType(), record.attemptNumber(), record.actionTaken(),
                                record.outcome(), record.timestamp()));
                writer.newLine();
            }
        }
    }

    private static <K extends Enum<K>> Map<K, Long> count(List<HistoricalRecoveryRecord> records,
            Function<HistoricalRecoveryRecord, K> classifier, Class<K> keyType) {
        Map<K, Long> values = new EnumMap<>(keyType);
        for (K key : keyType.getEnumConstants()) values.put(key, 0L);
        records.forEach(record -> values.merge(classifier.apply(record), 1L, Long::sum));
        return Map.copyOf(values);
    }

    private static <K extends Enum<K>> Map<K, OutcomeStatistics> groupedOutcome(List<HistoricalRecoveryRecord> records,
            Function<HistoricalRecoveryRecord, K> classifier, Class<K> keyType) {
        Map<K, OutcomeStatistics> values = new EnumMap<>(keyType);
        for (K key : keyType.getEnumConstants()) {
            values.put(key, outcome(records.stream().filter(record -> classifier.apply(record) == key).toList()));
        }
        return Map.copyOf(values);
    }

    private static OutcomeStatistics outcome(List<HistoricalRecoveryRecord> records) {
        long success = records.stream().filter(record -> record.outcome() == AttemptStatus.SUCCEEDED).count();
        return new OutcomeStatistics(records.size(), success, records.size() - success);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
