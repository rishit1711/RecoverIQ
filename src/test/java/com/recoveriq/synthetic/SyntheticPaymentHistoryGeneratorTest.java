package com.recoveriq.synthetic;

import java.util.EnumSet;

/** Dependency-free checks for the Day 2 historical-data layer. */
public final class SyntheticPaymentHistoryGeneratorTest {
    private static PaymentHistory history(long seed, int datasetSize) {
        GeneratorConfig defaults = GeneratorConfig.defaults();
        GeneratorConfig config = new GeneratorConfig(seed, datasetSize, 40, 1, 12, 3, defaults.startAt(), 365, "INR", .72,
                defaults.archetypeWeights(), defaults.customerBehaviourProfiles(), .045, 12, defaults.failureTypeMultipliers(),
                defaults.actionSelectionWeights(), defaults.actionSuccessMultipliers());
        return new SyntheticPaymentHistoryGenerator(config).generate();
    }

    public static void main(String[] args) {
        sameSeedProducesSameHistory();
        differentSeedProducesDifferentHistory();
        datasetSizeIsExact();
        generatedValuesAreValid();
        retryLimitsAndStatesAreValid();
        statisticsAreInternallyConsistent();
        System.out.println("6 checks passed");
    }

    private static void sameSeedProducesSameHistory() {
        require(history(42, 1_000).equals(history(42, 1_000)), "same seed must reproduce the dataset");
    }

    private static void differentSeedProducesDifferentHistory() {
        require(!history(42, 100).equals(history(43, 100)), "different seeds must create different datasets");
    }

    private static void datasetSizeIsExact() {
        require(history(9, 20).payments().size() == 20, "small dataset size was not honoured");
        require(history(9, 1_000).payments().size() == 1_000, "medium dataset size was not honoured");
        require(history(9, 10_000).payments().size() == 10_000, "large dataset size was not honoured");
    }

    private static void generatedValuesAreValid() {
        PaymentHistory history = history(17, 1_000);
        for (HistoricalRecoveryRecord record : history.records()) {
            require(record.customerId() != null && record.paymentId() != null && record.amount() > 0 && record.timestamp() != null,
                    "record has missing required data");
            require(EnumSet.allOf(FailureType.class).contains(record.failureType()), "invalid failure type");
            require(EnumSet.allOf(RecoveryAction.class).contains(record.actionTaken()), "invalid recovery action");
            require(EnumSet.allOf(AttemptStatus.class).contains(record.outcome()), "invalid outcome");
        }
    }

    private static void retryLimitsAndStatesAreValid() {
        PaymentHistory history = history(17, 1_000);
        for (Payment payment : history.payments()) {
            require(payment.attempts().size() <= 4, "retry limit was exceeded");
            for (int index = 0; index < payment.attempts().size(); index++) {
                PaymentAttempt attempt = payment.attempts().get(index);
                require(attempt.attemptNumber() == index + 1, "attempt sequence is invalid");
                require(attempt.status() == AttemptStatus.SUCCEEDED ? attempt.failureType() == FailureType.NONE
                        : attempt.failureType() != FailureType.NONE, "outcome and failure type conflict");
                if (index == 0) require(attempt.actionTaken() == RecoveryAction.INITIAL_PAYMENT_ATTEMPT,
                        "first attempt must be the initial payment action");
                if (index < payment.attempts().size() - 1) require(attempt.status() == AttemptStatus.FAILED,
                        "no attempt may follow a success");
            }
        }
    }

    private static void statisticsAreInternallyConsistent() {
        PaymentHistory history = history(17, 1_000);
        PaymentHistoryStatistics statistics = history.statistics();
        require(statistics.totalRecords() == history.records().size(), "total record statistic is wrong");
        require(statistics.uniqueCustomers() > 0 && statistics.uniqueCustomers() <= 40, "unique-customer statistic is unreasonable");
        require(statistics.overall().succeeded() + statistics.overall().failed() == statistics.totalRecords(),
                "success and failure counts do not add up");
        require(statistics.overall().successRate() > .15 && statistics.overall().successRate() < .95,
                "success rate is outside reasonable configured bounds");
        require(statistics.failureTypeDistribution().values().stream().mapToLong(Long::longValue).sum() == statistics.totalRecords(),
                "failure distribution does not cover all records");
        require(statistics.actionDistribution().values().stream().mapToLong(Long::longValue).sum() == statistics.totalRecords(),
                "action distribution does not cover all records");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
