# ADR-001: Microservices with a 21-service decomposition
- Status: Accepted
- Date: 2026-10-01
- Deciders: Project owner, ArchitectAgent
- Related requirements: all; BR-10, NFR-SCALE-001..003

## Context
The brief mandates microservices but forbids unnecessary ones. The domain has parts with very different load profiles:

- location ingestion: about 100K writes/s at design scale
- discovery: read-heavy
- order and payment: low volume but strongly consistent

It also has clearly separable business capabilities.

## Decision
Build 3 infrastructure services (gateway, discovery, config) and 18 domain services, plus `requirement-service` in Phase 18. Each domain service is accepted only if it has its own data, its own consistency boundary, or a distinct scaling profile.

- Product is merged into **menu-service**.
- Wallet is a module of **payment-service**.
- Coupon is renamed **promotion-service**.
- **realtime-service** is added.
- **admin-service** owns complaints and back-office aggregation, not other services' writes.

The full list and boundaries are in [04-system-architecture.md §3](../04-system-architecture.md) and [05-microservices.md](../05-microservices.md).

## Consequences
### Positive
- Independent scaling of location, realtime and search, and independent deployment.
- Clear ownership. A failure in one service is contained.

### Negative / risks
- Operational complexity: about 21 deployables, distributed tracing and eventual consistency.
- Heavy local footprint. Mitigated by Compose profiles and shared platform libraries (`backend/platform/*`).
- Cross-service consistency needs saga and outbox discipline (ADR-005, ADR-007).

## Alternatives considered
- **Modular monolith:** simplest to run and a good fit for a small team, but contradicts the brief.
- **The brief's 23-service list as-is:** Product, Wallet and Coupon as separate services would add network hops and sagas without independent data or scaling needs.
