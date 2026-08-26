package com.recoveriq.api;
import com.recoveriq.prediction.*; import com.recoveriq.optimizer.*; import com.recoveriq.synthetic.*; import java.util.*;
import org.springframework.stereotype.Service;
@Service public class RecoveryApplicationService {
 private final ActionConditionedRecoveryPredictionEngine predictor=new ActionConditionedRecoveryPredictionEngine(List.of(),PredictionConfig.defaults()); private final NextBestActionOptimizer optimizer=new NextBestActionOptimizer(OptimizerConfig.defaults());
 public NextBestActionResult evaluate(String customer,String payment,int amount,FailureType failure,int attempt){ var ctx=new PredictionContext(customer,CustomerArchetype.RELIABLE,amount,failure,attempt+1); var actions=List.of(RecoveryAction.IMMEDIATE_RETRY,RecoveryAction.DELAYED_RETRY,RecoveryAction.SEND_PAYMENT_LINK,RecoveryAction.REQUEST_PAYMENT_METHOD_UPDATE,RecoveryAction.STOP_RECOVERY); return optimizer.optimize(new OptimizationContext(payment,amount,failure,attempt),predictor.predictAll(ctx,actions)); }
}
