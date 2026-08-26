package com.recoveriq.api;
import com.recoveriq.optimizer.*; import com.recoveriq.synthetic.FailureType; import jakarta.validation.constraints.*; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api") public class RecoveryController {
 private final RecoveryApplicationService service; public RecoveryController(RecoveryApplicationService s){service=s;}
 @GetMapping("/health") public java.util.Map<String,String> health(){return java.util.Map.of("status","ok");}
 @PostMapping("/recovery/evaluate") public ResponseEntity<?> evaluate(@RequestBody @jakarta.validation.Valid Request r){ NextBestActionResult x=service.evaluate(r.customerId,r.paymentId,r.amount,FailureType.valueOf(r.failureType),r.attemptNumber); if(x.selectedEvaluation().isEmpty())return ResponseEntity.status(422).body(java.util.Map.of("message",x.reason())); var e=x.selectedEvaluation().get(); return ResponseEntity.ok(java.util.Map.of("paymentId",r.paymentId,"recommendedAction",e.action(),"predictedSuccessProbability",e.predictedSuccessProbability(),"expectedRecoveryValue",e.expectedRecoveryValue(),"reason",x.reason())); }
 @GetMapping("/recovery/{paymentId}") public Object get(@PathVariable String paymentId){return service.find(paymentId);}
 @PostMapping("/recovery/run") public Object run(@RequestBody @jakarta.validation.Valid Request r){return service.run(r.customerId,r.paymentId,r.amount,FailureType.valueOf(r.failureType),r.attemptNumber);}
 @PostMapping("/recovery/evaluate/batch") public Object batch(@RequestBody @jakarta.validation.Valid BatchRequest r){return service.batch(r.datasetSize,r.seed);}
 @GetMapping("/recovery/{paymentId}/events") public Object events(@PathVariable String paymentId){return service.history(paymentId);}
 public static record Request(@NotBlank String customerId,@NotBlank String paymentId,@Positive int amount,@NotBlank String failureType,@Min(0) int attemptNumber){}
 public static record BatchRequest(@Min(1) @Max(10000) int datasetSize,long seed){}
}
