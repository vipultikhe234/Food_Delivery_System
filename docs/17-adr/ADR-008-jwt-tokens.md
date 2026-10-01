# ADR-008: RS256 JWT access tokens + rotating opaque refresh tokens
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-AUTH-001, REQ-AUTH-003, REQ-AUTH-004, REQ-RT-001

## Context
About 20 services must authenticate every request without making identity-service a bottleneck. Stolen refresh tokens must be detectable.

## Decision
- **Access token:** JWT signed with RS256, TTL 15 minutes.
  - Claims: `sub`, `roles`, `perms`, `scopes` (restaurant/branch IDs), `sid`, `iat`, `exp`, `iss`, `aud`.
  - Signing key ID in `kid`; public keys published at a JWKS endpoint.
  - The private key is held in the secret manager.
- **Refresh token:** a random 256-bit opaque value, stored as a SHA-256 hash, TTL 30 days.
  - Rotated on every use and grouped into a *family*.
  - Reuse of a rotated token revokes the whole family.
- **Web:** access token in memory; refresh token in an `HttpOnly; Secure; SameSite=Strict` cookie scoped to `/api/v1/auth`, with CSRF protection on refresh.
- **Mobile:** refresh token in secure storage.
- **Permission changes:** take effect at the next refresh (≤ 15 min). For immediate effect on **block or role revocation**, a `revoked_sessions` deny-list in Redis is checked by the gateway against the `sid` claim.

## Consequences
### Positive
- Stateless validation in every service and in realtime-service (STOMP CONNECT).
- Token theft detection. Key rotation without downtime.

### Negative / risks
- Up to 15 minutes of stale permissions, except for block/revoke, which use the deny-list.
- Key management must be correct (rotation runbook in 09-security.md).

## Alternatives considered
- **Opaque tokens + introspection:** an extra network hop on every request.
- **HS256 shared secret:** every service could mint tokens.
- **Long-lived access tokens:** no revocation.
