package com.recoveriq.synthetic;

public record Customer(
        String customerId,
        CustomerArchetype archetype,
        double financialStability,
        double engagement,
        double methodHealth,
        double riskPropensity,
        String preferredPaymentMethod) { }
