package com.recoveriq.api;
import com.recoveriq.prediction.*; import com.recoveriq.optimizer.*; import com.recoveriq.synthetic.*; import com.recoveriq.persistence.*; import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class RecoveryApplicationService {
 private final ActionConditionedRecoveryPredictionEngine predictor=new ActionConditionedRecoveryPredictionEngine(List.of(),PredictionConfig.defaults()); private final NextBestActionOptimizer optimizer=new NextBestActionOptimizer(OptimizerConfig.defaults());
 private final RecoveryAnalysisRepository repository; public RecoveryApplicationService(RecoveryAnalysisRepository r){repository=r;}
 @Transactional public NextBestActionResult evaluate(String customer,String payment,int amount,FailureType failure,int attempt){ var ctx=new PredictionContext(customer,CustomerArchetype.RELIABLE,amount,failure,attempt+1); var actions=List.of(RecoveryAction.IMMEDIATE_RETRY,RecoveryAction.DELAYED_RETRY,RecoveryAction.SEND_PAYMENT_LINK,RecoveryAction.REQUEST_PAYMENT_METHOD_UPDATE,RecoveryAction.STOP_RECOVERY); var result=optimizer.optimize(new OptimizationContext(payment,amount,failure,attempt),predictor.predictAll(ctx,actions)); result.selectedEvaluation().ifPresent(e->{var x=repository.findByPaymentId(payment).orElseGet(RecoveryAnalysisEntity::new);x.customerId=customer;x.paymentId=payment;x.amount=amount;x.failureType=failure.name();x.recommendedAction=e.action().name();x.probability=e.predictedSuccessProbability();x.expectedRecoveryValue=e.expectedRecoveryValue();x.reason=result.reason();repository.save(x);}); return result; }
 public RecoveryAnalysisEntity find(String payment){return repository.findByPaymentId(payment).orElseThrow(()->new NoSuchElementException("analysis not found"));}
}
