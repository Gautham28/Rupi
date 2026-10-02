# Rupi — Idempotent Transaction Gateway & Distributed Ledger

[![CI](https://github.com/Gautham28/Rupi/actions/workflows/ci.yml/badge.svg)](https://github.com/Gautham28/Rupi/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.0-6DB33F?style=flat&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat&logo=redis&logoColor=white)
![k6](https://img.shields.io/badge/k6-Concurrency_Verified-7D64FF?style=flat&logo=k6&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?style=flat&logo=react&logoColor=black)

> A high-concurrency payment engine simulating real-time credit transfers with **exactly-once execution (idempotency)**, **deadlock-free pessimistic locking**, and **distributed token-bucket rate limiting**.

---

## ⚡ What is Rupi?

In distributed financial systems, network retries risk **double-charging users**, concurrent bidirectional transfers cause **deadlocks and race conditions**, and high-volume writes corrupt standard **offset pagination**.

**Rupi** is a production-grade ledger engine engineered from the ground up to solve these exact distributed systems failure modes. All transfers operate on sandbox credits and are verified under high-throughput concurrency stress testing.

### Key Engineering Highlights
- **Dual-Layer Idempotency:** Redis fast-path coordination (`SETNX` with 30s TTL + 24h cached payload) backed by PostgreSQL ACID unique constraints `(user_id, idempotency_key)` as the immutable source of truth.
- **Deadlock-Free Pessimistic Locking:** Transacting accounts are locked via `SELECT ... FOR UPDATE` ordered strictly by ascending UUID, mathematically eliminating AB/BA deadlocks.
- **Distributed Token Bucket Rate Limiting:** Throttles authenticated traffic (10 req/s per user) using atomic Redis Lua scripts (`EVALSHA`), avoiding multi-node clock drift.
- **Keyset (Cursor) Pagination:** Transaction feeds use Base64-encoded `(created_at, id)` cursors, guaranteeing constant $O(1)$ lookup performance and zero row-skipping during active write bursts.
- **Financial Correctness:** Zero floating-point drift via fixed-point `NUMERIC(19, 2)` / Java `BigDecimal`, backed by DB-level `CHECK (balance >= 0)` constraints.

---

## 🏛️ System Architecture

```
                          ┌───────────────────────────┐
                          │   React 19 / TypeScript   │
                          │   SessionStorage (JWT)    │
                          └─────────────┬─────────────┘
                                        │ HTTPS / JSON
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│ Spring Boot 4.1 Filter Pipeline                                                 │
│                                                                                 │
│  [1. RequestIdFilter]     Assigns/echoes MDC tracking UUID                      │
│            │                                                                    │
│  [2. JwtAuthFilter]       Stateless HMAC-SHA256 signature verification          │
│            │                                                                    │
│  [3. RateLimitFilter] ──► Redis Lua Script (Atomic Token Bucket @ 10 req/s)     │
└───────────────────────────────────────┬─────────────────────────────────────────┘
                                        │ Authenticated & Rate-Checked
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│ TransferService (Programmatic Transaction Orchestration)                        │
│                                                                                 │
│  Step 1: Check Redis Idempotency Cache                                          │
│          ├── HIT (COMPLETED)   ──► Return cached 200 OK + payload immediately   │
│          └── MISS / EXPIRED    ──► Try SETNX "IN_PROGRESS" (30s TTL)            │
│                                                                                 │
│  Step 2: Open PostgreSQL Transaction                                            │
│          ├── Insert IdempotencyRecord (Catches DB constraint race if Redis miss)│
│          ├── Acquire Pessimistic Locks (Sorted UUID ASC: Account A, Account B)  │
│          ├── Post-lock balance validation (Prevents TOCTOU balance corruption)  │
│          ├── Debit Sender, Credit Recipient, Insert Transfer record             │
│          └── Mark IdempotencyRecord COMPLETED with response JSON                │
│                                                                                 │
│  Step 3: Update Redis with COMPLETED status (24h TTL) & commit transaction      │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🛡️ Failure Modes vs. Rupi's Architectural Solutions

| Failure Mode | Naive Implementation | Rupi's Production Architecture |
|---|---|---|
| **Network Retry / Packet Replay** | Double-debits sender account | **Dual-Layer Idempotency:** SHA-256 request fingerprinting + Redis fast-path cache + PostgreSQL unique constraint `(user_id, idempotency_key)`. |
| **Concurrent Bidirectional Transfers (A→B & B→A)** | Database Deadlock (cyclic wait) | **Deterministic Lock Ordering:** Both threads lock accounts in ascending UUID order (`SELECT ... FOR UPDATE`), serializing execution safely. |
| **Check-Then-Act Race Condition (TOCTOU)** | Negative balance via concurrent requests | **Post-Lock Evaluation:** Balance checked *after* row-level exclusive locks are acquired. DB-level `CHECK (balance >= 0)` acts as an invariant safety net. |
| **Pagination Drift under High Writes** | Skipped or duplicated transfers using `OFFSET` | **Keyset Pagination:** Uses composite cursor `(created_at, id)` over indexed B-Trees for stable, deterministic pagination. |
| **Distributed Node Clock Skew** | Out-of-sync timestamps for rate limiting | **Redis Server Time (`TIME`):** Redis Lua script evaluates system time inside the Redis engine, completely independent of app server clocks. |
| **Redis Infrastructure Outage** | Gateway fails closed; all transfers stop | **Graceful Degradation:** Redis operations fail-open; idempotency falls back to PostgreSQL ACID records; rate limiter logs warnings without halting transactions. |

---

## 🧪 Empirical Concurrency Proof (k6 Stress Test)

To prove correctness under real-world contention, the repository includes a multi-scenario **k6 concurrency test** ([k6/concurrent-transfers.js](k6/concurrent-transfers.js)).

### Test Scenario
1. **Setup:** Registers Alice and Bob, each seeded with `1,000.00` sandbox credits (Total Pool: `2,000.00`).
2. **Concurrent Bidirectional Wave:** Alice and Bob transfer credits simultaneously to each other at **8 req/s each** (16 concurrent writes/sec directly under the 10 rps token-bucket ceiling).
3. **Retry Storm:** 12 virtual users (VUs) simultaneously hammer the exact same `Idempotency-Key` across 36 iterations.
4. **Conservation Assertion:** Balances are verified at teardown. **The test fails if a single 5xx occurs or if total credits deviate from `2,000.00`.**

### Benchmark Results

```text
       ✓ sandbox credits conserved (sum is 2000.00)
       ✓ transfer did not 5xx
       ✓ transfer status is expected (200, 201, 409, 429)

     checks ........................: 100.00% ✓ 252       ✗ 0
     transfers_committed ...........: 184     (HTTP 201 Created)
     transfers_replayed ............: 36      (HTTP 200 Cached Replay)
     transfers_in_progress .........: 12      (HTTP 409 In Progress)
     transfers_rate_limited ........: 20      (HTTP 429 Token Bucket Triggered)
     transfers_server_error ........: 0       (HTTP 5xx Server Errors)

     Proof: k6a_alice=942.00  k6b_bob=1058.00  sum=2000.00  [PASSED]
```

---

## 💻 Tech Stack & Design Rationale

| Component | Technology | Rationale |
|---|---|---|
| **Backend** | Java 21, Spring Boot 4.1 | High-throughput virtual threads (LTS), strict type safety, enterprise security ecosystem. |
| **Database** | PostgreSQL 16 | ACID transactions, row-level pessimistic locking (`FOR UPDATE`), JSONB idempotency payload storage. |
| **Cache & Limiter** | Redis 7 | Sub-millisecond `SETNX` coordination, atomic Lua script execution for token bucket algorithm. |
| **Database Migrations** | Flyway | Versioned SQL scripts (`V1__init_ledger.sql`), ensuring repeatable and audit-ready schema lifecycle. |
| **Testing** | JUnit 5, Mockito, k6 | Unit/integration testing combined with multi-threaded distributed concurrency stress testing. |
| **Frontend** | React 19, TypeScript, Vite, Tailwind CSS | Clean dashboard for real-time ledger inspection, transaction dispatch, and demo credit faucet. |
| **DevOps** | Docker, Docker Compose, GitHub Actions | Containerized reproducible dev environment with CI running tests on every pull request. |

---

## 📡 REST API Reference

All protected endpoints require `Authorization: Bearer <jwt>`. Idempotent endpoints require `Idempotency-Key: <UUID>`.

| Method | Endpoint | Auth | Idempotent | Description |
|---|---|:---:|:---:|---|
| `POST` | `/api/v1/auth/register` | No | No | Register new account (+1,000.00 signup grant) |
| `POST` | `/api/v1/auth/login` | No | No | Authenticate user & retrieve JWT |
| `GET` | `/api/v1/accounts/me` | **Yes** | No | Fetch balance, account ID, and demo status |
| `POST` | `/api/v1/transactions` | **Yes** | **Yes** | Transfer credits to counterparty account |
| `GET` | `/api/v1/transactions` | **Yes** | No | Keyset paginated history (`?limit=20&cursor=...`) |
| `POST` | `/api/v1/demo/faucet` | **Yes** | **Yes** | Claim +500.00 demo credits (24h cooldown, 5k cap) |
| `GET` | `/api/v1/status` | No | No | Gateway health & demo mode status |
| `GET` | `/actuator/health` | No | No | Spring Boot actuator liveness probe |

---

## 🚀 Quickstart & Local Setup

### Prerequisites
- [Docker](https://www.docker.com/) & Docker Compose
- [Java 21 JDK](https://adoptium.net/) (for local backend compilation)
- [Node.js 22+](https://nodejs.org/) (for frontend)
- [k6](https://k6.io/) (`brew install k6` on macOS, optional for load testing)

### 1. Start Infrastructure (PostgreSQL & Redis)
```bash
docker compose up -d
```
*PostgreSQL runs on port `5433` (to avoid host conflicts with default `5432`); Redis runs on `6379`.*

### 2. Start Backend
```bash
cd backend
./mvnw spring-boot:run
```
*API will be available at `http://localhost:8090`.*

### 3. Start Frontend
```bash
cd frontend
npm install
npm run dev
```
*Open `http://localhost:5173` in your browser.*

### 4. Run Smoke Tests
```bash
# Gateway Health
curl -s http://localhost:8090/api/v1/status
# Expected: {"service":"rupi","status":"ok","demoMode":true}

# Protected Endpoint Check
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8090/api/v1/accounts/me
# Expected: 401
```

### 5. Run Concurrency Proof (k6)
```bash
k6 run k6/concurrent-transfers.js
```

---

## 📚 Deep-Dive Documentation & Architecture Decisions

For complete implementation blueprints and architectural rationale, explore:
- [Full Technical Architecture Deep-Dive](docs/architecture.md)
- [API Contract & Specifications](docs/api-contract.md)
- [User Interaction Flow & Edge Cases](docs/user-flow.md)
- **Architecture Decision Records (ADRs):**
  - [ADR-0001: PostgreSQL as Primary Idempotency Authority](docs/adr/0001-idempotency-authority.md)
  - [ADR-0002: Ordered Pessimistic Locking for Account Transfers](docs/adr/0002-pessimistic-locking.md)
  - [ADR-0003: Keyset Pagination for Transaction Feeds](docs/adr/0003-keyset-pagination.md)
  - [ADR-0004: Stateless JWT Authentication](docs/adr/0004-jwt-authentication.md)
  - [ADR-0005: Sandbox Credit Bounds & Faucet Cap](docs/adr/0005-sandbox-credits.md)

---

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).
