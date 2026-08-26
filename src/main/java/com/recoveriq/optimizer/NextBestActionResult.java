package com.recoveriq.optimizer;

import com.recoveriq.synthetic.RecoveryAction;
import java.util.List;
import java.util.Optional;

/** Final Day 4 selection plus every evaluated candidate for auditability. */
public record NextBestActionResult(
        Optional<RecoveryAction> selectedAction,
        Optional<ActionEvaluation> selectedEvaluation,
        List<ActionEvaluation> evaluatedActions,
        String reason) {
    public NextBestActionResult {
        selectedAction = selectedAction == null ? Optional.empty() : selectedAction;
        selectedEvaluation = selectedEvaluation == null ? Optional.empty() : selectedEvaluation;
        evaluatedActions = List.copyOf(evaluatedActions);
    }
}
