# ADR 0001: PostgreSQL owns idempotency

## Status

Accepted

## Context

The PRD asked Redis to be the idempotency store. Redis can lose keys, expire them, or miss a write after a successful database commit. That window can double-debit an account.

## Decision

- `idempotency_record` in PostgreSQL is the source of truth, unique on `(user_id, idempotency_key)`.
- Redis holds a short-lived `IN_PROGRESS` marker and a 24-hour cached completed response.
- A request fingerprint (user + route + canonical body hash) is stored with the key. A reused key with a different fingerprint returns `409 IDEMPOTENCY_KEY_REUSED`.
- If Redis is empty after commit, the next request replays from PostgreSQL.

## Consequences

Exactly-once *network delivery* is not claimed. One committed transfer per authenticated key is guaranteed even if Redis is flushed.
