# ADR-005: Transactional outbox for event publication
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-PLAT-006, REQ-AUDIT-001, NFR-REL-001, NFR-CONS-001

## Context
A service that commits a state change and then publishes to Kafka (a "dual write") can lose the event if it crashes between the two steps, or publish an event for a rolled-back change. Distributed (XA) transactions are excluded by the brief.

## Decision
- Every state change that emits a domain or audit event writes an `outbox_events` row **in the same local transaction**.
- A relay in each service polls unpublished rows using `SELECT … FOR UPDATE SKIP LOCKED`, publishes them in order per aggregate, then marks them published. Published rows are purged after 7 days.
- The relay is implemented once in `backend/platform/common-events`.
- **Exception:** `location.updates.v1` is published directly, without the outbox. Updates are high-volume and ephemeral, and the next update supersedes a lost one. This is documented in 08-event-driven-architecture.md.

## Consequences
### Positive
- Atomic state change plus event, with no XA. Events survive broker outages.
- Sensitive operations cannot commit without their audit event.

### Negative / risks
- Publish latency equals the polling interval (target p95 < 1 s).
- Extra write per transaction, and the outbox table needs indexing and cleanup.
- Delivery is at-least-once, so duplicates are possible and consumers must be idempotent.

## Alternatives considered
- **CDC with Debezium:** lower latency and no polling, but more infrastructure (Kafka Connect). A possible later replacement behind the same table design.
- **Dual write with retries:** loses events on crash.
