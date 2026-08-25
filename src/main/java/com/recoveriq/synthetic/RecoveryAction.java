package com.recoveriq.synthetic;

public enum RecoveryAction {
    INITIAL_PAYMENT_ATTEMPT,
    IMMEDIATE_RETRY,
    DELAYED_RETRY,
    SEND_PAYMENT_LINK,
    REQUEST_PAYMENT_METHOD_UPDATE,
    STOP_RECOVERY
}
