# ADR-004: Redis for caching, geo, rate limits, locks and pub/sub
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-MENU-004, REQ-RESTAURANT-003, REQ-AUTH-002, REQ-SEC-001, REQ-LOCATION-001, REQ-DELIVERY-003, REQ-RT-001

## Context
Several hot paths need sub-millisecond shared state:

- menu and availability reads
- OTP and login counters
- gateway rate limiting
- "latest partner location" with radius search
- per-order assignment locks
- cross-instance WebSocket fan-out

## Decision
Use Redis, separated into **two logical deployments** with different eviction policies:

| Instance | Contents | Eviction | Persistence |
|---|---|---|---|
| `redis-cache` | Menu, branch status, popular searches, active offers, preferences | `allkeys-lru` | None (rebuildable) |
| `redis-state` | OTP, rate-limit buckets, login counters, GEO latest locations, partner availability, locks, Pub/Sub | `noeviction` | AOF (everysec) where useful |

Locally both run as one container with two logical DBs. The separation is enforced in production.

## Consequences
### Positive
- One component covers six needs.
- Cache eviction can never delete OTPs, locks or GEO state.

### Negative / risks
- Redis is not a source of truth. Order, payment and wallet state are never cached (10-caching.md).
- A Redis outage degrades features. Behaviour on outage is defined per use (14-disaster-recovery.md).

## Alternatives considered
- **Memcached:** no GEO, Pub/Sub or atomic scripts.
- **Hazelcast:** embedded grid adds JVM coupling.
- **Database-only:** too slow for rate limits and location.
