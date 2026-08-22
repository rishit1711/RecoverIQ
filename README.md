# RecoverIQ synthetic payment histories

This package creates synthetic payment histories for exercising RecoverIQ's
decision engine. It intentionally models dependencies: a customer's latent
financial stability, engagement, payment-method health and risk propensity
drive their payments; payment-level funds, method, authentication and risk
state drive failure and retry outcomes.

```python
from recoveriq import GeneratorConfig, SyntheticPaymentHistoryGenerator

config = GeneratorConfig(seed=42, customer_count=1_000, max_retries_per_payment=3)
history = SyntheticPaymentHistoryGenerator(config).generate()
history.to_jsonl("payment-history.jsonl")
```

Reusing the same configuration and seed produces an equal history. Change the
seed to create another deterministic dataset. `archetype_weights`,
`transient_failure_rate`, and `retry_initial_delay_hours` allow scenario-level
configuration without disconnecting the causal relationships. Each JSONL record
is one payment with its complete ordered attempt sequence and eventual outcome.

Run the tests with `python -m unittest discover -s tests`.
