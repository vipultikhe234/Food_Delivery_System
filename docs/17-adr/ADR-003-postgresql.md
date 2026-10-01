# ADR-003: PostgreSQL + PostGIS, database per service
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-PLAT-008, REQ-SEARCH-001..003, REQ-LOCATION-001..002, NFR-CONS-001..002

## Context
Orders, payments and the wallet ledger need ACID transactions and constraints. Discovery needs geospatial radius queries and text search. The brief requires PostgreSQL and database-per-service.

## Decision
- PostgreSQL (latest supported major version) with the PostGIS and pg_trgm extensions where needed.
- Each service owns one logical database and its own credentials. No cross-service queries.
- Flyway migrations follow expand/contract.
- Locally there is one server with many logical databases. In production, managed instances are grouped by criticality: order and payment on dedicated instances.

## Consequences
### Positive
- Strong consistency where money is involved.
- Geo and full-text search without extra infrastructure at first.
- One database technology for the team to master.

### Negative / risks
- No cross-service joins. Read models (search, analytics, admin aggregation) are built from events.
- A single primary limits write scale. That is acceptable: order writes are about 120/s at design peak. Location history is the outlier and is handled by partitioning, down-sampling and archival (11-scalability.md).

## Alternatives considered
- **MongoDB:** weaker multi-document transactional guarantees for money flows.
- **Shared single database:** violates bounded contexts and the brief.
