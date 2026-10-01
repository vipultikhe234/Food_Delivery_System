# ADR-010: Dedicated realtime-service with Redis Pub/Sub
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-RT-001..003, NFR-PERF-004

## Context
Customers, restaurants and partners need server push for order status, offers and live location. WebSocket connections are long-lived and stateful. At design scale, peak concurrent connections are estimated at about 300K (11-scalability.md).

## Decision
- Create `realtime-service`: Spring WebSocket + STOMP with a simple in-memory broker per instance.
- Authentication by JWT at CONNECT. Subscription authorisation in a `ChannelInterceptor`.
- It consumes Kafka (order, payment, delivery and notification events, plus `location.updates.v1` filtered to active deliveries).
- It routes messages through **Redis Pub/Sub** keyed by user ID, so the instance holding the socket delivers it.
- It is stateless apart from connections and scales on connection count.

## Consequences
### Positive
- Connection scaling is isolated from request-scaling services.
- One place for subscription security. Simple horizontal fan-out.

### Negative / risks
- Redis Pub/Sub is fire-and-forget. Clients resynchronise via REST on reconnect (REQ-RT-001 AC4), so a missed push is recovered.
- One more deployable.

## Alternatives considered
- **STOMP broker relay to RabbitMQ:** a mature fan-out, but adds a second messaging system.
- **WebSocket inside notification-service:** couples high-volume location fan-out with notification dispatch.
- **Server-Sent Events:** simpler, but the partner app needs bidirectional messaging for offers.
