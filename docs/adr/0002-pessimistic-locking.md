# ADR 0002: Ordered pessimistic account locks

## Status

Accepted

## Context

Concurrent transfers on the same accounts can overspend or deadlock.

## Decision

Inside a single database transaction:

1. Lock both account rows with `PESSIMISTIC_WRITE` (`SELECT … FOR UPDATE`).
2. Always lock in ascending account ID order.
3. Re-check the sender balance after both locks are held.
4. Update both balances and insert an immutable `transfer` row in the same commit.

## Consequences

Reciprocal A↔B transfers cannot deadlock. Lost updates cannot produce a negative balance. Throughput is limited by row lock contention, which is acceptable for this MVP.
