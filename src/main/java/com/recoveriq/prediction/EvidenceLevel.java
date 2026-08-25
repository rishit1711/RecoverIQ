package com.recoveriq.prediction;

/** The deterministic backoff level that supplied a prediction's evidence. */
public enum EvidenceLevel {
    CUSTOMER_FAILURE_ACTION,
    CUSTOMER_ACTION,
    SEGMENT_FAILURE_ACTION,
    FAILURE_ACTION,
    GLOBAL_ACTION,
    GLOBAL_PRIOR
}
