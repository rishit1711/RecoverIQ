# RecoverIQ

### Explainable Payment Recovery Decision Engine

> **Don't blindly retry failed payments. Recover them intelligently.**

RecoverIQ is a data-driven payment recovery decision engine designed to help merchants make smarter decisions when payments fail.

Instead of applying the same retry strategy to every failed payment, RecoverIQ analyzes payment context, customer behavior, failure type, previous attempts, and available recovery actions to determine the **next best recovery action**.

---

## 🚀 The Problem

When a payment fails, many systems follow a simple approach:

```text
Payment Failed → Retry → Retry Again
```

But not every payment failure has the same cause.

A payment can fail due to:

* Insufficient funds
* Temporary payment failures
* Invalid payment methods
* Authentication requirements
* Other payment-related conditions
* Fraud or risk signals

Blindly retrying every failed payment can lead to:

* Lost revenue
* Unnecessary retry attempts
* Additional operational cost
* Increased customer friction
* Poor recovery efficiency

**RecoverIQ replaces blind retries with context-aware recovery decisions.**

---

## 💡 What RecoverIQ Does

RecoverIQ follows a simple three-stage approach:

```text
              Failed Payment
                    │
                    ▼
          ┌───────────────────┐
          │  Analyze Context  │
          └─────────┬─────────┘
                    │
                    ▼
          ┌───────────────────┐
          │      Predict      │
          │ Success by Action │
          └─────────┬─────────┘
                    │
                    ▼
          ┌───────────────────┐
          │     Optimize      │
          │   Next Best Action│
          └─────────┬─────────┘
                    │
                    ▼
          ┌───────────────────┐
          │  Bounded Recovery │
          │     Workflow      │
          └─────────┬─────────┘
                    │
                    ▼
              Recovery Outcome
```

### Predict → Optimize → Recover

**Predict:**
Estimate the probability of success for each eligible recovery action.

**Optimize:**
Select the action that provides the best expected business value after considering success probability, recoverable amount, action cost, and customer friction.

**Recover:**
Execute the selected action through a bounded state-machine workflow with retry and action limits.

---

## 🎯 Key Features

### 1. Action-Conditioned Prediction

RecoverIQ estimates:

```text
P(success | customer, payment context, failure type, action)
```

Instead of predicting whether a payment will succeed in general, the engine evaluates the probability of success **for each possible recovery action**.

Example:

| Recovery Action | Predicted Success |
| --------------- | ----------------: |
| Immediate Retry |               55% |
| Delayed Retry   |               78% |
| Payment Link    |               64% |

This allows the system to compare recovery strategies rather than blindly retrying.

---

### 2. Evidence-Based Probability Estimation

The prediction engine uses historical recovery records and follows an evidence hierarchy:

```text
Customer + Failure Type + Action
              ↓
Customer + Action
              ↓
Customer Segment + Failure Type + Action
              ↓
Failure Type + Action
              ↓
Global Action History
              ↓
Configured Prior
```

When historical data is sparse, the engine falls back to broader evidence instead of producing unreliable predictions.

Probability smoothing is also used to avoid extreme estimates such as:

```text
1 success / 1 attempt = 100%
0 success / 1 attempt = 0%
```

---

### 3. Next Best Action Optimization

RecoverIQ does not simply select the action with the highest probability.

The optimizer evaluates:

```text
Score =
Predicted Success Probability × Recoverable Amount
− Action Cost
− Customer Friction
```

This allows the system to optimize for **business value**, rather than probability alone.

Available recovery actions include:

* `IMMEDIATE_RETRY`
* `DELAYED_RETRY`
* `SEND_PAYMENT_LINK`
* `REQUEST_PAYMENT_METHOD_UPDATE`
* `STOP_RECOVERY`

---

### 4. Failure-Aware Recovery

Different failure types lead to different eligible actions.

For example:

```text
Insufficient Funds
        ↓
Delayed Retry / Payment Link
```

```text
Invalid Payment Method
        ↓
Payment Method Update / Payment Link
```

```text
Fraud or Risk
        ↓
Stop Recovery
```

This prevents a one-size-fits-all recovery strategy.

---

### 5. Explainable Decisions

Every recommendation is explainable.

Instead of simply returning:

```text
DELAYED_RETRY
```

RecoverIQ can provide reasoning such as:

```text
Delayed Retry was selected because it provides
the strongest expected recovery value after
considering success probability, action cost,
and customer friction.
```

The system also produces a **decision trace** containing:

* Candidate actions
* Success probability
* Expected recovery value
* Action cost
* Customer friction
* Eligibility
* Selected action
* Selection reason

This makes recovery decisions **transparent and auditable**.

---

### 6. Bounded Recovery Workflow

RecoverIQ uses an explicit state-machine workflow.

Example:

```text
FAILED
  │
  ▼
RECOVERY_IN_PROGRESS
  │
  ▼
RETRY_SCHEDULED
  │
  ├── Success ──→ RECOVERED
  │
  └── Failure ──→ FAILED
                       │
                       ▼
                 Next Recovery Action
```

Terminal states:

```text
RECOVERED
EXHAUSTED
```

The workflow includes:

* Maximum retry limits
* Maximum action limits
* Terminal state protection
* Duplicate action protection
* Permanent-risk handling

This prevents uncontrolled or infinite retry loops.

---

## 🏢 How It Helps Merchants

RecoverIQ is designed around a simple business objective:

> **Recover more valuable revenue while avoiding unnecessary recovery attempts and customer friction.**

For merchants, this can help:

* Improve payment recovery rate
* Recover revenue that would otherwise be lost
* Reduce unnecessary retry attempts
* Reduce customer friction
* Make recovery decisions more consistent
* Understand why a recovery action was selected
* Measure recovery performance against a baseline

In short:

**The goal isn't to retry more payments.
The goal is to recover the right payments using the right action at the right time.**

---

## 📊 Business Impact Evaluation

RecoverIQ includes a batch evaluation engine to compare different recovery strategies.

### Strategies

```text
1. Do Nothing
2. Blind Retry
3. RecoverIQ Adaptive Recovery
```

The evaluation measures:

* Recovery rate
* Recovered revenue
* Revenue at risk
* Number of interventions
* Unproductive attempts
* Improvement over baseline

This allows the decision engine to be evaluated based on **business outcomes**, not just individual predictions.

---

## 🧪 Synthetic Payment Dataset

The MVP uses reproducible synthetic payment-history data.

The generator models meaningful customer behaviour such as:

* Financial stability
* Engagement
* Payment-method health
* Risk propensity
* Preferred payment method
* Customer archetype

Supported archetypes include:

* `RELIABLE`
* `CASH_FLOW_SENSITIVE`
* `DISENGAGED`
* `HIGH_RISK`

The generated data contains:

* Customers
* Payments
* Payment attempts
* Failure types
* Recovery actions
* Historical outcomes

The generator is seeded, making experiments reproducible.

```text
Same Seed + Same Configuration
            ↓
      Same Dataset
```

---

## 🏗️ Architecture

```text
                    ┌─────────────────────┐
                    │     REST API        │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Application Service│
                    └──────────┬──────────┘
                               │
                 ┌─────────────┴─────────────┐
                 ▼                           ▼
        ┌──────────────────┐       ┌──────────────────┐
        │ Prediction Engine│       │    Optimizer     │
        └────────┬─────────┘       └────────┬─────────┘
                 │                           │
                 └─────────────┬─────────────┘
                               ▼
                    ┌─────────────────────┐
                    │ Recovery Workflow   │
                    │   State Machine     │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Persistence / Audit │
                    └─────────────────────┘
```

### Core Modules

```text
synthetic/
    Synthetic payment-history generation

prediction/
    Action-conditioned probability estimation

optimizer/
    Eligibility + cost + friction + next-best-action

workflow/
    Bounded recovery state machine

batch/
    Business outcome evaluation

evaluation/
    Deterministic development / held-out split

persistence/
    JPA entities and repositories

api/
    REST controllers and application services
```

---

## 🔌 REST API

### Health

```http
GET /api/health
POST /api/health
```

### Evaluate a Payment

```http
POST /api/recovery/evaluate
```

Returns:

* Recommended action
* Predicted success probability
* Expected recovery value
* Explanation
* Decision trace

### Run Recovery

```http
POST /api/recovery/run
```

Runs the bounded recovery workflow and returns the resulting recovery history.

### Get Recovery Analysis

```http
GET /api/recovery/{paymentId}
```

### Get Recovery Events

```http
GET /api/recovery/{paymentId}/events
```

### Batch Evaluation

```http
POST /api/recovery/evaluate/batch
```

Runs a deterministic batch evaluation and returns business-level recovery metrics.

---

## 💾 Persistence

RecoverIQ uses **PostgreSQL** with **Spring Data JPA**.

Two important persistence concepts are separated:

### Recovery Analysis

Stores the decision:

```text
Payment
Customer
Failure Type
Recommended Action
Predicted Probability
Expected Recovery Value
Reason
Timestamp
```

### Recovery Events

Stores what happened during execution:

```text
Payment
Action
Previous State
New State
Attempt Number
Outcome
Timestamp
```

This creates a clear separation between:

**What the system decided**
and
**What the system actually executed.**

---

## 🔐 Reliability & Safety

The workflow is designed with bounded execution and basic idempotency.

RecoverIQ protects against:

* Infinite retries
* Duplicate recovery execution
* Actions after terminal states
* Recovery of permanent-risk failures

Recovery events are also persisted for auditability.

---

## 🧠 Design Principles

RecoverIQ is built around four distinct responsibilities:

### Prediction

> **What is likely to work?**

### Optimization

> **What is worth doing?**

### Workflow

> **How should the action be executed safely?**

### Auditability

> **What decision was made and what happened afterward?**

Keeping these responsibilities separate makes the system easier to test, explain, and extend.

---

## 🛠️ Tech Stack

| Technology                | Purpose                          |
| ------------------------- | -------------------------------- |
| Java                      | Core implementation              |
| Spring Boot               | REST API & application framework |
| Spring Data JPA           | Persistence                      |
| PostgreSQL                | Database                         |
| Maven                     | Build & dependency management    |
| JUnit / Java Test Runners | Testing                          |

---

## ▶️ Running Locally

### 1. Clone the repository

```bash
git clone <your-repository-url>
cd RecoverIQ
```

### 2. Configure PostgreSQL

Set the following environment variables:

```bash
RECOVERIQ_DB_URL=<your-postgresql-url>
RECOVERIQ_DB_USERNAME=<your-username>
RECOVERIQ_DB_PASSWORD=<your-password>
```

### 3. Build the project

```bash
mvn test
```

### 4. Run the application

```bash
mvn spring-boot:run
```

The API can then be accessed through the configured Spring Boot server.

---

## 🧪 Testing

The project includes tests covering:

* Synthetic data reproducibility
* Failure/action correlations
* Prediction evidence backoff
* Probability smoothing
* Action and failure-specific predictions
* Customer segment influence
* Retry limits
* Action eligibility
* Cost/friction trade-offs
* Workflow transitions
* Terminal states
* Duplicate protection
* Audit events
* Batch aggregation
* Large dataset processing
* Decision explanations
* Decision traces

The batch evaluation has also been designed to process larger synthetic datasets such as **10,000+ payment cases**.

---

## 🔮 Future Vision

The current MVP intentionally focuses on the core decision engine.

A production evolution could introduce:

* Real payment-provider integrations
* Real payment and customer signals
* Production schedulers
* Real notification channels
* More advanced ML models
* Real-time recovery optimization
* Merchant analytics dashboards
* Distributed processing for large-scale payment recovery

The long-term vision is to make RecoverIQ an **intelligence layer for payment recovery infrastructure**.

---

## ⚠️ Current MVP Scope

RecoverIQ currently focuses on the **decision-making and recovery workflow layer**.

It does not currently execute real payments through Razorpay or other payment providers.

The recovery execution in the MVP is simulated and deterministic so that decisions and outcomes remain reproducible and testable.

---

## 📌 Project Status

**MVP — Core Decision Engine Implemented**

The current implementation includes:

* ✅ Synthetic payment-history generation
* ✅ Action-conditioned prediction
* ✅ Evidence-based probability estimation
* ✅ Next-best-action optimization
* ✅ Failure-aware recovery
* ✅ Explainable decisions
* ✅ Decision traces
* ✅ Bounded recovery workflow
* ✅ Audit events
* ✅ PostgreSQL persistence
* ✅ REST APIs
* ✅ Batch business evaluation
* ✅ Deterministic evaluation split
* ✅ Automated testing

---

## 🎯 The Core Idea

Traditional recovery:

```text
Payment Failed
      ↓
Retry
      ↓
Retry Again
```

RecoverIQ:

```text
Payment Failed
      ↓
Understand the Context
      ↓
Predict What Will Work
      ↓
Optimize Business Value
      ↓
Choose the Next Best Action
      ↓
Recover Within Safe Limits
      ↓
Measure the Outcome
```

### **Don't blindly retry failed payments. Recover them intelligently.**
