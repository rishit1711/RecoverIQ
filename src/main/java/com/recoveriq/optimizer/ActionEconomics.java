package com.recoveriq.optimizer;

/** Explicit monetary cost and friction penalty for one recovery action. */
public record ActionEconomics(double actionCost, double frictionPenalty) {
    public ActionEconomics {
        if (actionCost < 0 || frictionPenalty < 0) throw new IllegalArgumentException("costs cannot be negative");
    }
}
