# 08 — Event-Driven Architecture

| Field | Value |
|---|---|
| Version | 1.0.0 |
| Status | **Approved** 2026-10-01 |
| Depends on | [ADR-002](17-adr/ADR-002-kafka.md), [ADR-005](17-adr/ADR-005-outbox-pattern.md), [ADR-007](17-adr/ADR-007-orchestrated-saga.md), [order-state-machine](architecture/order-state-machine.md) |
| Requirements | REQ-PLAT-005, REQ-PLAT-006, REQ-ORDER-005, NFR-REL-001, NFR-CONS-003 |

---

## 1. Envelope

Every message (event or command) uses the same JSON envelope, defined as a JSON Schema in `backend/platform/event-contracts/envelope.schema.json`:

```json
{
  "eventId": "0192f0c4-6a1e-7b3c-9d2e-5f6a7b8c9d0e",
  "eventType": "OrderConfirmed",
  "eventVersion": 1,
  "occurredAt": "2026-10-01T07:31:12.345Z",
  "producer": "order-service",
  "aggregateType": "Order",
  "aggregateId": "0192...o1",
  "aggregateVersion": 4,
  "correlationId": "c0a8012e-...",
  "causationId": "0192...previous-event-id",
  "actor": { "type": "SYSTEM", "id": null },
  "payload": { }
}
```

Kafka headers duplicate `eventType`, `eventVersion`, `correlationId` and `traceparent`, so consumers can route and trace without parsing the body. The message key is `aggregateId`.

**Payload rules:**
- Carry the facts consumers need, avoiding chatty callbacks, but **no secrets and minimal PII**. For example, an order event carries `customerId`, not phone or address. notification-service resolves contact data from its own sources.
- Monetary values are `{amount: "string", currency}`.
- `aggregateVersion` lets read models discard stale events (search, analytics).

## 2. Topics

| Topic | Key | Producers | Partitions (prod / local) | Retention | Cleanup |
|---|---|---|---|---|---|
| `identity.events.v1` | userId | identity | 6 / 1 | 7 d | delete |
| `user.events.v1` | userId | user | 6 / 1 | 7 d | delete |
| `restaurant.events.v1` | restaurantId | restaurant | 6 / 1 | 7 d | delete |
| `menu.events.v1` | branchId | menu | 12 / 1 | 7 d | delete |
| `media.events.v1` | mediaId | media | 3 / 1 | 3 d | delete |
| `promotion.events.v1` | offerId | promotion | 6 / 1 | 7 d | delete |
| `order.events.v1` | orderId | order | 24 / 3 | 7 d | delete |
| `payment.events.v1` | orderId | payment | 12 / 3 | 7 d | delete |
| `payment.commands.v1` | orderId | order, admin | 12 / 3 | 7 d | delete |
| `delivery.events.v1` | orderId | delivery | 12 / 3 | 7 d | delete |
| `delivery.commands.v1` | orderId | order | 12 / 3 | 7 d | delete |
| `partner.events.v1` | partnerId | delivery | 6 / 1 | 7 d | delete |
| `location.updates.v1` | partnerId | location (direct, no outbox) | 48 / 3 | 6 h | delete |
| `review.events.v1` | targetId | review | 6 / 1 | 7 d | delete |
| `notification.events.v1` | userId | notification | 6 / 1 | 3 d | delete |
| `complaint.events.v1` | complaintId | admin | 3 / 1 | 7 d | delete |
| `audit.events.v1` | entityId | all | 12 / 1 | 7 d | delete |
| `<topic>.retry.<n>` / `<topic>.dlt` | same | consumers | Same as source | 7 d / 30 d | delete |

Payment and delivery events and commands are keyed by **orderId**, not paymentId or partnerId, so that the orchestrator sees each order's messages in order.

Partition counts at design scale are sized from about 100K active orders and about 100K location msg/s (11-scalability.md). Increasing partitions later changes key-to-partition mapping, so production counts are set generously up front. Producer settings: `acks=all`, `enable.idempotence=true`, `compression.type=lz4`, `linger.ms=5` (location `linger.ms=20`, batched). Production: RF 3, `min.insync.replicas=2`.

## 3. Event catalogue

Legend for consumers: ORD order, PAY payment, DEL delivery, LOC location, NOT notification, RT realtime, SRCH search, AN analytics, REC recommendation, PRO promotion, ID identity, USR user, REST restaurant, MENU menu, REV review, AU audit, CART cart.

### 3.1 order.events.v1 (producer: order-service)
| Event | Key payload fields | Consumers |
|---|---|---|
| `OrderCreated` | orderNumber, customerId, branchId, restaurantId, items[], grandTotal, paymentMethod, couponCode | AN, NOT (COD only) |
| `OrderPaymentRetried` | paymentDeadline | — |
| `OrderPaymentFailed` | reason (`GATEWAY_FAILED`/`TIMEOUT`), closed | PRO (release safety net), NOT, AN, RT |
| `OrderConfirmed` | branchId, items[], grandTotal, paymentMethod | NOT, RT (restaurant new order), AN |
| `RestaurantAcceptedOrder` | prepTimeMinutes, estimatedReadyAt | NOT, RT, AN |
| `RestaurantRejectedOrder` | reasonCode, auto (bool) | NOT, RT, AN, MENU (stock restore) |
| `OrderPreparing` | — | RT, NOT |
| `OrderReady` | readyAt | DEL (immediate assignment if none), RT, NOT |
| `OrderPartnerAssigned` | partnerId, assignedAt | RT, NOT |
| `OrderPartnerReleased` | partnerId, reason | RT |
| `OrderDeliveryAssigned` | partnerId | RT, AN |
| `OrderPickedUp` / `OrderOutForDelivery` | partnerId, at | RT, NOT, AN |
| `OrderDelivered` | deliveredAt, deliveryMinutes | NOT, RT, AN, REC, REV (enable review), PAY (COD finalise) |
| `OrderDeliveryFailed` | reasonCode | NOT, RT, AN, admin alert |
| `OrderCancelled` | cancelledByType, reasonCode, wasPaid | PRO, DEL, NOT, RT, AN, MENU (stock restore), PAY (safety net) |
| `OrderRefundPending` / `OrderRefunded` | amount, destination | NOT, RT, AN |

### 3.2 Commands (orchestrator → participant)
| Topic | Command | Payload | Reply events |
|---|---|---|---|
| `payment.commands.v1` | `RegisterCodPayment` | orderId, amount | `CodPaymentRegistered` |
| `payment.commands.v1` | `CancelPayment` | orderId | `PaymentCancelled` |
| `payment.commands.v1` | `RefundRequested` | orderId, amount, destination, reasonCode, requestedBy, refundRequestId (idempotency) | `RefundCreated` → `RefundCompleted` / `RefundFailed` |
| `delivery.commands.v1` | `AssignDeliveryRequested` | orderId, branchId, pickup location, drop location, notBefore, priority, excludePartnerIds[] | `DeliveryAssigned` / `DeliveryAssignmentFailed` |
| `delivery.commands.v1` | `CancelDeliveryRequested` | orderId, reason | `DeliveryCancelled` |

Commands are ordinary messages in the envelope (`eventType` = command name). They are written to the outbox like events.

### 3.3 payment.events.v1 (producer: payment-service)
| Event | Payload | Consumers |
|---|---|---|
| `PaymentInitiated` | paymentId, method, amount | AN |
| `PaymentCompleted` | paymentId, method, amount, providerPaymentId | ORD, NOT, AN, RT |
| `PaymentFailed` | paymentId, failureCode | ORD, NOT, RT |
| `PaymentCancelled` | paymentId | ORD |
| `CodPaymentRegistered` / `CodCollected` | amount, partnerId | ORD, AN, DEL (earnings) |
| `RefundCreated` / `RefundCompleted` / `RefundFailed` | refundId, amount, destination | ORD, NOT, AN, RT, AU (via audit topic) |
| `WalletCredited` / `WalletDebited` | walletId, amount, reason | NOT, AN |

### 3.4 delivery.events.v1 and partner.events.v1 (producer: delivery-service)
| Event | Payload | Consumers |
|---|---|---|
| `DeliveryOfferCreated` | partnerId, expiresAt, pickup summary | RT (partner), NOT (push) |
| `DeliveryAssigned` | partnerId, partner display info, assignedAt | ORD, RT (registers active delivery for location fan-out), NOT |
| `DeliveryUnassigned` | partnerId, reason | ORD, RT |
| `DeliveryAssignmentFailed` | rounds, reason | ORD, NOT (customer delay), admin alert |
| `DeliveryPickedUp` / `DeliveryOutForDelivery` | partnerId, at | ORD |
| `DeliveryCompleted` | partnerId, at, proofType, codCollected | ORD, PAY (COD), RT (unregister), AN |
| `DeliveryFailed` | partnerId, reasonCode | ORD, admin alert |
| `DeliveryCancelled` | — | ORD |
| `PartnerVerified` / `PartnerSuspended` | partnerId, userId | ID (role), NOT |
| `PartnerStatusChanged` | partnerId, from, to | AN |

### 3.5 Other domains
| Topic | Events | Main consumers |
|---|---|---|
| identity.events.v1 | `UserRegistered`, `UserRoleChanged`, `UserBlocked`, `UserUnblocked`, `SessionRevoked`, `OtpRequested` (contains a delivery reference, **not** the OTP) | USR, NOT, AN, RT (disconnect on block) |
| user.events.v1 | `UserProfileUpdated`, `UserPreferencesUpdated`, `AddressChanged`, `DeviceRegistered` | REC, NOT, ai (cache) |
| restaurant.events.v1 | `RestaurantSubmitted`, `RestaurantApproved`, `RestaurantRejected`, `RestaurantSuspended`, `BranchCreated`, `BranchUpdated`, `BranchAvailabilityChanged`, `StaffInvited`, `StaffRemoved` | SRCH, MENU, ID, NOT, CART, DEL (branch location cache), AN |
| menu.events.v1 | `MenuUpdated`, `ProductUpdated`, `MenuItemAvailabilityChanged` | SRCH, CART, REC |
| promotion.events.v1 | `OfferUpdated`, `CouponRedeemed`, `PromotionAbuseFlagged` | SRCH (badges), AN, admin |
| review.events.v1 | `ReviewCreated`, `ReviewModerated`, `RatingAggregated` | REST, MENU, DEL, SRCH, REC, NOT (restaurant) |
| notification.events.v1 | `NotificationCreated` | RT (in-app push) |
| complaint.events.v1 | `ComplaintCreated`, `ComplaintResolved` | NOT, AN |
| media.events.v1 | `MediaUploaded`, `MediaRejected` | Owning services (optional status sync) |
| audit.events.v1 | `AuditRecorded` (actor, action, entity, before/after masked, reason) | AU |

### 3.6 location.updates.v1 (exception to the outbox)
`{partnerId, lat, lng, accuracyM, speedMps, heading, recordedAt, orderId?}`. Published directly after the Redis write; loss of a single update is acceptable because the next one supersedes it. Consumers: `location-history-writer` (location-service, batch persist for active deliveries), realtime-service (forwards only partners in the active-delivery map), delivery-service (optional: arrival geofence detection).

## 4. Consumer groups

Group naming: `<service>.<purpose>`, e.g. `order.saga`, `notification.dispatch`, `search.indexer`, `analytics.ingest`, `realtime.fanout`, `location.history-writer`.

Each consumer group processes a message as follows:
1. Deserialise and validate the envelope against the schema. Invalid messages go **straight to the DLT** (poison pill, never retried).
2. Open a transaction:
   ```sql
   INSERT INTO processed_events (event_id, consumer) VALUES (?, ?) ON CONFLICT DO NOTHING;
   ```
   If 0 rows are inserted the message is a duplicate: commit and skip.
3. Apply the business change, plus any outbox writes, in the **same** transaction.
4. Commit, then acknowledge the offset (manual ack, `AckMode.RECORD` for critical consumers, `BATCH` for analytics and indexers).

realtime-service, which has no database, uses an in-memory LRU of recent event IDs. Duplicates are harmless for UI pushes.

## 5. Retry and dead-letter policy (REQ-PLAT-005)

| Consumer type | Strategy | Why |
|---|---|---|
| **Order-sensitive** (`order.saga`, delivery command handler, payment command handler) | **Blocking retries** in place: 3 attempts with exponential backoff (1 s, 5 s, 25 s), then DLT and alert | Non-blocking retry topics would let later events for the same order overtake the failed one |
| **Order-insensitive** (notification, search, analytics, recommendation, audit) | **Non-blocking** retry topics `.retry.0` (10 s), `.retry.1` (1 min), `.retry.2` (10 min), then `.dlt` | Keeps the partition flowing |
| Non-retryable errors (validation, unknown event type, business rule violation such as an invalid transition) | Directly to DLT | Retrying cannot succeed |

**DLT handling:**
- Alert when the DLT message count is greater than 0 (critical for order and payment topics) (12-observability.md).
- The admin "DLT viewer" (Phase 14/20) lists messages with error details and allows **replay** after a fix. Replay is safe because consumers are idempotent.
- DLT messages keep the original headers plus `x-exception-class`, `x-exception-message` (sanitised), `x-original-topic`, `x-original-offset` and `x-failed-at`.

## 6. Outbox relay (ADR-005)

```text
loop every 200 ms (configurable) per service instance:
  BEGIN
  SELECT * FROM outbox_events
   WHERE published_at IS NULL
   ORDER BY created_at
   LIMIT 500
   FOR UPDATE SKIP LOCKED
  for each row: kafkaTemplate.send(topic, partition_key, payload, headers)  -- await acks
  UPDATE outbox_events SET published_at = now() WHERE id IN (...)
  COMMIT
on send failure: attempts++, last_error, backoff; row stays unpublished
```

- **Per-aggregate ordering:** rows for one aggregate are created in commit order. With `SKIP LOCKED` and several relay instances, two instances could publish events for the same aggregate out of order. To avoid this, the relay claims rows by **partition key hash ranges**: each instance takes a lease on a shard (`hashtext(partition_key) % 16`) using ShedLock-style leases. An alternative is a single active relay per service. The single relay is the v1 default because throughput (about 120 orders/s at design peak) is well within one relay's capacity.
- Metrics: `outbox_pending_count`, `outbox_publish_latency_seconds`, `outbox_failures_total`. Alert when the oldest unpublished row is more than 60 s old.

## 7. Schema evolution

- JSON Schemas live in `event-contracts/<topic>/<EventType>.v<n>.schema.json`. Producer tests validate emitted payloads; consumer tests validate against the producer schema (contract tests).
- **Compatible changes** (keep `eventVersion`): add optional fields; add enum values, since consumers must tolerate unknown values.
- **Breaking changes**: rename or remove a field, change a type or change semantics. These need a new `eventVersion`, with both versions produced during migration, or a new topic `.v2` for structural changes. Consumers migrate, then the old version is removed in a later release. CI checks schema compatibility against the last release.
- Avro with Schema Registry can replace JSON Schema later via an ADR if governance or throughput requires it.

## 8. Event flow examples

### 8.1 Online order, happy path
```text
POST /orders → [ORD] OrderCreated (+ saga PAYMENT deadline)
POST /payments → [PAY] PaymentInitiated
webhook → [PAY] PaymentCompleted → [ORD] commit coupon, OrderConfirmed → [RT] restaurant new order, [NOT] customer
staff accept → [ORD] RestaurantAcceptedOrder + cmd AssignDeliveryRequested(notBefore)
[DEL] DeliveryOfferCreated → partner accepts → DeliveryAssigned → [ORD] OrderPartnerAssigned, [RT] start forwarding location
staff ready → [ORD] OrderReady + OrderDeliveryAssigned (same tx, partner already assigned)
partner pickup → [DEL] DeliveryPickedUp → [ORD] OrderPickedUp → ... → DeliveryCompleted → [ORD] OrderDelivered
```

### 8.2 Restaurant timeout
```text
saga job: CONFIRMED and RESTAURANT_ACCEPT deadline passed → [ORD] RestaurantRejectedOrder(auto=true)
  → OrderRefundPending + cmd RefundRequested → [PAY] RefundCreated → webhook → RefundCompleted → [ORD] OrderRefunded
  → [PRO] release coupon (orchestrator call; safety net via event) → [NOT] customer notified at each step
```

## 9. Delivery guarantee summary
| Concern | Guarantee | Mechanism |
|---|---|---|
| Event loss | None for domain events | Outbox, `acks=all`, RF3 / min ISR 2, manual commit after processing |
| Duplicates | Possible; harmless | `processed_events`, idempotent handlers, natural unique keys |
| Ordering | Per aggregate (key) | Key = aggregateId, single relay, blocking retries for order-sensitive consumers |
| Latency (NFR-CONS-003, proposed p95 < 5 s) | To be verified in Phase 21 | Relay interval 200 ms, consumer lag alerts |
