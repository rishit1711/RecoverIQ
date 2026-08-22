"""Reproducible, correlated synthetic payment histories.

The generator deliberately models shared causes instead of sampling output
columns independently.  Each customer has stable behavioural and financial
traits; each payment has a state (funds, method health, authentication and
risk); retries evolve that same state over time.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timedelta, timezone
from enum import Enum
import json
from pathlib import Path
import random
from typing import Iterator


class CustomerArchetype(str, Enum):
    RELIABLE = "reliable"
    CASH_FLOW_SENSITIVE = "cash_flow_sensitive"
    DISENGAGED = "disengaged"
    HIGH_RISK = "high_risk"


class FailureType(str, Enum):
    NONE = "none"
    TRANSIENT = "transient"
    INSUFFICIENT_FUNDS = "insufficient_funds"
    AUTHENTICATION_REQUIRED = "authentication_required"
    PAYMENT_METHOD_INVALID = "payment_method_invalid"
    FRAUD_OR_RISK = "fraud_or_risk"


class AttemptStatus(str, Enum):
    SUCCEEDED = "succeeded"
    FAILED = "failed"


@dataclass(frozen=True)
class GeneratorConfig:
    """Controls scale and operational assumptions for one generated dataset."""

    seed: int = 20260822
    customer_count: int = 250
    min_payments_per_customer: int = 3
    max_payments_per_customer: int = 12
    max_retries_per_payment: int = 3
    start_at: datetime = datetime(2025, 1, 1, tzinfo=timezone.utc)
    days_of_history: int = 365
    currency: str = "INR"
    recurring_share: float = 0.72
    archetype_weights: tuple[tuple[CustomerArchetype, float], ...] = (
        (CustomerArchetype.RELIABLE, 0.52),
        (CustomerArchetype.CASH_FLOW_SENSITIVE, 0.25),
        (CustomerArchetype.DISENGAGED, 0.15),
        (CustomerArchetype.HIGH_RISK, 0.08),
    )
    transient_failure_rate: float = 0.045
    retry_initial_delay_hours: int = 12

    def __post_init__(self) -> None:
        if self.customer_count < 1:
            raise ValueError("customer_count must be positive")
        if not 1 <= self.min_payments_per_customer <= self.max_payments_per_customer:
            raise ValueError("payment bounds must be positive and ordered")
        if self.max_retries_per_payment < 0 or self.days_of_history < 1:
            raise ValueError("retry count must be non-negative and history must be positive")
        if not 0 <= self.recurring_share <= 1:
            raise ValueError("recurring_share must be between zero and one")
        if not self.archetype_weights or any(weight < 0 for _, weight in self.archetype_weights):
            raise ValueError("archetype_weights must contain non-negative weights")
        if sum(weight for _, weight in self.archetype_weights) <= 0:
            raise ValueError("archetype_weights must have a positive total")
        if not 0 <= self.transient_failure_rate <= 1 or self.retry_initial_delay_hours < 1:
            raise ValueError("failure rate must be between zero and one and retry delay must be positive")


@dataclass(frozen=True)
class Customer:
    customer_id: str
    archetype: CustomerArchetype
    financial_stability: float
    engagement: float
    method_health: float
    risk_propensity: float
    preferred_payment_method: str


@dataclass(frozen=True)
class PaymentAttempt:
    attempt_number: int
    attempted_at: datetime
    status: AttemptStatus
    failure_type: FailureType
    payment_method: str
    customer_engaged: bool


@dataclass(frozen=True)
class Payment:
    payment_id: str
    customer_id: str
    amount: int
    currency: str
    is_recurring: bool
    due_at: datetime
    attempts: tuple[PaymentAttempt, ...]
    eventual_outcome: AttemptStatus


@dataclass(frozen=True)
class PaymentHistory:
    customers: tuple[Customer, ...]
    payments: tuple[Payment, ...]

    def to_jsonl(self, path: str | Path) -> None:
        """Write one payment-level record per line, including its attempts."""
        target = Path(path)
        with target.open("w", encoding="utf-8", newline="\n") as handle:
            for payment in self.payments:
                record = asdict(payment)
                record["due_at"] = payment.due_at.isoformat()
                record["eventual_outcome"] = payment.eventual_outcome.value
                for attempt in record["attempts"]:
                    attempt["attempted_at"] = attempt["attempted_at"].isoformat()
                    attempt["status"] = attempt["status"].value
                    attempt["failure_type"] = attempt["failure_type"].value
                handle.write(json.dumps(record, sort_keys=True) + "\n")


class SyntheticPaymentHistoryGenerator:
    """Produces reproducible histories whose columns arise from shared state."""

    def __init__(self, config: GeneratorConfig = GeneratorConfig()) -> None:
        self.config = config
        self._rng = random.Random(config.seed)

    def generate(self) -> PaymentHistory:
        """Generate a complete dataset. Recreate the generator for a fresh run."""
        customers = tuple(self._make_customer(index) for index in range(1, self.config.customer_count + 1))
        payments = tuple(payment for customer in customers for payment in self._payments_for(customer))
        return PaymentHistory(customers=customers, payments=payments)

    def _make_customer(self, index: int) -> Customer:
        archetype = self._weighted_choice(self.config.archetype_weights)
        # These distributions create persistent, correlated customer behaviour.
        profiles = {
            CustomerArchetype.RELIABLE: (0.82, 0.82, 0.90, 0.06),
            CustomerArchetype.CASH_FLOW_SENSITIVE: (0.43, 0.70, 0.78, 0.10),
            CustomerArchetype.DISENGAGED: (0.61, 0.25, 0.70, 0.12),
            CustomerArchetype.HIGH_RISK: (0.42, 0.35, 0.52, 0.62),
        }
        means = profiles[archetype]
        traits = [self._bounded_normal(mean, 0.12) for mean in means]
        return Customer(
            customer_id=f"cus_{index:05d}",
            archetype=archetype,
            financial_stability=traits[0],
            engagement=traits[1],
            method_health=traits[2],
            risk_propensity=traits[3],
            preferred_payment_method=self._weighted_choice((("card", 0.56), ("upi", 0.34), ("netbanking", 0.10))),
        )

    def _payments_for(self, customer: Customer) -> Iterator[Payment]:
        count = self._rng.randint(self.config.min_payments_per_customer, self.config.max_payments_per_customer)
        first_day = self._rng.randint(0, max(0, self.config.days_of_history - 30))
        for sequence in range(1, count + 1):
            recurring = self._rng.random() < self.config.recurring_share
            # Recurring payments have monthly cadence; one-off payments are scattered.
            day_offset = first_day + (sequence - 1) * 30 if recurring else self._rng.randrange(self.config.days_of_history)
            due_at = self.config.start_at + timedelta(days=min(day_offset, self.config.days_of_history - 1), hours=self._rng.randrange(24))
            amount = self._amount(customer, recurring)
            attempts = self._attempts_for(customer, due_at)
            yield Payment(
                payment_id=f"pay_{customer.customer_id[4:]}_{sequence:03d}",
                customer_id=customer.customer_id,
                amount=amount,
                currency=self.config.currency,
                is_recurring=recurring,
                due_at=due_at,
                attempts=tuple(attempts),
                eventual_outcome=attempts[-1].status,
            )

    def _attempts_for(self, customer: Customer, due_at: datetime) -> list[PaymentAttempt]:
        # Payment-level state is sampled once. It is the common cause of failures
        # and subsequent recovery, rather than independent output values.
        funds_available = self._rng.random() < customer.financial_stability
        method_valid = self._rng.random() < customer.method_health
        auth_required = self._rng.random() < (0.06 + (1 - customer.method_health) * 0.14)
        risk_blocked = self._rng.random() < customer.risk_propensity * 0.30
        transient_first = self._rng.random() < self.config.transient_failure_rate
        attempts: list[PaymentAttempt] = []

        for number in range(1, self.config.max_retries_per_payment + 2):
            delay_hours = 0 if number == 1 else self.config.retry_initial_delay_hours * (2 ** (number - 2))
            attempted_at = due_at + timedelta(hours=delay_hours)
            engaged = number > 1 and self._rng.random() < customer.engagement * min(0.95, 0.46 + 0.18 * number)
            failure = self._failure_for_attempt(
                number, funds_available, method_valid, auth_required, risk_blocked, transient_first, engaged
            )
            status = AttemptStatus.SUCCEEDED if failure is FailureType.NONE else AttemptStatus.FAILED
            attempts.append(PaymentAttempt(number, attempted_at, status, failure, customer.preferred_payment_method, engaged))
            if status is AttemptStatus.SUCCEEDED:
                break
            # State can evolve only through a plausible causal path.
            if failure is FailureType.INSUFFICIENT_FUNDS:
                funds_available = self._rng.random() < (0.20 + 0.62 * customer.financial_stability + 0.10 * number)
            elif failure in (FailureType.AUTHENTICATION_REQUIRED, FailureType.PAYMENT_METHOD_INVALID) and engaged:
                method_valid, auth_required = True, False
            if failure is FailureType.FRAUD_OR_RISK:
                break
        return attempts

    @staticmethod
    def _failure_for_attempt(
        number: int, funds: bool, method_valid: bool, auth_required: bool, risk_blocked: bool,
        transient_first: bool, engaged: bool,
    ) -> FailureType:
        if risk_blocked:
            return FailureType.FRAUD_OR_RISK
        if not method_valid:
            return FailureType.PAYMENT_METHOD_INVALID
        if auth_required and not engaged:
            return FailureType.AUTHENTICATION_REQUIRED
        if not funds:
            return FailureType.INSUFFICIENT_FUNDS
        if transient_first and number == 1:
            return FailureType.TRANSIENT
        return FailureType.NONE

    def _amount(self, customer: Customer, recurring: bool) -> int:
        base = 799 if recurring else 1_499
        # Reliable customers skew toward higher plan tiers, without making amount
        # an unrelated random field.
        multiplier = 0.7 + customer.financial_stability * 0.75 + self._rng.random() * 0.35
        return int(round(base * multiplier / 10) * 10)

    def _weighted_choice(self, choices: tuple[tuple[object, float], ...]):
        needle = self._rng.random()
        cumulative = 0.0
        for value, weight in choices:
            cumulative += weight
            if needle < cumulative:
                return value
        return choices[-1][0]

    def _bounded_normal(self, mean: float, deviation: float) -> float:
        return round(min(0.99, max(0.01, self._rng.gauss(mean, deviation))), 3)
