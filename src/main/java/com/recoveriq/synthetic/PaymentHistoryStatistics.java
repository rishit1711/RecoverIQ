package com.recoveriq.synthetic;

import java.util.Map;

/** Basic dataset-quality diagnostics, derived from flat historical records. */
public record PaymentHistoryStatistics(
        long totalRecords,
        long uniqueCustomers,
        OutcomeStatistics overall,
        Map<FailureType, Long> failureTypeDistribution,
        Map<RecoveryAction, Long> actionDistribution,
        Map<RecoveryAction, OutcomeStatistics> successRateByAction,
        Map<FailureType, OutcomeStatistics> successRateByFailureType,
        Map<CustomerArchetype, OutcomeStatistics> successRateByCustomerBehaviour,
        Map<Integer, Long> retryDistribution) { }
