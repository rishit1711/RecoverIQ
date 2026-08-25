# RecoverIQ synthetic payment histories

The repository includes dependency-free Python and Java generators for
synthetic payment histories that exercise RecoverIQ's
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

## Java

The Java implementation lives in `src/main/java/com/recoveriq/synthetic` and
requires Java 21. Compile and run its tests without a build tool:

```powershell
javac -d out (Get-ChildItem -Recurse src/main/java,src/test/java -Filter *.java | ForEach-Object FullName)
java -cp out com.recoveriq.synthetic.SyntheticPaymentHistoryGeneratorTest
```

Use `new SyntheticPaymentHistoryGenerator(GeneratorConfig.defaults()).generate()`.
The Java configuration supports an exact `datasetSize` (20, 1,000, 10,000+),
customer count, seed, retry limit and timing, failure-type multipliers, action
selection weights, action-success multipliers, and customer archetype weights.
It also exposes each archetype's stability, engagement, payment-method health,
and risk means through `customerBehaviourProfiles`.

`PaymentHistory.records()` returns flat `HistoricalRecoveryRecord` values with
the customer/payment IDs, amount, behaviour, failure type, attempt number,
action, ground-truth outcome and timestamp. `PaymentHistory.statistics()`
returns distribution and recovery-rate checks. `writeJsonLines(path)` exports
one such labelled record per line for the Day 3 prediction layer.
