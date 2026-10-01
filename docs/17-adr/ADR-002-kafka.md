# ADR-002: Kafka for domain events
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-PLAT-005, REQ-PLAT-006, REQ-LOCATION-001, REQ-SEARCH-003, REQ-ANALYTICS-001

## Context
Services must react to each other's state changes asynchronously, with ordering per order, replay for read models (search, analytics, recommendation), consumer groups, and a high-throughput location stream.

## Decision
Use Apache Kafka in KRaft mode (no ZooKeeper).

- Topics are per aggregate stream and versioned (`order.events.v1`).
- The key is the aggregate ID.
- Producers use `acks=all`, idempotence enabled, replication factor 3 and `min.insync.replicas=2` in production.
- Spring for Apache Kafka on the client side.

## Consequences
### Positive
- Per-key ordering, durable log, replay to rebuild read models, horizontal consumer scaling, and the throughput needed for location updates.

### Negative / risks
- Operational weight. Mitigated with a single broker locally and a managed service (MSK) in the cloud.
- At-least-once delivery requires idempotent consumers (REQ-PLAT-005).
- Non-blocking retry topics break per-key ordering, so order-sensitive consumers use blocking retries (see 08-event-driven-architecture.md).

## Alternatives considered
- **RabbitMQ:** excellent routing, but no log replay for read-model rebuilds.
- **Redis Streams:** lighter, but weaker durability guarantees and tooling at this scale.
- **Cloud-native (SNS/SQS, Kinesis):** ties local development to the cloud and gives weaker per-key ordering semantics across consumers.
