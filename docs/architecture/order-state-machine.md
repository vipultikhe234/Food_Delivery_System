# Order State Machine

| Field | Value |
|---|---|
| Version | 1.0.0 |
| Status | **Approved** 2026-10-01 (with [IMPACT-0001](../requirements/impact/IMPACT-0001.md), REQ-ORDER-002 v2) |
| Owner | order-service |
| Requirements | REQ-ORDER-002 v2, REQ-ORDER-003, REQ-ORDER-004, REQ-ORDER-005, REQ-PAYMENT-004, REQ-PAYMENT-005, REQ-DELIVERY-003/004, REQ-RT-003 v2 |

> **Pending change (Proposed 2026-10-01):** REQ-ORDER-002 v3 (IMPACT-0002) adds lifecycles per order type, the states `SERVED`, `HANDED_OVER` and `COMPLETED`, and transitions T27–T34 ([ORDER_ARCHITECTURE §4](restaurant-os/ORDER_ARCHITECTURE.md#4-lifecycles-per-order-type-req-order-002-v3-req-order-008)). This document becomes v2.0.0 when that design is approved.

The state machine lives in the order-service `domain` layer as an enum plus an explicit transition table. Every transition:

- is checked against the table (otherwise `409 INVALID_ORDER_TRANSITION`)
- is authorised by actor type and ownership or scope
- increments the optimistic `version` (concurrent transitions: exactly one wins, and the others receive `409 CONCURRENT_MODIFICATION` and retry against the new state)
- appends to `order_status_history` (from, to, actor type, actor ID, reason, correlation ID)
- writes the matching event to the outbox in the same transaction

---

## 1. States

| State | Meaning | Terminal? |
|---|---|---|
| `CREATED` | Order persisted from a valid quote; coupon reserved | No |
| `PAYMENT_PENDING` | Waiting for online payment confirmation (webhook) | No |
| `PAYMENT_FAILED` | Payment failed, or the payment window (15 min) expired | Yes once the window has expired. Before that, the customer may retry. |
| `CONFIRMED` | Paid (or COD accepted); waiting for the restaurant | No |
| `RESTAURANT_ACCEPTED` | Restaurant accepted with a prep time | No |
| `RESTAURANT_REJECTED` | Restaurant rejected, or the acceptance timeout (5 min) passed | Yes if unpaid (COD); otherwise continues to refund |
| `PREPARING` | Kitchen is preparing | No |
| `READY_FOR_PICKUP` | Food ready; no partner assigned yet | No |
| `DELIVERY_ASSIGNED` | Food ready **and** a partner is assigned | No |
| `PICKED_UP` | Partner has the food | No |
| `OUT_FOR_DELIVERY` | Partner travelling to the customer | No |
| `DELIVERED` | Delivered (OTP or proof confirmed) | Yes |
| `DELIVERY_FAILED` | Delivery could not be completed | Yes unless support initiates a refund |
| `CANCELLED` | Cancelled by customer, support or system | Yes if unpaid; otherwise continues to refund |
| `REFUND_PENDING` | Refund requested from payment-service | No |
| `REFUNDED` | Refund confirmed by gateway webhook or wallet credit | Yes |

### Partner assignment is a separate attribute (IMPACT-0001)
Assignment may happen **during preparation** (REQ-DELIVERY-003 AC1). The status stays linear: the order records `delivery_partner_id` and `partner_assigned_at` as soon as `DeliveryAssigned` arrives, but does not change status until the food is ready. When the restaurant marks the order ready:

- if a partner is already assigned: `PREPARING → READY_FOR_PICKUP → DELIVERY_ASSIGNED` in **one transaction**, producing two history rows and two events
- otherwise it stays `READY_FOR_PICKUP` and an immediate assignment request is sent

The customer sees the partner (REQ-RT-003 v2) from `DeliveryAssigned`, not from the `DELIVERY_ASSIGNED` status.

---

## 2. Diagram

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> PAYMENT_PENDING: online payment
    CREATED --> CONFIRMED: COD eligible
    CREATED --> CANCELLED: customer/system

    PAYMENT_PENDING --> CONFIRMED: PaymentCompleted
    PAYMENT_PENDING --> PAYMENT_FAILED: PaymentFailed / 15-min timeout
    PAYMENT_PENDING --> CANCELLED: customer
    PAYMENT_FAILED --> PAYMENT_PENDING: retry (within window)
    PAYMENT_FAILED --> CANCELLED: customer / late payment

    CONFIRMED --> RESTAURANT_ACCEPTED: accept / auto-accept
    CONFIRMED --> RESTAURANT_REJECTED: reject / 5-min timeout
    CONFIRMED --> CANCELLED: customer/support

    RESTAURANT_ACCEPTED --> PREPARING: staff
    RESTAURANT_ACCEPTED --> CANCELLED: customer/support

    PREPARING --> READY_FOR_PICKUP: staff
    PREPARING --> CANCELLED: support/admin

    READY_FOR_PICKUP --> DELIVERY_ASSIGNED: partner assigned (system)
    READY_FOR_PICKUP --> CANCELLED: support/admin

    DELIVERY_ASSIGNED --> PICKED_UP: assigned partner
    DELIVERY_ASSIGNED --> READY_FOR_PICKUP: partner released
    DELIVERY_ASSIGNED --> CANCELLED: support/admin

    PICKED_UP --> OUT_FOR_DELIVERY: assigned partner
    PICKED_UP --> DELIVERY_FAILED: partner/support
    OUT_FOR_DELIVERY --> DELIVERED: partner (OTP/proof)
    OUT_FOR_DELIVERY --> DELIVERY_FAILED: partner/support

    RESTAURANT_REJECTED --> REFUND_PENDING: if paid (system)
    CANCELLED --> REFUND_PENDING: if paid (system)
    DELIVERY_FAILED --> REFUND_PENDING: support decision
    REFUND_PENDING --> REFUNDED: RefundCompleted

    DELIVERED --> [*]
    REFUNDED --> [*]
```

---

## 3. Transition table

Actor types: `CUSTOMER` (order owner), `RESTAURANT` (staff with scope on the order's branch), `PARTNER` (the assigned partner), `SUPPORT` (SUPPORT_AGENT or ADMIN with `ORDER_CANCEL_ANY`/`ORDER_MANAGE`), `SYSTEM` (saga, consumers, timeout jobs).

| # | From | To | Trigger | Actor | Guards | Side effects (same transaction unless noted) | Event |
|---|---|---|---|---|---|---|---|
| T1 | CREATED | PAYMENT_PENDING | Order created with an online method | SYSTEM | Quote valid, coupon reserved | Saga deadline `PAYMENT` = now + 15 min | `OrderCreated` (emitted on creation; carries `paymentMethod`) |
| T2 | CREATED | CONFIRMED | Order created with COD | SYSTEM | COD eligible (REQ-PAYMENT-004 AC1) | Command `RegisterCodPayment` | `OrderConfirmed` |
| T3 | CREATED | CANCELLED | Cancel before payment initiated | CUSTOMER, SYSTEM | — | Release coupon | `OrderCancelled` |
| T4 | PAYMENT_PENDING | CONFIRMED | `PaymentCompleted` | SYSTEM | Amount and currency equal order total | Commit coupon; deadline `RESTAURANT_ACCEPT` = now + 5 min (or immediate auto-accept) | `OrderConfirmed` |
| T5 | PAYMENT_PENDING | PAYMENT_FAILED | `PaymentFailed`, or payment deadline passed | SYSTEM | — | On timeout: release coupon, `closed = true`. On gateway failure: keep the coupon reserved until the deadline. | `OrderPaymentFailed` |
| T6 | PAYMENT_FAILED | PAYMENT_PENDING | Customer retries payment | CUSTOMER | `closed = false` and now < payment deadline | — | `OrderPaymentRetried` |
| T7 | PAYMENT_PENDING | CANCELLED | Customer cancels | CUSTOMER | — | Release coupon; command `CancelPayment` (best effort) | `OrderCancelled` |
| T8 | CONFIRMED | RESTAURANT_ACCEPTED | Accept (prep time 5–120 min) or auto-accept | RESTAURANT, SYSTEM | Branch scope | Set `prep_time_minutes`, `estimated_ready_at`; schedule assignment (command `AssignDeliveryRequested` with `notBefore`) | `RestaurantAcceptedOrder` |
| T9 | CONFIRMED | RESTAURANT_REJECTED | Reject (reason required) or accept deadline passed | RESTAURANT, SYSTEM | Reason present | Release coupon; restore tracked stock | `RestaurantRejectedOrder` |
| T10 | CONFIRMED | CANCELLED | Cancel | CUSTOMER, SUPPORT | SUPPORT needs a reason | Release coupon; restore stock | `OrderCancelled` |
| T11 | RESTAURANT_ACCEPTED | PREPARING | Start preparing | RESTAURANT | Branch scope | — | `OrderPreparing` |
| T12 | RESTAURANT_ACCEPTED | CANCELLED | Cancel | CUSTOMER, SUPPORT | SUPPORT needs a reason | Release coupon; `CancelDeliveryRequested` if a partner is assigned or assignment is scheduled | `OrderCancelled` |
| T13 | PREPARING | READY_FOR_PICKUP | Mark ready | RESTAURANT | Branch scope | `ready_at` = now. If a partner is assigned, T15 runs in the same transaction. Otherwise `AssignDeliveryRequested(immediate)` and deadline `ASSIGNMENT` = now + 10 min. | `OrderReady` |
| T14 | PREPARING | CANCELLED | Cancel | SUPPORT | Reason required; audited | `CancelDeliveryRequested` | `OrderCancelled` |
| T15 | READY_FOR_PICKUP | DELIVERY_ASSIGNED | Partner assigned (`DeliveryAssigned`, or already recorded) | SYSTEM | `delivery_partner_id` set | — | `OrderDeliveryAssigned` |
| T16 | READY_FOR_PICKUP | CANCELLED | Cancel | SUPPORT | Reason required | `CancelDeliveryRequested` | `OrderCancelled` |
| T17 | DELIVERY_ASSIGNED | PICKED_UP | `DeliveryPickedUp` | PARTNER (via delivery-service) | Event partner = order partner | — | `OrderPickedUp` |
| T18 | DELIVERY_ASSIGNED | READY_FOR_PICKUP | `DeliveryUnassigned` | SYSTEM | Event partner = order partner | Clear partner fields; assignment restarts in delivery-service | `OrderPartnerReleased` |
| T19 | DELIVERY_ASSIGNED | CANCELLED | Cancel | SUPPORT | Reason required | `CancelDeliveryRequested` | `OrderCancelled` |
| T20 | PICKED_UP | OUT_FOR_DELIVERY | `DeliveryOutForDelivery` | PARTNER | Same partner | — | `OrderOutForDelivery` |
| T21 | PICKED_UP, OUT_FOR_DELIVERY | DELIVERY_FAILED | `DeliveryFailed` (reason category) or support action | PARTNER, SUPPORT | Reason required | Notify support | `OrderDeliveryFailed` |
| T22 | OUT_FOR_DELIVERY | DELIVERED | `DeliveryCompleted` | PARTNER | OTP or proof verified by delivery-service; COD: cash collected confirmed | `delivered_at` = now | `OrderDelivered` |
| T23 | RESTAURANT_REJECTED, CANCELLED | REFUND_PENDING | Automatic, if a captured payment exists | SYSTEM | `paid_amount > 0` | Command `RefundRequested(full, to original method)` | `OrderRefundPending` |
| T24 | DELIVERY_FAILED | REFUND_PENDING | Support decides to refund | SUPPORT (`ORDER_REFUND`) | Reason required; audited | Command `RefundRequested(amount, destination)` | `OrderRefundPending` |
| T25 | REFUND_PENDING | REFUNDED | `RefundCompleted` covering the requested amount | SYSTEM | — | — | `OrderRefunded` |
| T26 | PAYMENT_FAILED | CANCELLED | Customer abandons, or a late payment arrives (reason `LATE_PAYMENT`) | CUSTOMER, SYSTEM | — | Release the coupon if still reserved | `OrderCancelled` |

### Non-status changes (no transition)
| Change | Allowed in | Effect |
|---|---|---|
| Partner assigned | RESTAURANT_ACCEPTED, PREPARING | Sets `delivery_partner_id`, `partner_assigned_at`; publishes `OrderPartnerAssigned` |
| Partner released | RESTAURANT_ACCEPTED, PREPARING | Clears partner fields; publishes `OrderPartnerReleased` |
| Prep time extended | RESTAURANT_ACCEPTED, PREPARING (once, up to +30 min) | Updates `estimated_ready_at`; reschedules assignment; customer notified |
| Partial refund after delivery | DELIVERED | Status stays `DELIVERED`. The refund is tracked in payment-service and on the order's `refunded_amount`. |

---

## 4. Edge cases

| Case | Handling |
|---|---|
| `PaymentCompleted` arrives after `CANCELLED` or a closed `PAYMENT_FAILED` | Order-service records the payment, then CANCELLED → REFUND_PENDING (T23). For PAYMENT_FAILED it first applies T26 (reason `LATE_PAYMENT`), then T23. Covered by a dedicated failure test. |
| Duplicate events | `processed_events` dedupe. A transition to the current state is ignored as a no-op (logged at debug). |
| Out-of-order events (e.g. `DeliveryPickedUp` before `OrderReady` is processed) | Delivery-service only allows pickup after it has consumed `OrderReady`, so this ordering cannot occur from the source. Any invalid transition from an event goes to the retry topic, then the DLT with an alert. It is never forced. |
| `RefundFailed` | Order stays `REFUND_PENDING`; support is alerted (admin queue). A manual retry from the admin UI re-issues the command with a new idempotency key. |
| Restaurant closes after `CONFIRMED` | Treated as reject (T9) with reason `BRANCH_CLOSED`. |
| Customer unreachable at delivery | Partner reports `DELIVERY_FAILED` with reason `CUSTOMER_UNREACHABLE` after the in-app call and wait flow. Whether a refund follows is a support decision (T24). |
| Order-service crash mid-saga | State, outbox and saga deadlines are in the database. On restart, the relay publishes pending events and the deadline job resumes. Consumers are idempotent. |

### Refund policy after a failed delivery
**OQ-21 (decided 2026-10-01):** in v1, refunds after `DELIVERY_FAILED` happen only by support decision (T24). Automatic rules by failure reason may be revisited after UAT data, through a requirement change.

---

## 5. Customer-facing status mapping

| Internal state(s) | Customer label | Restaurant label | Partner label |
|---|---|---|---|
| CREATED, PAYMENT_PENDING | Awaiting payment | — (not visible) | — |
| PAYMENT_FAILED | Payment failed | — | — |
| CONFIRMED | Waiting for restaurant | **New order** | — |
| RESTAURANT_ACCEPTED, PREPARING | Preparing your food (+ partner name once assigned) | Accepted / Preparing | Offer / Head to restaurant |
| READY_FOR_PICKUP | Food is ready | Ready | — |
| DELIVERY_ASSIGNED | Partner arriving at restaurant | Partner arriving | Pick up order |
| PICKED_UP, OUT_FOR_DELIVERY | On the way (live map) | Picked up | Deliver to customer |
| DELIVERED | Delivered | Delivered | Completed |
| RESTAURANT_REJECTED, CANCELLED, DELIVERY_FAILED | Cancelled / Failed (reason) | Cancelled | Cancelled |
| REFUND_PENDING, REFUNDED | Refund in progress / Refunded | — | — |

---

## 6. Test obligations

1. A unit test for every allowed transition (T1–T26) and every disallowed (state, action) pair. The disallowed pairs are generated from the table.
2. Concurrency test: parallel accept and cancel on one order. Exactly one succeeds, and the history is consistent.
3. Saga failure tests (REQ-ORDER-005 AC4): payment failure, payment timeout, restaurant rejection, accept timeout, no partner available, late payment after cancel, refund failure, crash mid-saga (Testcontainers, kill the consumer).
4. Contract tests for each emitted event against its JSON Schema.
