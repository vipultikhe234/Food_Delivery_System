# ADR-007: Orchestrated saga in order-service
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-ORDER-002..005, REQ-PAYMENT-002, REQ-PROMO-002, REQ-DELIVERY-003

## Context
Placing and fulfilling an order spans promotion, payment, restaurant, menu stock and delivery. Each owns its own data, and XA transactions are excluded. Failures (payment failed, restaurant rejected, no partner, cancellation) need compensations and timeouts.

## Decision
order-service is the **saga orchestrator**, driven by the order state machine (docs/architecture/order-state-machine.md):

- It reacts to events: PaymentCompleted/Failed, DeliveryAssigned, DeliveryCompleted, and so on.
- It issues commands as messages (`payment.commands.v1`, `delivery.commands.v1`).
- Coupon reservation is a synchronous call made before the order is created.
- Each waiting step has a deadline in `saga_state`, enforced by a scheduled job using ShedLock.

Side effects that need no coordination are left to choreography: notifications, search index, analytics, audit.

## Consequences
### Positive
- The whole business flow, its timeouts and its compensations are readable in one service and testable with failure injection.

### Negative / risks
- order-service becomes central and must stay lean. Domain rules for payment and delivery stay in their own services.
- It needs careful idempotency for commands and replies.

## Alternatives considered
- **Pure choreography:** no central coordinator, but the flow is scattered across services and is hard to reason about and test.
- **Workflow engine (Temporal/Camunda):** powerful, but adds significant infrastructure for one saga.
