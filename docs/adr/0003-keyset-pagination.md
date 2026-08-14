# ADR 0003: Keyset pagination on (created_at, id)

## Status

Accepted

## Context

The PRD paginated with `WHERE created_at < ?`. Rows that share a timestamp would be skipped or duplicated.

## Decision

Order by `created_at DESC, id DESC`. The opaque cursor encodes both values. Queries use tuple comparison, not offset pagination.

## Consequences

History stays stable when new rows arrive and when timestamps collide. Clients must treat `cursor` as opaque.
