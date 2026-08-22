import unittest

from recoveriq.synthetic_history import (
    AttemptStatus,
    CustomerArchetype,
    FailureType,
    GeneratorConfig,
    SyntheticPaymentHistoryGenerator,
)


def history(seed: int = 17):
    return SyntheticPaymentHistoryGenerator(
        GeneratorConfig(seed=seed, customer_count=400, min_payments_per_customer=5, max_payments_per_customer=5)
    ).generate()


class SyntheticPaymentHistoryGeneratorTests(unittest.TestCase):
    def test_same_seed_produces_identical_history(self):
        self.assertEqual(history(123), history(123))
        self.assertNotEqual(history(123), history(124))

    def test_attempts_are_a_causal_sequence_not_independent_draws(self):
        for payment in history().payments:
            self.assertEqual(payment.eventual_outcome, payment.attempts[-1].status)
            self.assertEqual(payment.attempts[0].attempt_number, 1)
            self.assertTrue(all(
                later.attempt_number == earlier.attempt_number + 1
                and later.attempted_at > earlier.attempted_at
                for earlier, later in zip(payment.attempts, payment.attempts[1:])
            ))
            self.assertTrue(all(attempt.status is AttemptStatus.FAILED for attempt in payment.attempts[:-1]))

    def test_failure_type_is_correlated_with_eventual_recovery(self):
        data = history().payments
        transient = [p for p in data if p.attempts[0].failure_type is FailureType.TRANSIENT]
        invalid = [p for p in data if p.attempts[0].failure_type is FailureType.PAYMENT_METHOD_INVALID]
        self.assertTrue(transient)
        self.assertTrue(invalid)
        transient_recovery = sum(p.eventual_outcome is AttemptStatus.SUCCEEDED for p in transient) / len(transient)
        invalid_recovery = sum(p.eventual_outcome is AttemptStatus.SUCCEEDED for p in invalid) / len(invalid)
        self.assertGreater(transient_recovery, invalid_recovery)

    def test_customer_archetypes_have_distinct_failure_patterns(self):
        data = history()
        by_id = {customer.customer_id: customer for customer in data.customers}
        reliable = [p for p in data.payments if by_id[p.customer_id].archetype is CustomerArchetype.RELIABLE]
        cash_flow = [p for p in data.payments if by_id[p.customer_id].archetype is CustomerArchetype.CASH_FLOW_SENSITIVE]
        reliable_funds = sum(p.attempts[0].failure_type is FailureType.INSUFFICIENT_FUNDS for p in reliable) / len(reliable)
        cash_flow_funds = sum(p.attempts[0].failure_type is FailureType.INSUFFICIENT_FUNDS for p in cash_flow) / len(cash_flow)
        self.assertGreater(cash_flow_funds, reliable_funds)
