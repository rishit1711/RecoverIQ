package com.recoveriq.synthetic;

import java.time.Instant;
import java.util.List;

/** Parameters for a deterministic synthetic-history scenario. */
public record GeneratorConfig(
        long seed,
        int datasetSize,
        int customerCount,
        int minPaymentsPerCustomer,
        int maxPaymentsPerCustomer,
        int maxRetriesPerPayment,
        Instant startAt,
        int daysOfHistory,
        String currency,
        double recurringShare,
        List<ArchetypeWeight> archetypeWeights,
        java.util.Map<CustomerArchetype, CustomerBehaviourProfile> customerBehaviourProfiles,
        double transientFailureRate,
        int retryInitialDelayHours,
        java.util.Map<FailureType, Double> failureTypeMultipliers,
        java.util.Map<RecoveryAction, Double> actionSelectionWeights,
        java.util.Map<RecoveryAction, Double> actionSuccessMultipliers) {

    public record ArchetypeWeight(CustomerArchetype archetype, double weight) { }

    public GeneratorConfig {
        archetypeWeights = List.copyOf(archetypeWeights);
        customerBehaviourProfiles = java.util.Map.copyOf(customerBehaviourProfiles);
        failureTypeMultipliers = java.util.Map.copyOf(failureTypeMultipliers);
        actionSelectionWeights = java.util.Map.copyOf(actionSelectionWeights);
        actionSuccessMultipliers = java.util.Map.copyOf(actionSuccessMultipliers);
        if (datasetSize < 1) throw new IllegalArgumentException("datasetSize must be positive");
        if (customerCount < 1) throw new IllegalArgumentException("customerCount must be positive");
        if (minPaymentsPerCustomer < 1 || minPaymentsPerCustomer > maxPaymentsPerCustomer) {
            throw new IllegalArgumentException("payment bounds must be positive and ordered");
        }
        if (maxRetriesPerPayment < 0 || daysOfHistory < 1) {
            throw new IllegalArgumentException("retry count must be non-negative and history must be positive");
        }
        if (recurringShare < 0 || recurringShare > 1 || transientFailureRate < 0 || transientFailureRate > 1) {
            throw new IllegalArgumentException("rates must be between zero and one");
        }
        if (retryInitialDelayHours < 1 || archetypeWeights.isEmpty()
                || archetypeWeights.stream().anyMatch(weight -> weight.weight() < 0)
                || archetypeWeights.stream().mapToDouble(ArchetypeWeight::weight).sum() <= 0) {
            throw new IllegalArgumentException("retry delay and archetype weights must be positive");
        }
        if (!customerBehaviourProfiles.keySet().containsAll(java.util.List.of(CustomerArchetype.values()))) {
            throw new IllegalArgumentException("a customer behaviour profile is required for every archetype");
        }
        validatePositiveWeights(failureTypeMultipliers, "failureTypeMultipliers");
        validateNonNegativeWeights(actionSelectionWeights, "actionSelectionWeights");
        validateNonNegativeWeights(actionSuccessMultipliers, "actionSuccessMultipliers");
    }

    private static <T> void validateNonNegativeWeights(java.util.Map<T, Double> weights, String name) {
        if (weights.values().stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException(name + " cannot contain negative values");
        }
    }

    private static <T> void validatePositiveWeights(java.util.Map<T, Double> weights, String name) {
        if (weights.values().stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException(name + " must contain positive values");
        }
    }

    public static GeneratorConfig defaults() {
        return new GeneratorConfig(
                20260822L, 1_000, 250, 3, 12, 3, Instant.parse("2025-01-01T00:00:00Z"),
                365, "INR", 0.72,
                List.of(
                        new ArchetypeWeight(CustomerArchetype.RELIABLE, 0.52),
                        new ArchetypeWeight(CustomerArchetype.CASH_FLOW_SENSITIVE, 0.25),
                        new ArchetypeWeight(CustomerArchetype.DISENGAGED, 0.15),
                        new ArchetypeWeight(CustomerArchetype.HIGH_RISK, 0.08)),
                java.util.Map.of(
                        CustomerArchetype.RELIABLE, new CustomerBehaviourProfile(.82, .82, .90, .06),
                        CustomerArchetype.CASH_FLOW_SENSITIVE, new CustomerBehaviourProfile(.43, .70, .78, .10),
                        CustomerArchetype.DISENGAGED, new CustomerBehaviourProfile(.61, .25, .70, .12),
                        CustomerArchetype.HIGH_RISK, new CustomerBehaviourProfile(.42, .35, .52, .62)),
                0.045, 12,
                java.util.Map.of(
                        FailureType.TRANSIENT, 1.0,
                        FailureType.INSUFFICIENT_FUNDS, 1.0,
                        FailureType.AUTHENTICATION_REQUIRED, 1.0,
                        FailureType.PAYMENT_METHOD_INVALID, 1.0,
                        FailureType.FRAUD_OR_RISK, 1.0),
                java.util.Map.of(
                        RecoveryAction.IMMEDIATE_RETRY, 1.0,
                        RecoveryAction.DELAYED_RETRY, 1.0,
                        RecoveryAction.SEND_PAYMENT_LINK, 1.0,
                        RecoveryAction.REQUEST_PAYMENT_METHOD_UPDATE, 1.0),
                java.util.Map.of(
                        RecoveryAction.IMMEDIATE_RETRY, 0.85,
                        RecoveryAction.DELAYED_RETRY, 1.15,
                        RecoveryAction.SEND_PAYMENT_LINK, 1.30,
                        RecoveryAction.REQUEST_PAYMENT_METHOD_UPDATE, 1.45));
    }
}
