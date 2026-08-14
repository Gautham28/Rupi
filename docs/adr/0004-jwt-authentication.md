# ADR 0004: Stateless JWT authentication

## Status

Accepted

## Context

Transaction APIs must know the sender without a server session store.

## Decision

- Access tokens are signed JWTs. Subject is the immutable user UUID.
- Passwords are hashed with BCrypt (cost 12).
- Token lifetime is 24 hours. No refresh-token rotation in v1.
- Frontend keeps the token in memory and `sessionStorage` (not `localStorage`).
- `fromAccountId` is never taken from the client; it is loaded from the authenticated user.

## Consequences

Logout is client-side only until the token expires. Stolen tokens work until expiry. That is accepted for the sandbox MVP.
