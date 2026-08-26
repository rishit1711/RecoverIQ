package com.recoveriq.workflow;

import com.recoveriq.batch.BatchRecoveryEvaluator;
import com.recoveriq.optimizer.*;
import com.recoveriq.prediction.*;
import com.recoveriq.synthetic.*;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/** Core Day 5–6 checks, including bounded transitions and deterministic aggregation. */
public final class RecoveryWorkflowAndBatchTest {
    public static void main(String[] args) {
        validAndInvalidTransitions(); successAndPermanentFailureTerminate(); retryLimitAndAuditEvents(); duplicateInProgressActionRejected(); batchIsDeterministicAndAggregates(); emptyBatchIsSafe(); tenThousandCasesComplete(); System.out.println("7 checks passed");
    }
    private static RecoveryWorkflow workflow(FailureType f) { return new RecoveryWorkflow(new RecoveryWorkflow.Case("case",2000,f,Instant.EPOCH),new RecoveryWorkflow.Config(2,3)); }
    private static void validAndInvalidTransitions() { RecoveryWorkflow w=workflow(FailureType.TRANSIENT); w.start(RecoveryAction.DELAYED_RETRY); require(w.state()==RecoveryWorkflow.State.RETRY_SCHEDULED,"scheduled state"); w.complete(RecoveryWorkflow.Outcome.FAILED); require(w.state()==RecoveryWorkflow.State.FAILED,"failed state"); try{w.complete(RecoveryWorkflow.Outcome.SUCCESS);throw new AssertionError();}catch(IllegalStateException expected){} }
    private static void successAndPermanentFailureTerminate() { RecoveryWorkflow w=workflow(FailureType.TRANSIENT); w.run(RecoveryAction.IMMEDIATE_RETRY,1,new RecoveryWorkflow.DeterministicExecutor()); require(w.state()==RecoveryWorkflow.State.RECOVERED,"success terminal"); try{w.start(RecoveryAction.DELAYED_RETRY);throw new AssertionError();}catch(IllegalStateException expected){} require(workflow(FailureType.FRAUD_OR_RISK).state()==RecoveryWorkflow.State.EXHAUSTED,"permanent failure"); }
    private static void retryLimitAndAuditEvents() { RecoveryWorkflow w=workflow(FailureType.TRANSIENT); w.run(RecoveryAction.IMMEDIATE_RETRY,0,new RecoveryWorkflow.DeterministicExecutor()); w.run(RecoveryAction.DELAYED_RETRY,0,new RecoveryWorkflow.DeterministicExecutor()); try{w.start(RecoveryAction.IMMEDIATE_RETRY);throw new AssertionError();}catch(IllegalStateException expected){} require(w.events().size()==4,"every action audited"); }
    private static void duplicateInProgressActionRejected() { RecoveryWorkflow w=workflow(FailureType.TRANSIENT); w.start(RecoveryAction.IMMEDIATE_RETRY); try{w.start(RecoveryAction.IMMEDIATE_RETRY);throw new AssertionError();}catch(IllegalStateException expected){} }
    private static void batchIsDeterministicAndAggregates() { BatchRecoveryEvaluator e=evaluator(); List<BatchRecoveryEvaluator.EvaluationCase> cases=List.of(item("a",1000),item("b",2000)); var one=e.evaluate(cases); var two=e.evaluate(cases); require(one.equals(two)&&one.totalPayments()==2&&one.revenueAtRisk()==3000&&one.interventionCount()>=0,"batch metrics"); }
    private static void emptyBatchIsSafe() { var r=evaluator().evaluate(List.of()); require(r.totalPayments()==0&&r.revenueAtRisk()==0&&r.recoveryRate()==0,"empty batch"); }
    private static void tenThousandCasesComplete() { var cases=new ArrayList<BatchRecoveryEvaluator.EvaluationCase>(); for(int i=0;i<10_000;i++) cases.add(item("large"+i,1000+(i%3)*500)); var result=evaluator().evaluate(cases); require(result.totalPayments()==10_000&&result.revenueAtRisk()>0,"10k batch"); }
    private static BatchRecoveryEvaluator evaluator() { var predictor=new ActionConditionedRecoveryPredictionEngine(List.of(),PredictionConfig.defaults()); return new BatchRecoveryEvaluator(predictor,new NextBestActionOptimizer(OptimizerConfig.defaults()),new RecoveryWorkflow.Config(2,3),new RecoveryWorkflow.DeterministicExecutor()); }
    private static BatchRecoveryEvaluator.EvaluationCase item(String id,int amount) { return new BatchRecoveryEvaluator.EvaluationCase(new RecoveryWorkflow.Case(id,amount,FailureType.TRANSIENT,Instant.EPOCH),CustomerArchetype.RELIABLE); }
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
