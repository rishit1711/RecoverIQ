package com.recoveriq.synthetic;

/** Mean latent traits for an archetype; all values must be probabilities. */
public record CustomerBehaviourProfile(
        double financialStability,
        double engagement,
        double methodHealth,
        double riskPropensity) {
    public CustomerBehaviourProfile {
        if (financialStability < 0 || financialStability > 1 || engagement < 0 || engagement > 1
                || methodHealth < 0 || methodHealth > 1 || riskPropensity < 0 || riskPropensity > 1) {
            throw new IllegalArgumentException("customer behaviour means must be probabilities");
        }
    }
}
