package com.recoveriq.optimizer;

import com.recoveriq.synthetic.FailureType;
import com.recoveriq.synthetic.RecoveryAction;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Configurable constraints and economics for transparent next-best-action scoring. */
public record OptimizerConfig(
        int maximumRetryCount,
        Map<RecoveryAction, ActionEconomics> economicsByAction,
        Map<FailureType, Set<RecoveryAction>> eligibleActionsByFailureType,
        Set<RecoveryAction> retryActions) {
    public OptimizerConfig {
        if (maximumRetryCount < 0) throw new IllegalArgumentException("maximumRetryCount cannot be negative");
        economicsByAction = Map.copyOf(economicsByAction);
        retryActions = Set.copyOf(retryActions);
        Map<FailureType, Set<RecoveryAction>> eligibility = new EnumMap<>(FailureType.class);
        eligibleActionsByFailureType.forEach((failure, actions) -> eligibility.put(failure, Set.copyOf(actions)));
        eligibleActionsByFailureType = Map.copyOf(eligibility);
    }

    public static OptimizerConfig defaults() {
        Map<RecoveryAction, ActionEconomics> economics = Map.of(
                RecoveryAction.IMMEDIATE_RETRY, new ActionEconomics(10, 5),
                RecoveryAction.DELAYED_RETRY, new ActionEconomics(15, 8),
                RecoveryAction.SEND_PAYMENT_LINK, new ActionEconomics(25, 25),
                RecoveryAction.REQUEST_PAYMENT_METHOD_UPDATE, new ActionEconomics(30, 35),
                RecoveryAction.STOP_RECOVERY, new ActionEconomics(0, 0),
                RecoveryAction.INITIAL_PAYMENT_ATTEMPT, new ActionEconomics(0, 0));
        Map<FailureType, Set<RecoveryAction>> eligibility = new EnumMap<>(FailureType.class);
        eligibility.put(FailureType.TRANSIENT, EnumSet.of(RecoveryAction.IMMEDIATE_RETRY, RecoveryAction.DELAYED_RETRY,
                RecoveryAction.SEND_PAYMENT_LINK));
        eligibility.put(FailureType.INSUFFICIENT_FUNDS, EnumSet.of(RecoveryAction.DELAYED_RETRY, RecoveryAction.SEND_PAYMENT_LINK));
        eligibility.put(FailureType.AUTHENTICATION_REQUIRED, EnumSet.of(RecoveryAction.SEND_PAYMENT_LINK, RecoveryAction.DELAYED_RETRY));
        eligibility.put(FailureType.PAYMENT_METHOD_INVALID, EnumSet.of(RecoveryAction.REQUEST_PAYMENT_METHOD_UPDATE,
                RecoveryAction.SEND_PAYMENT_LINK));
        eligibility.put(FailureType.FRAUD_OR_RISK, EnumSet.of(RecoveryAction.STOP_RECOVERY));
        eligibility.put(FailureType.NONE, EnumSet.noneOf(RecoveryAction.class));
        return new OptimizerConfig(2, economics, eligibility,
                EnumSet.of(RecoveryAction.IMMEDIATE_RETRY, RecoveryAction.DELAYED_RETRY));
    }
}
