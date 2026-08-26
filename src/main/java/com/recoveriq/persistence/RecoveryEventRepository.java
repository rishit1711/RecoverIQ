package com.recoveriq.persistence;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface RecoveryEventRepository extends JpaRepository<RecoveryEventEntity,Long>{List<RecoveryEventEntity> findByPaymentIdOrderByOccurredAtAsc(String paymentId);}
