package com.recoveriq.api;
import com.recoveriq.optimizer.*; import com.recoveriq.prediction.*; import com.recoveriq.synthetic.*; import java.util.*;
/** Unit checks for explanation/trace projection; it does not alter the optimizer. */
public final class DecisionExplanationFactoryTest {
 public static void main(String[] args){var optimizer=new NextBestActionOptimizer(OptimizerConfig.defaults());var result=optimizer.optimize(new OptimizationContext("p",2000,FailureType.TRANSIENT,0),List.of(p(RecoveryAction.IMMEDIATE_RETRY,.55),p(RecoveryAction.DELAYED_RETRY,.78),p(RecoveryAction.SEND_PAYMENT_LINK,.64)));var explained=DecisionExplanationFactory.explain(FailureType.TRANSIENT,result);if(!explained.explanation().contains("DELAYED_RETRY")||explained.trace().size()!=3||!explained.trace().stream().anyMatch(t->t.selected()&&t.action().equals("DELAYED_RETRY")))throw new AssertionError("invalid explanation trace");System.out.println("1 check passed");}
 private static RecoveryPrediction p(RecoveryAction a,double v){return new RecoveryPrediction(a,v,v,10,EvidenceLevel.FAILURE_ACTION,false,"test");}
}
