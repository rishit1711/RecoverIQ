package com.recoveriq.prediction;

import com.recoveriq.synthetic.AttemptStatus;
import com.recoveriq.synthetic.CustomerArchetype;
import com.recoveriq.synthetic.FailureType;
import com.recoveriq.synthetic.HistoricalRecoveryRecord;
import com.recoveriq.synthetic.RecoveryAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free unit tests for the deterministic Day 3 prediction engine. */
public final class ActionConditionedRecoveryPredictionEngineTest {
    private static final PredictionConfig CONFIG = new PredictionConfig(3, 3, .50, 4.0);

    public static void main(String[] args) {
        knownDistributionIsSmoothedCorrectly();
        actionsCanProduceDifferentPredictions();
        failureTypesCanProduceDifferentPredictions();
        customerBehaviourInfluencesPrediction();
        sparseCustomerHistoryFallsBack();
        noDataUsesSafePrior();
        predictionsAreDeterministicAndBounded();
        multipleActionsAreReturnedWithoutChoosingOne();
        System.out.println("8 checks passed");
    }

    private static void knownDistributionIsSmoothedCorrectly() {
        List<HistoricalRecoveryRecord> records = records("cus_1", CustomerArchetype.RELIABLE, FailureType.TRANSIENT,
                RecoveryAction.DELAYED_RETRY, 8, 2);
        RecoveryPrediction prediction = engine(records).predict(context("cus_1", CustomerArchetype.RELIABLE, FailureType.TRANSIENT),
                RecoveryAction.DELAYED_RETRY);
        require(close(prediction.observedSuccessRate(), .80), "unexpected observed rate");
        require(close(prediction.predictedSuccessProbability(), 10.0 / 14.0), "unexpected smoothed probability");
        require(prediction.sampleSize() == 10 && prediction.evidenceLevel() == EvidenceLevel.CUSTOMER_FAILURE_ACTION,
                "wrong evidence details");
    }

    private static void actionsCanProduceDifferentPredictions() {
        List<HistoricalRecoveryRecord> records = new ArrayList<>();
        records.addAll(records("cus_1", CustomerArchetype.RELIABLE, FailureType.TRANSIENT, RecoveryAction.DELAYED_RETRY, 8, 2));
        records.addAll(records("cus_1", CustomerArchetype.RELIABLE, FailureType.TRANSIENT, RecoveryAction.IMMEDIATE_RETRY, 2, 8));
        var engine = engine(records);
        double delayed = engine.predict(context("cus_1", CustomerArchetype.RELIABLE, FailureType.TRANSIENT), RecoveryAction.DELAYED_RETRY)
                .predictedSuccessProbability();
        double immediate = engine.predict(context("cus_1", CustomerArchetype.RELIABLE, FailureType.TRANSIENT), RecoveryAction.IMMEDIATE_RETRY)
                .predictedSuccessProbability();
        require(delayed > immediate, "different action outcomes should produce different predictions");
    }

    private static void failureTypesCanProduceDifferentPredictions() {
        List<HistoricalRecoveryRecord> records = new ArrayList<>();
        records.addAll(records("h1", CustomerArchetype.RELIABLE, FailureType.TRANSIENT, RecoveryAction.DELAYED_RETRY, 7, 1));
        records.addAll(records("h2", CustomerArchetype.RELIABLE, FailureType.INSUFFICIENT_FUNDS, RecoveryAction.DELAYED_RETRY, 1, 7));
        var engine = engine(records);
        double transientRate = engine.predict(context("new", CustomerArchetype.DISENGAGED, FailureType.TRANSIENT), RecoveryAction.DELAYED_RETRY)
                .predictedSuccessProbability();
        double fundsRate = engine.predict(context("new", CustomerArchetype.DISENGAGED, FailureType.INSUFFICIENT_FUNDS), RecoveryAction.DELAYED_RETRY)
                .predictedSuccessProbability();
        require(transientRate > fundsRate, "failure type must affect the prediction");
    }

    private static void customerBehaviourInfluencesPrediction() {
        List<HistoricalRecoveryRecord> records = new ArrayList<>();
        records.addAll(records("r", CustomerArchetype.RELIABLE, FailureType.AUTHENTICATION_REQUIRED, RecoveryAction.SEND_PAYMENT_LINK, 6, 1));
        records.addAll(records("d", CustomerArchetype.DISENGAGED, FailureType.AUTHENTICATION_REQUIRED, RecoveryAction.SEND_PAYMENT_LINK, 1, 6));
        var engine = engine(records);
        double reliable = engine.predict(context("new-r", CustomerArchetype.RELIABLE, FailureType.AUTHENTICATION_REQUIRED),
                RecoveryAction.SEND_PAYMENT_LINK).predictedSuccessProbability();
        double disengaged = engine.predict(context("new-d", CustomerArchetype.DISENGAGED, FailureType.AUTHENTICATION_REQUIRED),
                RecoveryAction.SEND_PAYMENT_LINK).predictedSuccessProbability();
        require(reliable > disengaged, "segment evidence should influence new-customer predictions");
    }

    private static void sparseCustomerHistoryFallsBack() {
        List<HistoricalRecoveryRecord> records = new ArrayList<>();
        records.addAll(records("sparse", CustomerArchetype.RELIABLE, FailureType.TRANSIENT, RecoveryAction.DELAYED_RETRY, 1, 0));
        records.addAll(records("sparse", CustomerArchetype.RELIABLE, FailureType.INSUFFICIENT_FUNDS, RecoveryAction.DELAYED_RETRY, 1, 2));
        RecoveryPrediction prediction = engine(records).predict(context("sparse", CustomerArchetype.RELIABLE, FailureType.TRANSIENT),
                RecoveryAction.DELAYED_RETRY);
        require(prediction.evidenceLevel() == EvidenceLevel.CUSTOMER_ACTION && prediction.sampleSize() == 4,
                "sparse customer/failure data did not back off to customer/action evidence");
    }

    private static void noDataUsesSafePrior() {
        RecoveryPrediction prediction = engine(List.of()).predict(context("missing", CustomerArchetype.HIGH_RISK, FailureType.FRAUD_OR_RISK),
                RecoveryAction.STOP_RECOVERY);
        require(prediction.evidenceLevel() == EvidenceLevel.GLOBAL_PRIOR && prediction.sampleSize() == 0
                && close(prediction.predictedSuccessProbability(), .50), "no-data behaviour is unsafe");
    }

    private static void predictionsAreDeterministicAndBounded() {
        var engine = engine(records("c", CustomerArchetype.RELIABLE, FailureType.TRANSIENT, RecoveryAction.DELAYED_RETRY, 2, 1));
        var context = context("c", CustomerArchetype.RELIABLE, FailureType.TRANSIENT);
        RecoveryPrediction first = engine.predict(context, RecoveryAction.DELAYED_RETRY);
        RecoveryPrediction second = engine.predict(context, RecoveryAction.DELAYED_RETRY);
        require(first.equals(second) && first.predictedSuccessProbability() >= 0 && first.predictedSuccessProbability() <= 1,
                "prediction must be deterministic and bounded");
    }

    private static void multipleActionsAreReturnedWithoutChoosingOne() {
        var engine = engine(records("c", CustomerArchetype.RELIABLE, FailureType.TRANSIENT, RecoveryAction.DELAYED_RETRY, 2, 1));
        List<RecoveryAction> candidates = List.of(RecoveryAction.IMMEDIATE_RETRY, RecoveryAction.DELAYED_RETRY, RecoveryAction.SEND_PAYMENT_LINK);
        List<RecoveryPrediction> predictions = engine.predictAll(context("c", CustomerArchetype.RELIABLE, FailureType.TRANSIENT), candidates);
        require(predictions.size() == 3 && predictions.get(0).action() == RecoveryAction.IMMEDIATE_RETRY
                && predictions.get(1).action() == RecoveryAction.DELAYED_RETRY && predictions.get(2).action() == RecoveryAction.SEND_PAYMENT_LINK,
                "engine must return each requested action, preserving caller order");
    }

    private static ActionConditionedRecoveryPredictionEngine engine(List<HistoricalRecoveryRecord> records) {
        return new ActionConditionedRecoveryPredictionEngine(records, CONFIG);
    }

    private static PredictionContext context(String customerId, CustomerArchetype behaviour, FailureType failure) {
        return new PredictionContext(customerId, behaviour, 2_000, failure, 1);
    }

    private static List<HistoricalRecoveryRecord> records(String customerId, CustomerArchetype behaviour, FailureType failure,
            RecoveryAction action, int successes, int failures) {
        List<HistoricalRecoveryRecord> records = new ArrayList<>();
        for (int index = 0; index < successes + failures; index++) {
            AttemptStatus outcome = index < successes ? AttemptStatus.SUCCEEDED : AttemptStatus.FAILED;
            records.add(new HistoricalRecoveryRecord(customerId, "pay_" + customerId + "_" + index, 2_000, "INR", behaviour,
                    failure, 1, action, outcome, Instant.parse("2025-01-01T00:00:00Z").plusSeconds(index)));
        }
        return records;
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < .000001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
