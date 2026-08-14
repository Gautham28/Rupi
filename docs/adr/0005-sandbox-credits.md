# ADR 0005: Sandbox credits, not real money

## Status

Accepted

## Context

Rupi is a correctness demo. Real rails (cards, banks, KYC, withdrawals) are out of scope.

## Decision

- All balances are labelled **sandbox credits**.
- Registration grants 1,000.00 credits.
- Optional demo faucet grants 500.00 credits, once per 24 hours, capped at 5,000.00.
- Faucet is gated by `DEMO_MODE` and writes `demo_credit_event`.
- UI never uses a real currency symbol or implies redeemability.

## Consequences

Users can exercise transfers without payment processors. Abuse is bounded by the faucet cap, not by financial regulation.
