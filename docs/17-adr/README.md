# Architecture Decision Records

Each ADR records one significant decision: its context, the decision, its consequences and the alternatives. ADRs are immutable once accepted. To change a decision, write a new ADR that *supersedes* the old one.

| ADR | Title | Status | Date |
|---|---|---|---|
| [ADR-001](ADR-001-microservices.md) | Microservices with a 21-service decomposition | Accepted | 2026-10-01 |
| [ADR-002](ADR-002-kafka.md) | Kafka for domain events | Accepted | 2026-10-01 |
| [ADR-003](ADR-003-postgresql.md) | PostgreSQL + PostGIS, database per service | Accepted | 2026-10-01 |
| [ADR-004](ADR-004-redis.md) | Redis for caching, geo, rate limits, locks, pub/sub | Accepted | 2026-10-01 |
| [ADR-005](ADR-005-outbox-pattern.md) | Transactional outbox for event publication | Accepted | 2026-10-01 |
| [ADR-006](ADR-006-react-capacitor.md) | React + Capacitor (+ Ionic React) for mobile | Accepted | 2026-10-01 |
| [ADR-007](ADR-007-orchestrated-saga.md) | Orchestrated saga in order-service | Accepted | 2026-10-01 |
| [ADR-008](ADR-008-jwt-tokens.md) | RS256 JWT access tokens + rotating opaque refresh tokens | Accepted | 2026-10-01 |
| [ADR-009](ADR-009-search-on-postgresql.md) | Search on PostgreSQL behind a SearchIndex port | Accepted | 2026-10-01 |
| [ADR-010](ADR-010-realtime-service.md) | Dedicated realtime-service with Redis Pub/Sub | Accepted | 2026-10-01 |
| [ADR-011](ADR-011-discovery-and-config.md) | Eureka + Spring Cloud Config in all environments (v1) | Accepted | 2026-10-01 |
| [ADR-012](ADR-012-requirements-in-git.md) | Git-versioned requirements until requirement-service | Accepted | 2026-10-01 |
| [ADR-013](ADR-013-java-maven.md) | Java 21 LTS, Maven multi-module, Spring Boot release train | Accepted | 2026-10-01 |
| [ADR-014](ADR-014-k6.md) | k6 as primary performance testing tool | Accepted | 2026-10-01 |
| [ADR-015](ADR-015-external-provider-adapters.md) | Ports and adapters for external providers; initial providers | Accepted | 2026-10-01 |
| [ADR-016](ADR-016-aws.md) | AWS as target cloud, cost-minimised for portfolio context | Accepted | 2026-10-01 |
| [ADR-017](ADR-017-typescript-agents.md) | TypeScript for DevAgent and TestAgent | Accepted | 2026-10-01 |
| [ADR-018](ADR-018-restaurant-os-services.md) | Restaurant OS service boundaries (25 deployables; amends ADR-001) | Proposed | 2026-10-01 |
| [ADR-019](ADR-019-tenant-isolation.md) | Tenant isolation: application guard + PostgreSQL row-level security | Proposed | 2026-10-01 |
| [ADR-020](ADR-020-inventory-ledger-costing.md) | Immutable stock ledger, idempotent consumption, configurable costing | Proposed | 2026-10-01 |
| [ADR-021](ADR-021-shared-pricing-library.md) | Shared, framework-free pricing library | Proposed | 2026-10-01 |

## Template

```markdown
# ADR-NNN: Title
- Status: Proposed | Accepted | Superseded by ADR-XXX
- Date: YYYY-MM-DD
- Deciders: ...
- Related requirements: REQ-...

## Context
## Decision
## Consequences
### Positive
### Negative / risks
## Alternatives considered
```
