package com.recoveriq.persistence;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="recovery_analysis",indexes=@Index(name="idx_analysis_customer",columnList="customerId")) public class RecoveryAnalysisEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(nullable=false) public String customerId; @Column(nullable=false,unique=true) public String paymentId; public int amount; @Column(nullable=false) public String failureType; public String recommendedAction; public double probability; public double expectedRecoveryValue; @Column(length=1000) public String reason; @Column(nullable=false) public Instant createdAt=Instant.now();
}
