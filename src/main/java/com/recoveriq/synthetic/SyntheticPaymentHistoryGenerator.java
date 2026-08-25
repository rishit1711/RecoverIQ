package com.recoveriq.synthetic;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates labelled historical data from shared customer and payment state.
 * It is deliberately independent of any future prediction or decision engine.
 */
public final class SyntheticPaymentHistoryGenerator {
    private final GeneratorConfig config;
    private final Random random;

    public SyntheticPaymentHistoryGenerator(GeneratorConfig config) {
        this.config = config;
        this.random = new Random(config.seed());
    }

    /** Produces exactly config.datasetSize() payment cases, with zero or more retries per case. */
    public PaymentHistory generate() {
        List<Customer> customers = new ArrayList<>();
        int[] firstDueDays = new int[config.customerCount()];
        int[] paymentSequences = new int[config.customerCount()];
        for (int index = 1; index <= config.customerCount(); index++) {
            customers.add(makeCustomer(index));
            firstDueDays[index - 1] = random.nextInt(config.daysOfHistory());
        }
        List<Payment> payments = new ArrayList<>();
        for (int caseNumber = 1; caseNumber <= config.datasetSize(); caseNumber++) {
            int customerIndex = random.nextInt(customers.size());
            Customer customer = customers.get(customerIndex);
            int sequence = ++paymentSequences[customerIndex];
            boolean recurring = random.nextDouble() < config.recurringShare();
            int day = recurring ? firstDueDays[customerIndex] + (sequence - 1) * 30 : random.nextInt(config.daysOfHistory());
            Instant dueAt = config.startAt().plus(Math.min(day, config.daysOfHistory() - 1), ChronoUnit.DAYS)
                    .plus(random.nextInt(24), ChronoUnit.HOURS);
            List<PaymentAttempt> attempts = attemptsFor(customer, dueAt);
            payments.add(new Payment("pay_%06d".formatted(caseNumber), customer.customerId(), amount(customer, recurring),
                    config.currency(), recurring, dueAt, attempts, attempts.getLast().status()));
        }
        return new PaymentHistory(customers, payments);
    }

    private Customer makeCustomer(int index) {
        CustomerArchetype archetype = weightedArchetype();
        CustomerBehaviourProfile profile = config.customerBehaviourProfiles().get(archetype);
        return new Customer("cus_%05d".formatted(index), archetype, boundedNormal(profile.financialStability()),
                boundedNormal(profile.engagement()), boundedNormal(profile.methodHealth()),
                boundedNormal(profile.riskPropensity()), weightedMethod());
    }

    private List<PaymentAttempt> attemptsFor(Customer customer, Instant dueAt) {
        boolean funds = random.nextDouble() < inverseScaled(customer.financialStability(), FailureType.INSUFFICIENT_FUNDS);
        boolean validMethod = random.nextDouble() < inverseScaled(customer.methodHealth(), FailureType.PAYMENT_METHOD_INVALID);
        boolean authenticationRequired = random.nextDouble() < scaled(.06 + (1 - customer.methodHealth()) * .14,
                FailureType.AUTHENTICATION_REQUIRED);
        boolean riskBlocked = random.nextDouble() < scaled(customer.riskPropensity() * .30, FailureType.FRAUD_OR_RISK);
        boolean transientFirst = random.nextDouble() < scaled(config.transientFailureRate(), FailureType.TRANSIENT);
        List<PaymentAttempt> attempts = new ArrayList<>();

        for (int number = 1; number <= config.maxRetriesPerPayment() + 1; number++) {
            FailureType expectedFailure = failureFor(number, funds, validMethod, authenticationRequired, riskBlocked, transientFirst, false);
            RecoveryAction action = actionFor(number, expectedFailure);
            boolean engaged = number > 1 && random.nextDouble() < engagementChance(customer, number, action);
            FailureType failure = failureFor(number, funds, validMethod, authenticationRequired, riskBlocked, transientFirst, engaged);
            AttemptStatus outcome = failure == FailureType.NONE ? AttemptStatus.SUCCEEDED : AttemptStatus.FAILED;
            long delay = number == 1 ? 0 : (long) config.retryInitialDelayHours() * (1L << (number - 2));
            attempts.add(new PaymentAttempt(number, dueAt.plus(delay, ChronoUnit.HOURS), outcome, failure, action,
                    customer.preferredPaymentMethod(), engaged));
            if (outcome == AttemptStatus.SUCCEEDED || failure == FailureType.FRAUD_OR_RISK) break;

            if (failure == FailureType.INSUFFICIENT_FUNDS) {
                double recoveryChance = (.20 + .62 * customer.financialStability() + .10 * number) * successMultiplier(action);
                funds = random.nextDouble() < clamp(recoveryChance);
            } else if ((failure == FailureType.AUTHENTICATION_REQUIRED || failure == FailureType.PAYMENT_METHOD_INVALID)
                    && engaged && (action == RecoveryAction.SEND_PAYMENT_LINK || action == RecoveryAction.REQUEST_PAYMENT_METHOD_UPDATE)) {
                validMethod = true;
                authenticationRequired = false;
            }
        }
        return attempts;
    }

    private RecoveryAction actionFor(int attemptNumber, FailureType failure) {
        if (attemptNumber == 1) return RecoveryAction.INITIAL_PAYMENT_ATTEMPT;
        if (failure == FailureType.FRAUD_OR_RISK) return RecoveryAction.STOP_RECOVERY;
        List<RecoveryAction> allowed = switch (failure) {
            case TRANSIENT -> List.of(RecoveryAction.IMMEDIATE_RETRY, RecoveryAction.DELAYED_RETRY);
            case INSUFFICIENT_FUNDS -> List.of(RecoveryAction.DELAYED_RETRY, RecoveryAction.SEND_PAYMENT_LINK);
            case AUTHENTICATION_REQUIRED -> List.of(RecoveryAction.SEND_PAYMENT_LINK, RecoveryAction.DELAYED_RETRY);
            case PAYMENT_METHOD_INVALID -> List.of(RecoveryAction.REQUEST_PAYMENT_METHOD_UPDATE, RecoveryAction.SEND_PAYMENT_LINK);
            default -> List.of(RecoveryAction.IMMEDIATE_RETRY);
        };
        return weightedAction(allowed);
    }

    private static FailureType failureFor(int number, boolean funds, boolean validMethod, boolean authRequired,
            boolean riskBlocked, boolean transientFirst, boolean engaged) {
        if (riskBlocked) return FailureType.FRAUD_OR_RISK;
        if (!validMethod) return FailureType.PAYMENT_METHOD_INVALID;
        if (authRequired && !engaged) return FailureType.AUTHENTICATION_REQUIRED;
        if (!funds) return FailureType.INSUFFICIENT_FUNDS;
        if (transientFirst && number == 1) return FailureType.TRANSIENT;
        return FailureType.NONE;
    }

    private double engagementChance(Customer customer, int number, RecoveryAction action) {
        return clamp(customer.engagement() * Math.min(.95, .46 + .18 * number) * successMultiplier(action));
    }

    private double scaled(double probability, FailureType type) {
        return clamp(probability * config.failureTypeMultipliers().getOrDefault(type, 1.0));
    }

    private double inverseScaled(double probability, FailureType type) {
        return clamp(probability / config.failureTypeMultipliers().getOrDefault(type, 1.0));
    }

    private double successMultiplier(RecoveryAction action) {
        return config.actionSuccessMultipliers().getOrDefault(action, 1.0);
    }

    private RecoveryAction weightedAction(List<RecoveryAction> allowed) {
        double total = allowed.stream().mapToDouble(action -> config.actionSelectionWeights().getOrDefault(action, 1.0)).sum();
        double needle = random.nextDouble() * total;
        for (RecoveryAction action : allowed) {
            needle -= config.actionSelectionWeights().getOrDefault(action, 1.0);
            if (needle < 0) return action;
        }
        return allowed.getLast();
    }

    private int amount(Customer customer, boolean recurring) {
        double base = recurring ? 799 : 1_499;
        return (int) (Math.round(base * (.7 + customer.financialStability() * .75 + random.nextDouble() * .35) / 10) * 10);
    }

    private CustomerArchetype weightedArchetype() {
        double total = config.archetypeWeights().stream().mapToDouble(GeneratorConfig.ArchetypeWeight::weight).sum();
        double needle = random.nextDouble() * total;
        for (var choice : config.archetypeWeights()) {
            needle -= choice.weight();
            if (needle < 0) return choice.archetype();
        }
        return config.archetypeWeights().getLast().archetype();
    }

    private String weightedMethod() {
        double value = random.nextDouble();
        return value < .56 ? "card" : value < .90 ? "upi" : "netbanking";
    }

    private double boundedNormal(double mean) {
        return Math.round(clamp(mean + random.nextGaussian() * .12) * 1_000d) / 1_000d;
    }

    private static double clamp(double value) {
        return Math.min(.99, Math.max(.01, value));
    }
}
