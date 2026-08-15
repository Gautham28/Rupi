# Rupi

Sandbox ledger for idempotent transfers. Not real money.

Users get **1,000 sandbox credits** on signup, can optionally request more from a capped demo faucet, and can send credits to another account without double-debiting retries.

## Stack

- Frontend: React 19, Vite, TypeScript, Tailwind CSS
- Backend: Java 21, Spring Boot 4.1, Spring Security, JPA, Flyway
- PostgreSQL 16 (correctness: balances, transfers, idempotency records)
- Redis 7 (coordination, 24h response cache, rate limits)

## Local run

```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

- API: http://localhost:8090
- Health: http://localhost:8090/actuator/health
- UI: http://localhost:5173
- Postgres (Docker): `localhost:5433` — host `5432` is left free for a local Postgres install
- Redis (Docker): `localhost:6379`

## Smoke test

With Docker, backend, and frontend running:

```bash
curl -s http://localhost:8090/api/v1/status
# {"service":"rupi","status":"ok","demoMode":true}

curl -s http://localhost:8090/actuator/health
# {"status":"UP"}

curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8090/api/v1/accounts/me
# 401
```

Open http://localhost:5173 — the home page should show API `ok` and demo faucet `on`.

Register a user (password ≥ 10 chars), land on the dashboard with **1,000.00** sandbox credits, log out, then sign back in.

Protected routes (`/api/v1/accounts/**`, `/api/v1/transactions/**`, `/api/v1/demo/**`) require `Authorization: Bearer <jwt>`. Without a token:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8090/api/v1/accounts/me
# 401
```

## CI

Every push and pull request runs GitHub Actions:

- Backend: Java 21, `./mvnw test`
- Frontend: Node 22, `npm ci` then `npm run build` (TypeScript check + Vite bundle)

Open the **Actions** tab on GitHub to see the latest run. Local equivalent:

```bash
cd backend && ./mvnw test
cd frontend && npm ci && npm run build
```

## Docs

- [API contract](docs/api-contract.md)
- [ADRs](docs/adr/)
- Original PRD: [proj-description.md](proj-description.md)

## Demo faucet and rate limits

- `POST /api/v1/demo/faucet` with Bearer JWT + `Idempotency-Key` grants **500.00** sandbox credits.
- One grant per account every **24 hours**; balance cannot exceed **5,000.00**.
- Authenticated API traffic is limited to about **10 requests/second per user** (Redis token bucket). Over limit returns `429 RATE_LIMITED` with `Retry-After`.

## Status

Auth, transfers, history, demo faucet, and per-user rate limiting are live. CI, k6 concurrency proof, and free deploy are next.
