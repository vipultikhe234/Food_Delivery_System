# IMPACT-0001: Early delivery-partner assignment vs. linear order status

| Field | Value |
|---|---|
| Date | 2026-10-01 |
| Raised by | ArchitectAgent (Phase 2, order state machine design) |
| Requirements changed | REQ-ORDER-002 v1 → v2, REQ-RT-003 v1 → v2 |
| Breaking change | No. Nothing is implemented yet; this clarifies the design. |
| Status | **Approved** 2026-10-01 by project owner |

## Change detected
REQ-ORDER-002 v1 AC1 allowed `DELIVERY_ASSIGNED` to occur *before* `READY_FOR_PICKUP`. REQ-DELIVERY-003 deliberately starts assignment during preparation, so this case is common.

An order has exactly one status. "Preparing" and "partner assigned" can be true at the same time, so v1 was ambiguous:

- Should a restaurant marking the order ready move it *backwards* from DELIVERY_ASSIGNED to READY_FOR_PICKUP?
- Or does READY_FOR_PICKUP get skipped?

## Resolution (v2)
- The order status stays **linear**, exactly as in MP §11.
- Partner assignment is recorded on the order as **attributes** (`deliveryPartnerId`, `partnerAssignedAt`) as soon as `DeliveryAssigned` arrives, whatever the status.
- When the order reaches `READY_FOR_PICKUP` and a partner is already assigned, the system moves it to `DELIVERY_ASSIGNED` immediately.
- New AC7: if the partner is released, `DELIVERY_ASSIGNED → READY_FOR_PICKUP` and reassignment starts.
- REQ-RT-003: live tracking starts at the `DeliveryAssigned` *event*, not at the order status. This lets the customer watch the partner travel to the restaurant.

## Impact

| Area | Impact |
|---|---|
| Services | order-service (state machine, consumer of DeliveryAssigned), delivery-service (none; already event-based), realtime-service (tracking start condition) |
| APIs | `GET /api/v1/orders/{id}` and the tracking endpoint expose `deliveryPartner` and `partnerAssignedAt` independently of `status` |
| Database | `orders.delivery_partner_id`, `orders.partner_assigned_at`, `orders.ready_at` (already in the 06-database-design draft) |
| Events | `OrderDeliveryAssigned` is emitted when the status changes; `DeliveryAssigned` (delivery.events) remains the trigger for tracking |
| Web screens | Customer tracking page: show partner card plus map while the status is still PREPARING |
| Mobile screens | Same as web (customer mobile) |
| Tests | Transition table tests; early-assignment test; partner-release test; tracking privacy test still applies |
| Documentation | `docs/architecture/order-state-machine.md`, `08-event-driven-architecture.md`, `06-database-design.md` |

## Regression scope
Nothing is implemented yet. Once implemented, the regression suite is the order-service state machine suite plus the tracking E2E tests.

## Approval
- [x] Approved by project owner on 2026-10-01. REQ-ORDER-002 v2 and REQ-RT-003 v2 are now APPROVED (recorded in `requirements.json` statusHistory).
