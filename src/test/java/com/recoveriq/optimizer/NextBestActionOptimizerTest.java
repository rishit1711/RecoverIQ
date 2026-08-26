package com.recoveriq.optimizer;

import com.recoveriq.prediction.EvidenceLevel;
import com.recoveriq.prediction.RecoveryPrediction;
import com.recoveriq.synthetic.FailureType;
import com.recoveriq.synthetic.RecoveryAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dependency-free unit tests for Day 4 scoring and constraints. */
public final class NextBestActionOptimizerTest {
    public static void main(String[] args) {
        highestExpectedValueWins();
        retryLimitExcludesRetries();
        ineligibleActionIsNeverSelected();
        frictionAndCostCanChangeDecision();
        onlyEligibleActionIsSelected();
        noEligibleActionReturnsSafeResult();
        resultIsDeterministic();
        explanationMatchesSelectedCalculation();
        paymentAmountCanChangeDecision();
        System.out.println("9 checks passed");
    }

    private static void highestExpectedValueWins() {
        NextBestActionResult result = optimizer(OptimizerConfig.defaults()).optimize(context(2_000, FailureType.TRANSIENT, 0),
                List.of(prediction(RecoveryAction.IMMEDIATE_RETRY, .55), prediction(RecoveryAction.DELAYED_RETRY, .78),
                        prediction(RecoveryAction.SEND_PAYMENT_LINK, .64)));
        require(result.selectedAction().orElseThrow() == RecoveryAction.DELAYED_RETRY, "highest score should win");
    }

    private static void retryLimitExcludesRetries() {
        NextBestActionResult result = optimizer(OptimizerConfig.defaults()).optimize(context(2_000, FailureType.TRANSIENT, 2),
                List.of(prediction(RecoveryAction.IMMEDIATE_RETRY, .99), prediction(RecoveryAction.DELAYED_RETRY, .98),
                        prediction(RecoveryAction.SEND_PAYMENT_LINK, .40)));
        require(result.selectedAction().orElseThrow() == RecoveryAction.SEND_PAYMENT_LINK, "retry limit should exclude retry actions");
        require(result.evaluatedActions().stream().filter(e -> e.action() == RecoveryAction.IMMEDIATE_RETRY).noneMatch(ActionEvaluation::eligible),
                "immediate retry was incorrectly eligible");
    }

    private static void ineligibleActionIsNeverSelected() {
        NextBestActionResult result = optimizer(OptimizerConfig.defaults()).optimize(context(2_000, FailureType.INSUFFICIENT_FUNDS, 0),
                List.of(prediction(RecoveryAction.IMMEDIATE_RETRY, .99), prediction(RecoveryAction.DELAYED_RETRY, .50)));
        require(result.selectedAction().orElseThrow() == RecoveryAction.DELAYED_RETRY, "failure eligibility rule was ignored");
    }

    private static void frictionAndCostCanChangeDecision() {
        OptimizerConfig config = configWithEconomics(0, 0, 700, 400);
        NextBestActionResult result = optimizer(config).optimize(context(2_000, FailureType.TRANSIENT, 0),
                List.of(prediction(RecoveryAction.DELAYED_RETRY, .75), prediction(RecoveryAction.SEND_PAYMENT_LINK, .90)));
        require(result.selectedAction().orElseThrow() == RecoveryAction.DELAYED_RETRY,
                "high-probability but high-friction action should not automatically win");
    }

    private static void onlyEligibleActionIsSelected() {
        NextBestActionResult result = optimizer(OptimizerConfig.defaults()).optimize(context(2_000, FailureType.FRAUD_OR_RISK, 0),
                List.of(prediction(RecoveryAction.STOP_RECOVERY, .10)));
        require(result.selectedAction().orElseThrow() == RecoveryAction.STOP_RECOVERY, "only eligible action was not selected");
    }

    private static void noEligibleActionReturnsSafeResult() {
        NextBestActionResult result = optimizer(OptimizerConfig.defaults()).optimize(context(2_000, FailureType.NONE, 0),
                List.of(prediction(RecoveryAction.IMMEDIATE_RETRY, .80)));
        require(result.selectedAction().isEmpty() && result.selectedEvaluation().isEmpty()
                && result.reason().startsWith("No candidate action"), "no-eligible-action case is unsafe");
    }

    private static void resultIsDeterministic() {
        var optimizer = optimizer(OptimizerConfig.defaults());
        var context = context(2_000, FailureType.TRANSIENT, 0);
        var predictions = List.of(prediction(RecoveryAction.IMMEDIATE_RETRY, .55), prediction(RecoveryAction.DELAYED_RETRY, .78));
        require(optimizer.optimize(context, predictions).equals(optimizer.optimize(context, predictions)), "result must be deterministic");
    }

    private static void explanationMatchesSelectedCalculation() {
        NextBestActionResult result = optimizer(OptimizerConfig.defaults()).optimize(context(2_000, FailureType.TRANSIENT, 0),
                List.of(prediction(RecoveryAction.DELAYED_RETRY, .78)));
        ActionEvaluation evaluation = result.selectedEvaluation().orElseThrow();
        require(result.reason().contains("DELAYED_RETRY selected") && close(evaluation.score(), evaluation.expectedRecoveryValue()
                - evaluation.actionCost() - evaluation.frictionPenalty()), "explanation and calculation disagree");
    }

    private static void paymentAmountCanChangeDecision() {
        OptimizerConfig config = configWithEconomics(500, 0, 0, 0);
        var optimizer = optimizer(config);
        List<RecoveryPrediction> predictions = List.of(prediction(RecoveryAction.DELAYED_RETRY, .80),
                prediction(RecoveryAction.SEND_PAYMENT_LINK, .70));
        RecoveryAction lowAmount = optimizer.optimize(context(1_000, FailureType.TRANSIENT, 0), predictions).selectedAction().orElseThrow();
        RecoveryAction highAmount = optimizer.optimize(context(6_000, FailureType.TRANSIENT, 0), predictions).selectedAction().orElseThrow();
        require(lowAmount == RecoveryAction.SEND_PAYMENT_LINK && highAmount == RecoveryAction.DELAYED_RETRY,
                "recoverable amount should be able to alter the winning action");
    }

    private static OptimizerConfig configWithEconomics(double delayedCost, double delayedFriction, double linkCost, double linkFriction) {
        OptimizerConfig base = OptimizerConfig.defaults();
        Map<RecoveryAction, ActionEconomics> economics = new HashMap<>(base.economicsByAction());
        economics.put(RecoveryAction.DELAYED_RETRY, new ActionEconomics(delayedCost, delayedFriction));
        economics.put(RecoveryAction.SEND_PAYMENT_LINK, new ActionEconomics(linkCost, linkFriction));
        return new OptimizerConfig(base.maximumRetryCount(), economics, base.eligibleActionsByFailureType(), base.retryActions());
    }

    private static NextBestActionOptimizer optimizer(OptimizerConfig config) {
        return new NextBestActionOptimizer(config);
    }

    private static OptimizationContext context(int amount, FailureType failure, int retries) {
        return new OptimizationContext("pay_current", amount, failure, retries);
    }

    private static RecoveryPrediction prediction(RecoveryAction action, double probability) {
        return new RecoveryPrediction(action, probability, probability, 10, EvidenceLevel.FAILURE_ACTION, false, "test prediction");
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < .000001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
