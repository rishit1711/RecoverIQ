package com.recoveriq.synthetic;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates correlated, reproducible payment histories. Customer traits are
 * durable; per-payment state causes both failure type and retry outcome.
 */
public final class SyntheticPaymentHistoryGenerator {
    private final GeneratorConfig config;
    private final Random random;

    public SyntheticPaymentHistoryGenerator(GeneratorConfig config) {
        this.config = config;
        this.random = new Random(config.seed());
    }

    public PaymentHistory generate() {
        List<Customer> customers = new ArrayList<>();
        List<Payment> payments = new ArrayList<>();
        for (int index = 1; index <= config.customerCount(); index++) {
            Customer customer = makeCustomer(index);
            customers.add(customer);
            payments.addAll(paymentsFor(customer));
        }
        return new PaymentHistory(customers, payments);
    }

    private Customer makeCustomer(int index) {
        CustomerArchetype archetype = weightedArchetype();
        double[] means = switch (archetype) {
            case RELIABLE -> new double[]{.82, .82, .90, .06};
            case CASH_FLOW_SENSITIVE -> new double[]{.43, .70, .78, .10};
            case DISENGAGED -> new double[]{.61, .25, .70, .12};
            case HIGH_RISK -> new double[]{.42, .35, .52, .62};
        };
        return new Customer("cus_%05d".formatted(index), archetype,
                boundedNormal(means[0]), boundedNormal(means[1]), boundedNormal(means[2]), boundedNormal(means[3]),
                weightedMethod());
    }

    private List<Payment> paymentsFor(Customer customer) {
        int count = random.nextInt(config.maxPaymentsPerCustomer() - config.minPaymentsPerCustomer() + 1)
                + config.minPaymentsPerCustomer();
        int firstDay = random.nextInt(Math.max(1, config.daysOfHistory() - 29));
        List<Payment> payments = new ArrayList<>();
        for (int sequence = 1; sequence <= count; sequence++) {
            boolean recurring = random.nextDouble() < config.recurringShare();
            int dayOffset = recurring ? firstDay + (sequence - 1) * 30 : random.nextInt(config.daysOfHistory());
            Instant dueAt = config.startAt().plus(Math.min(dayOffset, config.daysOfHistory() - 1), ChronoUnit.DAYS)
                    .plus(random.nextInt(24), ChronoUnit.HOURS);
            List<PaymentAttempt> attempts = attemptsFor(customer, dueAt);
            payments.add(new Payment("pay_%s_%03d".formatted(customer.customerId().substring(4), sequence),
                    customer.customerId(), amount(customer, recurring), config.currency(), recurring, dueAt,
                    attempts, attempts.getLast().status()));
        }
        return payments;
    }

    private List<PaymentAttempt> attemptsFor(Customer customer, Instant dueAt) {
        boolean fundsAvailable = random.nextDouble() < customer.financialStability();
        boolean methodValid = random.nextDouble() < customer.methodHealth();
        boolean authRequired = random.nextDouble() < .06 + (1 - customer.methodHealth()) * .14;
        boolean riskBlocked = random.nextDouble() < customer.riskPropensity() * .30;
        boolean transientFirst = random.nextDouble() < config.transientFailureRate();
        List<PaymentAttempt> attempts = new ArrayList<>();
        for (int number = 1; number <= config.maxRetriesPerPayment() + 1; number++) {
            long delay = number == 1 ? 0 : (long) config.retryInitialDelayHours() * (1L << (number - 2));
            boolean engaged = number > 1 && random.nextDouble() < customer.engagement() * Math.min(.95, .46 + .18 * number);
            FailureType failure = failureFor(number, fundsAvailable, methodValid, authRequired, riskBlocked, transientFirst, engaged);
            AttemptStatus status = failure == FailureType.NONE ? AttemptStatus.SUCCEEDED : AttemptStatus.FAILED;
            attempts.add(new PaymentAttempt(number, dueAt.plus(delay, ChronoUnit.HOURS), status, failure,
                    customer.preferredPaymentMethod(), engaged));
            if (status == AttemptStatus.SUCCEEDED || failure == FailureType.FRAUD_OR_RISK) break;
            if (failure == FailureType.INSUFFICIENT_FUNDS) {
                fundsAvailable = random.nextDouble() < .20 + .62 * customer.financialStability() + .10 * number;
            } else if ((failure == FailureType.AUTHENTICATION_REQUIRED || failure == FailureType.PAYMENT_METHOD_INVALID) && engaged) {
                methodValid = true;
                authRequired = false;
            }
        }
        return attempts;
    }

    private static FailureType failureFor(int number, boolean funds, boolean validMethod, boolean authenticationRequired,
            boolean riskBlocked, boolean transientFirst, boolean engaged) {
        if (riskBlocked) return FailureType.FRAUD_OR_RISK;
        if (!validMethod) return FailureType.PAYMENT_METHOD_INVALID;
        if (authenticationRequired && !engaged) return FailureType.AUTHENTICATION_REQUIRED;
        if (!funds) return FailureType.INSUFFICIENT_FUNDS;
        if (transientFirst && number == 1) return FailureType.TRANSIENT;
        return FailureType.NONE;
    }

    private int amount(Customer customer, boolean recurring) {
        double base = recurring ? 799 : 1499;
        double multiplier = .7 + customer.financialStability() * .75 + random.nextDouble() * .35;
        return (int) (Math.round(base * multiplier / 10) * 10);
    }

    private CustomerArchetype weightedArchetype() {
        double needle = random.nextDouble() * config.archetypeWeights().stream()
                .mapToDouble(GeneratorConfig.ArchetypeWeight::weight).sum();
        for (var choice : config.archetypeWeights()) {
            needle -= choice.weight();
            if (needle < 0) return choice.archetype();
        }
        return config.archetypeWeights().getLast().archetype();
    }

    private String weightedMethod() {
        double needle = random.nextDouble();
        if (needle < .56) return "card";
        return needle < .90 ? "upi" : "netbanking";
    }

    private double boundedNormal(double mean) {
        return Math.round(Math.min(.99, Math.max(.01, mean + random.nextGaussian() * .12)) * 1000d) / 1000d;
    }
}
