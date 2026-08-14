# Rupi API contract

Base URL: `/api/v1`

All money values are **sandbox credits**, not real currency. Amounts use two decimal places (`numeric(19,2)` / `BigDecimal` scale 2).

## Conventions

| Topic | Rule |
| --- | --- |
| Auth | `Authorization: Bearer <jwt>` on every protected route |
| Idempotency | `Idempotency-Key: <uuid>` required on `POST /transactions` and `POST /demo/faucet` |
| Content type | `application/json` |
| Timestamps | UTC ISO-8601 |
| IDs | UUID strings |
| Pagination | Opaque `cursor` plus `limit` (default 20, max 50) |

Replay of a completed idempotent request returns the original status and body, plus header `Idempotent-Replayed: true`.

## Error envelope

Every non-2xx JSON body:

```json
{
  "code": "INSUFFICIENT_FUNDS",
  "message": "Account does not have enough sandbox credits.",
  "requestId": "7c2e0b3a-6d1f-4c8a-9e11-2f0d8c4a1b90",
  "fieldErrors": []
}
```

`fieldErrors` is present for validation failures only:

```json
{
  "field": "amount",
  "message": "must have at most 2 decimal places"
}
```

| HTTP | Code | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Bad body, missing/invalid UUID key, non-positive amount |
| 401 | `UNAUTHENTICATED` | Missing, expired, or invalid JWT |
| 403 | `FORBIDDEN` | Authenticated but not allowed |
| 404 | `NOT_FOUND` | Unknown recipient account |
| 409 | `USERNAME_TAKEN` | Register with an existing username |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | Same key currently executing |
| 409 | `IDEMPOTENCY_KEY_REUSED` | Same key, different request fingerprint |
| 409 | `INSUFFICIENT_FUNDS` | Sender balance too low after lock |
| 409 | `FAUCET_COOLDOWN` | Demo faucet used within 24 hours |
| 409 | `FAUCET_CAP_REACHED` | Balance would exceed 5,000.00 |
| 429 | `RATE_LIMITED` | Token bucket exhausted (`Retry-After` header) |
| 500 | `INTERNAL_ERROR` | Unexpected failure; no committed mutation |

## Endpoints

### `POST /auth/register`

Public. Atomically creates a user and one account with **1,000.00** sandbox credits.

Request:

```json
{ "username": "alice", "password": "correct-horse-battery" }
```

- Username: 3–32 chars, `[a-zA-Z0-9._-]`, stored lowercase
- Password: 10–72 chars

Response `201`:

```json
{
  "token": "<jwt>",
  "userId": "…",
  "username": "alice",
  "accountId": "…",
  "balance": "1000.00"
}
```

### `POST /auth/login`

Public.

```json
{ "username": "alice", "password": "correct-horse-battery" }
```

Response `200`: same shape as register. Invalid credentials → `401 UNAUTHENTICATED`.

### `GET /accounts/me`

Protected. Own account only.

```json
{
  "accountId": "…",
  "userId": "…",
  "username": "alice",
  "balance": "1000.00",
  "demoMode": true
}
```

### `POST /demo/faucet`

Protected. Demo-mode only. Grants **500.00** credits, once per 24 hours, never above **5,000.00**. Requires `Idempotency-Key`.

Response `200`:

```json
{
  "accountId": "…",
  "granted": "500.00",
  "balance": "1500.00"
}
```

Disabled when `DEMO_MODE=false` → `403 FORBIDDEN`.

### `POST /transactions`

Protected. Sender is the JWT subject’s account.

Headers: `Authorization`, `Idempotency-Key`

```json
{ "toAccountId": "…", "amount": "25.50" }
```

Rules:

- `amount` > 0, max 2 decimal places, max 1,000,000.00
- Cannot transfer to self
- Recipient must exist
- PostgreSQL is the idempotency authority; Redis is a 24-hour cache

Response `201` (first commit) or `200` (replay):

```json
{
  "id": "…",
  "fromAccountId": "…",
  "toAccountId": "…",
  "amount": "25.50",
  "direction": "OUTGOING",
  "status": "COMPLETED",
  "createdAt": "2026-08-14T02:40:00Z"
}
```

### `GET /transactions`

Protected. Incoming and outgoing transfers for the caller.

Query: `limit` (default 20), `cursor` (opaque)

```json
{
  "items": [
    {
      "id": "…",
      "fromAccountId": "…",
      "toAccountId": "…",
      "counterpartyAccountId": "…",
      "amount": "25.50",
      "direction": "INCOMING",
      "status": "COMPLETED",
      "createdAt": "2026-08-14T02:40:00Z"
    }
  ],
  "nextCursor": "eyJjIjoi…",
  "hasMore": true
}
```

Cursor is `(created_at, id)` descending. Timestamp-only cursors are not used.

### `GET /status`

Public liveness JSON used by the frontend smoke page. Actuator `GET /actuator/health` remains the deploy health check.
