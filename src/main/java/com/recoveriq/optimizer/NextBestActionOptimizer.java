package com.recoveriq.optimizer;

import com.recoveriq.prediction.RecoveryPrediction;
import com.recoveriq.synthetic.RecoveryAction;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Selects among supplied Day 3 probabilities. It never reads historical data
 * or calculates predictions itself.
 */
public final class NextBestActionOptimizer {
    private final OptimizerConfig config;

    public NextBestActionOptimizer(OptimizerConfig config) {
        this.config = config;
    }

    public NextBestActionResult optimize(OptimizationContext context, List<RecoveryPrediction> predictions) {
        List<ActionEvaluation> evaluations = predictions.stream().map(prediction -> evaluate(context, prediction)).toList();
        Optional<ActionEvaluation> selected = evaluations.stream().filter(ActionEvaluation::eligible)
                .max(Comparator.comparingDouble(ActionEvaluation::score).thenComparing(evaluation -> evaluation.action().name()));
        if (selected.isEmpty()) {
            return new NextBestActionResult(Optional.empty(), Optional.empty(), evaluations,
                    "No candidate action is eligible under the configured failure-type and retry constraints.");
        }
        ActionEvaluation winner = selected.get();
        String reason = "%s selected: expected recovery %.2f - cost %.2f - friction %.2f = score %.2f."
                .formatted(winner.action(), winner.expectedRecoveryValue(), winner.actionCost(), winner.frictionPenalty(), winner.score());
        return new NextBestActionResult(Optional.of(winner.action()), Optional.of(winner), evaluations, reason);
    }

    private ActionEvaluation evaluate(OptimizationContext context, RecoveryPrediction prediction) {
        RecoveryAction action = prediction.action();
        if (prediction.predictedSuccessProbability() < 0 || prediction.predictedSuccessProbability() > 1) {
            return ineligible(prediction, "Prediction probability is outside the valid range.");
        }
        if (!config.eligibleActionsByFailureType().getOrDefault(context.failureType(), java.util.Set.of()).contains(action)) {
            return ineligible(prediction, "Action is not eligible for failure type " + context.failureType() + ".");
        }
        if (config.retryActions().contains(action) && context.retryCount() >= config.maximumRetryCount()) {
            return ineligible(prediction, "Retry limit of " + config.maximumRetryCount() + " has been reached.");
        }
        ActionEconomics economics = config.economicsByAction().get(action);
        if (economics == null) return ineligible(prediction, "No configured cost and friction values exist for this action.");
        double expectedRecovery = prediction.predictedSuccessProbability() * context.recoverableAmount();
        double score = expectedRecovery - economics.actionCost() - economics.frictionPenalty();
        String reason = "Eligible: probability %.1f%% × amount %d = %.2f, minus cost %.2f and friction %.2f."
                .formatted(prediction.predictedSuccessProbability() * 100, context.recoverableAmount(), expectedRecovery,
                        economics.actionCost(), economics.frictionPenalty());
        return new ActionEvaluation(action, prediction.predictedSuccessProbability(), expectedRecovery, economics.actionCost(),
                economics.frictionPenalty(), score, true, reason);
    }

    private static ActionEvaluation ineligible(RecoveryPrediction prediction, String reason) {
        return new ActionEvaluation(prediction.action(), prediction.predictedSuccessProbability(), 0, 0, 0,
                Double.NEGATIVE_INFINITY, false, reason);
    }
}
