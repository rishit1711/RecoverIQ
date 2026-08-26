package com.recoveriq.persistence;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="recovery_event",indexes=@Index(name="idx_event_payment",columnList="paymentId")) public class RecoveryEventEntity { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(nullable=false) public String paymentId; @Column(nullable=false) public String action; public String previousState; public String newState; public int attemptNumber; public String outcome; @Column(nullable=false) public Instant occurredAt; }
