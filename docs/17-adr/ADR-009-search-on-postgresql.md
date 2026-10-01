# ADR-009: Search on PostgreSQL behind a SearchIndex port
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-SEARCH-001..003, NFR-PERF-002

## Context
The brief says to start with PostgreSQL and design for Elasticsearch/OpenSearch later. Search needs geo radius, text with typo tolerance, filters and sorting.

## Decision
- search-service keeps a denormalised read model (`restaurant_search_docs`, `dish_search_docs`) in its own PostgreSQL database, fed by events.
- It uses PostGIS (`ST_DWithin`, GiST), full-text `tsvector` (GIN), and `pg_trgm` similarity (GIN) for typos.
- All access goes through a `SearchIndex` interface. An `OpenSearchSearchIndex` adapter can replace it without API changes. A full rebuild job exists from day one.

## Consequences
### Positive
- No extra cluster to run in early phases. Transactional upserts. Simple local development.

### Negative / risks
- Relevance tuning and scale are weaker than a dedicated engine.
- Triggers to migrate: p95 above target at agreed load, or a need for advanced relevance features (synonyms, learning-to-rank).

## Alternatives considered
- **OpenSearch from day one:** better relevance and scale, but higher cost and operational load before it is needed.
