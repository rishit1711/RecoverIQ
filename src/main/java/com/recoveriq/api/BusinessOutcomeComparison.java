package com.recoveriq.api;
import com.recoveriq.batch.BatchRecoveryEvaluator;
/** Presentation-only comparison derived from actual batch evaluation metrics. */
public record BusinessOutcomeComparison(StrategyOutcome doNothing,StrategyOutcome blindRetry,StrategyOutcome recoverIQ,double improvementVsBlindRetry){
 public record StrategyOutcome(long recoveredRevenue,double recoveryRate){ }
 public static BusinessOutcomeComparison from(BatchRecoveryEvaluator.Result r){return new BusinessOutcomeComparison(new StrategyOutcome(0,0),new StrategyOutcome(r.baselineRecoveredRevenue(),0),new StrategyOutcome(r.recoverIQRecoveredRevenue(),r.recoveryRate()),r.improvementPercentage());}
}
