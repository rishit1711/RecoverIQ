package com.recoveriq.synthetic;

import java.time.Instant;
import java.util.List;

/** Parameters for a deterministic synthetic-history scenario. */
public record GeneratorConfig(
        long seed,
        int customerCount,
        int minPaymentsPerCustomer,
        int maxPaymentsPerCustomer,
        int maxRetriesPerPayment,
        Instant startAt,
        int daysOfHistory,
        String currency,
        double recurringShare,
        List<ArchetypeWeight> archetypeWeights,
        double transientFailureRate,
        int retryInitialDelayHours) {

    public record ArchetypeWeight(CustomerArchetype archetype, double weight) { }

    public GeneratorConfig {
        archetypeWeights = List.copyOf(archetypeWeights);
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
    }

    public static GeneratorConfig defaults() {
        return new GeneratorConfig(
                20260822L, 250, 3, 12, 3, Instant.parse("2025-01-01T00:00:00Z"),
                365, "INR", 0.72,
                List.of(
                        new ArchetypeWeight(CustomerArchetype.RELIABLE, 0.52),
                        new ArchetypeWeight(CustomerArchetype.CASH_FLOW_SENSITIVE, 0.25),
                        new ArchetypeWeight(CustomerArchetype.DISENGAGED, 0.15),
                        new ArchetypeWeight(CustomerArchetype.HIGH_RISK, 0.08)),
                0.045, 12);
    }
}
