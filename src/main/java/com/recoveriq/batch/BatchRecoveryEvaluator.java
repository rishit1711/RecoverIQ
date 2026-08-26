package com.recoveriq.batch;

import com.recoveriq.optimizer.*;
import com.recoveriq.prediction.*;
import com.recoveriq.synthetic.*;
import com.recoveriq.workflow.RecoveryWorkflow;
import java.util.*;

/** Sequential, deterministic comparison of RecoverIQ against immediate-retry baseline. */
public final class BatchRecoveryEvaluator {
    public record EvaluationCase(RecoveryWorkflow.Case recoveryCase, CustomerArchetype behaviour) { }
    public record Result(int totalPayments, long revenueAtRisk, long baselineRecoveredRevenue, long recoverIQRecoveredRevenue,
            double recoveryRate, double revenueRecoveryRate, int interventionCount, int unproductiveAttempts, double improvementPercentage) { }
    private final ActionConditionedRecoveryPredictionEngine predictor; private final NextBestActionOptimizer optimizer;
    private final RecoveryWorkflow.Config workflowConfig; private final RecoveryWorkflow.Executor executor;
    public BatchRecoveryEvaluator(ActionConditionedRecoveryPredictionEngine p, NextBestActionOptimizer o, RecoveryWorkflow.Config c, RecoveryWorkflow.Executor e) { predictor=p; optimizer=o; workflowConfig=c; executor=e; }
    public Result evaluate(List<EvaluationCase> cases) {
        long risk=0, base=0, iq=0; int recovered=0, interventions=0, unproductive=0;
        for (EvaluationCase item : cases) { int amount=item.recoveryCase().amount(); risk+=amount;
            Run baseline=run(item, true); Run intelligent=run(item, false); if(baseline.recovered) base+=amount; if(intelligent.recovered){iq+=amount;recovered++;} interventions+=intelligent.actions; unproductive+=intelligent.failed;
        }
        return new Result(cases.size(),risk,base,iq,cases.isEmpty()?0:recovered/(double)cases.size(),risk==0?0:iq/(double)risk,interventions,unproductive,base==0?0:(iq-base)*100.0/base);
    }
    private Run run(EvaluationCase item, boolean baseline) {
        RecoveryWorkflow w=new RecoveryWorkflow(item.recoveryCase(),workflowConfig); int failed=0;
        while(!w.terminal()) { PredictionContext pc=new PredictionContext(item.recoveryCase().id(),item.behaviour(),item.recoveryCase().amount(),item.recoveryCase().failureType(),w.retryCount()+1);
            List<RecoveryAction> candidates=baseline?List.of(RecoveryAction.IMMEDIATE_RETRY):List.of(RecoveryAction.IMMEDIATE_RETRY,RecoveryAction.DELAYED_RETRY,RecoveryAction.SEND_PAYMENT_LINK,RecoveryAction.REQUEST_PAYMENT_METHOD_UPDATE,RecoveryAction.STOP_RECOVERY);
            List<RecoveryPrediction> predictions=predictor.predictAll(pc,candidates); RecoveryAction action;
            if(baseline) action=RecoveryAction.IMMEDIATE_RETRY; else { NextBestActionResult choice=optimizer.optimize(new OptimizationContext(item.recoveryCase().id(),item.recoveryCase().amount(),item.recoveryCase().failureType(),w.retryCount()),predictions); if(choice.selectedAction().isEmpty()){w.exhaust();break;} action=choice.selectedAction().get(); }
            double probability=predictions.stream().filter(p->p.action()==action).findFirst().orElseThrow().predictedSuccessProbability();
            try { if(w.run(action,probability,executor)!=RecoveryWorkflow.Outcome.SUCCESS) failed++; } catch(IllegalStateException ex){w.exhaust();}
        } return new Run(w.state()==RecoveryWorkflow.State.RECOVERED,w.events().stream().filter(e->e.outcome()==RecoveryWorkflow.Outcome.PENDING).count()>Integer.MAX_VALUE?0:(int)w.events().stream().filter(e->e.outcome()==RecoveryWorkflow.Outcome.PENDING).count(),failed);
    }
    private record Run(boolean recovered,int actions,int failed) { }
}
