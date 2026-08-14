# PRD: Idempotent Transaction Processing Gateway (Proj name - Rupi)

## 1. Executive Summary
Problem: Distributed payment systems face risks of double-charging users during network retries and data corruption during concurrent transfer requests.
Solution: A robust, full-stack simulated payment gateway featuring JWT authentication, exactly-once processing (idempotency), and data consistency under high concurrency via pessimistic locking.

## 2. Tech Stack & Architecture

Frontend: React, Vite, Tailwind CSS (State management via Context or Zustand).

Backend: Java 17+, Spring Boot 3+ (Spring Web, Spring Data JPA, Spring Security).

Primary Datastore: PostgreSQL 15+ (ACID-compliant storage for users, accounts, and transactions).

Caching / State: Redis (Idempotency keys, rate limiting).

DevOps: Docker, GitHub Actions (CI/CD).

## 3. Core Epics & Requirements

### Epic 0: User Authentication (Spring Security & JWT)

Req 0.1: Implement a User entity (id, username, password_hash).

Req 0.2: Upon registration, the system must automatically create a linked Account entity for the user with a default balance of $1000.00.

Req 0.3: Implement JWT-based stateless authentication. All /api/v1/transactions endpoints must require a valid Bearer token.

### Epic 1: The Ledger Engine (PostgreSQL & Spring Data)

Req 1.1: Maintain an Account entity (id, user_id, balance, created_at).

Req 1.2: Maintain a Transaction entity (id, from_account, to_account, amount, status, idempotency_key, created_at).

Req 1.3: During a transfer, acquire a PESSIMISTIC_WRITE lock (SELECT ... FOR UPDATE) on sender and receiver accounts to prevent race conditions. Ensure consistent lock ordering (e.g., lower ID first) to prevent deadlocks.

### Epic 2: The Idempotency Layer (Redis)

Req 2.1: POST /transactions requires an Idempotency-Key UUID header.

Req 2.2: Check Redis before database execution:

If missing: Store key as IN_PROGRESS, execute transaction.

If IN_PROGRESS: Return 409 Conflict.

If COMPLETED: Return 200 OK with the cached JSON response.

Req 2.3: Keys require a 24-hour TTL in Redis.

### Epic 3: API Protection (Rate Limiting)

Req 3.1: Implement a Token Bucket algorithm via Redis Lua scripts (10 requests per second per user). Return 429 Too Many Requests if exceeded.

### Epic 4: High-Performance Data Retrieval

Req 4.1: Implement Keyset Pagination (Cursor-based) for the transaction history endpoint (WHERE created_at < ? ORDER BY created_at DESC LIMIT 20).

## 4. API Specifications

POST /api/v1/auth/register - Body: { username, password }

POST /api/v1/auth/login - Returns JWT.

POST /api/v1/transactions

Headers: Authorization: Bearer <token>, Idempotency-Key: <UUID>

Body: { "toAccountId": "uuid", "amount": 100.50 } (Note: fromAccountId is derived from the JWT).

GET /api/v1/transactions - Query: ?limit=20&cursor=<timestamp>

## 5. SDLC Execution Plan for Cursor

Phase 1 (Infra): Generate docker-compose.yml and init.sql for Postgres/Redis.

Phase 2 (Auth): Scaffold Spring Boot, implement Spring Security, JWT generation, and User/Account creation logic.

Phase 3 (Ledger): Implement Transaction Entities, Repositories, and the PESSIMISTIC_WRITE transfer service.

Phase 4 (Idempotency): Build the Redis interceptor for caching and concurrent request blocking.

Phase 5 (Frontend): Build the React Vite app with login screens, JWT storage, and the dashboard UI.