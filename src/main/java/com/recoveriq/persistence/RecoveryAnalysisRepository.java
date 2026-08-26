package com.recoveriq.persistence;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface RecoveryAnalysisRepository extends JpaRepository<RecoveryAnalysisEntity,Long>{Optional<RecoveryAnalysisEntity> findByPaymentId(String paymentId); List<RecoveryAnalysisEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);}
