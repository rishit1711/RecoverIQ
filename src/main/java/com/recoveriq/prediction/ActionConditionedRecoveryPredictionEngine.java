package com.recoveriq.prediction;

import com.recoveriq.synthetic.AttemptStatus;
import com.recoveriq.synthetic.HistoricalRecoveryRecord;
import com.recoveriq.synthetic.RecoveryAction;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Deterministically estimates P(success | candidate action, payment context)
 * from labelled Day 2 records. It intentionally does not select an action.
 */
public final class ActionConditionedRecoveryPredictionEngine {
    private final List<HistoricalRecoveryRecord> history;
    private final PredictionConfig config;

    public ActionConditionedRecoveryPredictionEngine(List<HistoricalRecoveryRecord> history, PredictionConfig config) {
        this.history = List.copyOf(history);
        this.config = config;
    }

    public List<RecoveryPrediction> predictAll(PredictionContext context, List<RecoveryAction> candidateActions) {
        return candidateActions.stream().map(action -> predict(context, action)).toList();
    }

    public RecoveryPrediction predict(PredictionContext context, RecoveryAction action) {
        List<Evidence> candidates = List.of(
                evidence(EvidenceLevel.CUSTOMER_FAILURE_ACTION, action, record -> record.customerId().equals(context.customerId())
                        && record.failureType() == context.failureType()),
                evidence(EvidenceLevel.CUSTOMER_ACTION, action, record -> record.customerId().equals(context.customerId())),
                evidence(EvidenceLevel.SEGMENT_FAILURE_ACTION, action, record -> record.customerBehaviour() == context.customerBehaviour()
                        && record.failureType() == context.failureType()),
                evidence(EvidenceLevel.FAILURE_ACTION, action, record -> record.failureType() == context.failureType()),
                evidence(EvidenceLevel.GLOBAL_ACTION, action, record -> true));

        for (Evidence candidate : candidates) {
            if (candidate.records.size() >= config.minimumEvidenceSamples()) return prediction(context, action, candidate);
        }
        for (Evidence candidate : candidates) {
            if (!candidate.records.isEmpty()) return prediction(context, action, candidate);
        }
        return new RecoveryPrediction(action, config.priorSuccessProbability(), config.priorSuccessProbability(), 0,
                EvidenceLevel.GLOBAL_PRIOR, false, "No historical records exist for this action; using the configured prior.");
    }

    private Evidence evidence(EvidenceLevel level, RecoveryAction action, Predicate<HistoricalRecoveryRecord> scope) {
        return new Evidence(level, history.stream().filter(record -> record.actionTaken() == action).filter(scope).toList());
    }

    private RecoveryPrediction prediction(PredictionContext context, RecoveryAction action, Evidence evidence) {
        List<HistoricalRecoveryRecord> contextual = evidence.records.stream().filter(record -> record.attemptNumber() == context.attemptNumber()
                && amountBand(record.amount()).equals(amountBand(context.amount()))).toList();
        boolean useContext = contextual.size() >= config.minimumContextualSamples();
        List<HistoricalRecoveryRecord> records = useContext ? contextual : evidence.records;
        long successes = records.stream().filter(record -> record.outcome() == AttemptStatus.SUCCEEDED).count();
        double observed = successes / (double) records.size();
        double probability = (successes + config.priorSuccessProbability() * config.priorStrength())
                / (records.size() + config.priorStrength());
        String suffix = useContext ? " Matching attempt number and amount band were available." : "";
        String reason = "%s evidence: %d records, observed success %.1f%%; smoothed with %.1f prior samples at %.1f%%.%s"
                .formatted(describe(evidence.level), records.size(), observed * 100, config.priorStrength(),
                        config.priorSuccessProbability() * 100, suffix);
        return new RecoveryPrediction(action, probability, observed, records.size(), evidence.level, useContext, reason);
    }

    private static String amountBand(int amount) {
        return amount < 1_000 ? "LOW" : amount < 3_000 ? "MEDIUM" : "HIGH";
    }

    private static String describe(EvidenceLevel level) {
        return switch (level) {
            case CUSTOMER_FAILURE_ACTION -> "Customer + failure type + action";
            case CUSTOMER_ACTION -> "Customer + action";
            case SEGMENT_FAILURE_ACTION -> "Customer segment + failure type + action";
            case FAILURE_ACTION -> "Failure type + action";
            case GLOBAL_ACTION -> "Global action";
            case GLOBAL_PRIOR -> "Global prior";
        };
    }

    private record Evidence(EvidenceLevel level, List<HistoricalRecoveryRecord> records) { }
}
