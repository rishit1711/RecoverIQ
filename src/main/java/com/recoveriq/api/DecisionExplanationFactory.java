package com.recoveriq.api;

import com.recoveriq.optimizer.*;
import com.recoveriq.synthetic.FailureType;
import java.util.List;

/** Converts existing optimizer calculations into stable, user-facing decision explanations. */
public final class DecisionExplanationFactory {
    public record TraceItem(String action,double successProbability,double expectedRecoveryValue,boolean selected,String status){ }
    public record ExplainedDecision(NextBestActionResult result,String explanation,List<TraceItem> trace){ }
    public static ExplainedDecision explain(FailureType failure,NextBestActionResult result){
        var trace=result.evaluatedActions().stream().map(e->new TraceItem(e.action().name(),e.predictedSuccessProbability(),e.expectedRecoveryValue(),e.eligible()&&result.selectedAction().filter(a->a==e.action()).isPresent(),e.reason())).toList();
        String explanation=result.selectedEvaluation().map(e->"%s selected for %s: %.1f%% predicted success and %.2f expected recovery; %s"
                .formatted(e.action(),failure,e.predictedSuccessProbability()*100,e.expectedRecoveryValue(),result.reason())).orElse(result.reason());
        return new ExplainedDecision(result,explanation,trace);
    }
    private DecisionExplanationFactory(){}
}
