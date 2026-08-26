package com.recoveriq.workflow;

import com.recoveriq.synthetic.FailureType;
import com.recoveriq.synthetic.RecoveryAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Bounded in-memory recovery state machine; no payment provider integration. */
public final class RecoveryWorkflow {
    public enum State { FAILED, RECOVERY_IN_PROGRESS, RETRY_SCHEDULED, PAYMENT_LINK_SENT, RECOVERED, EXHAUSTED }
    public enum Outcome { PENDING, SUCCESS, FAILED, PERMANENT_FAILURE }
    public record Case(String id, int amount, FailureType failureType, Instant createdAt) { }
    public record Event(String caseId, RecoveryAction action, State previousState, State newState, int attemptNumber, Outcome outcome, Instant timestamp) { }
    public record Config(int maximumRetries, int maximumActions) { }
    public interface Executor { Outcome execute(Case recoveryCase, RecoveryAction action, double probability, int attemptNumber); }

    /** Stable pseudo-execution: same case and attempt always yield the same simulated outcome. */
    public static final class DeterministicExecutor implements Executor {
        public Outcome execute(Case c, RecoveryAction action, double probability, int attempt) {
            if (c.failureType() == FailureType.FRAUD_OR_RISK) return Outcome.PERMANENT_FAILURE;
            long hash = 1125899906842597L;
            for (char ch : (c.id() + ":" + attempt).toCharArray()) hash = 31 * hash + ch;
            double unit = (hash & Long.MAX_VALUE) / (double) Long.MAX_VALUE;
            return unit < probability ? Outcome.SUCCESS : Outcome.FAILED;
        }
    }

    private final Case recoveryCase; private final Config config; private State state; private int retries; private int actions;
    private RecoveryAction activeAction; private final List<Event> events = new ArrayList<>();
    public RecoveryWorkflow(Case recoveryCase, Config config) {
        this.recoveryCase = recoveryCase; this.config = config;
        this.state = recoveryCase.failureType() == FailureType.FRAUD_OR_RISK ? State.EXHAUSTED : State.FAILED;
    }
    public State state() { return state; }
    public List<Event> events() { return List.copyOf(events); }
    public int retryCount() { return retries; }
    public boolean terminal() { return state == State.RECOVERED || state == State.EXHAUSTED; }
    public Outcome run(RecoveryAction action, double probability, Executor executor) {
        start(action); Outcome outcome = executor.execute(recoveryCase, action, probability, actions); complete(outcome); return outcome;
    }
    public void start(RecoveryAction action) {
        if (terminal()) throw new IllegalStateException("terminal recovery cases cannot accept actions");
        if (activeAction != null) throw new IllegalStateException("a recovery action is already in progress");
        if (actions >= config.maximumActions()) { state = State.EXHAUSTED; throw new IllegalStateException("maximum recovery actions reached"); }
        if (isRetry(action) && retries >= config.maximumRetries()) throw new IllegalStateException("maximum retries reached");
        State previous = state; activeAction = action; actions++; if (isRetry(action)) retries++;
        state = action == RecoveryAction.DELAYED_RETRY ? State.RETRY_SCHEDULED : action == RecoveryAction.SEND_PAYMENT_LINK
                || action == RecoveryAction.REQUEST_PAYMENT_METHOD_UPDATE ? State.PAYMENT_LINK_SENT : State.RECOVERY_IN_PROGRESS;
        event(action, previous, state, Outcome.PENDING);
    }
    public void complete(Outcome outcome) {
        if (activeAction == null) throw new IllegalStateException("no action is in progress");
        State previous = state; RecoveryAction action = activeAction; activeAction = null;
        state = outcome == Outcome.SUCCESS ? State.RECOVERED : outcome == Outcome.PERMANENT_FAILURE || actions >= config.maximumActions() ? State.EXHAUSTED : State.FAILED;
        event(action, previous, state, outcome);
    }
    public void exhaust() { if (!terminal()) state = State.EXHAUSTED; }
    private void event(RecoveryAction a, State from, State to, Outcome o) { events.add(new Event(recoveryCase.id(), a, from, to, actions, o, recoveryCase.createdAt().plusSeconds(events.size()))); }
    private static boolean isRetry(RecoveryAction a) { return a == RecoveryAction.IMMEDIATE_RETRY || a == RecoveryAction.DELAYED_RETRY; }
}
