# KOT requirements

| Field | Value |
|---|---|
| Module | KOT |
| Status | DRAFT (see [README](README.md)) |
| Sources | ROS §10, §12, §30.7 |
| Proposed owner | kitchen-service (new, ROS-OQ-01) |
| Proposed phase | 13B |
| Affects | REQ-ORDER-003 (accept now triggers KOT generation) |

## Scope
Turning accepted order items into kitchen order tickets per kitchen station, and keeping a complete, immutable history of every ticket and change.

Flow (ROS §10): Order → Order items → Kitchen station routing → KOT.

## Entities (ROS §12 and §10)
KitchenStation, KitchenPrinter, KitchenDisplay, StationProductMapping, Kot, KotItem, KotEvent (history).

## Requirements

| ID | Title | Priority | Depends on | Source |
|---|---|---|---|---|
| REQ-KOT-001 | Kitchen stations and item routing | P0 | REQ-MENU-005 | ROS §10, §12 |
| REQ-KOT-002 | KOT generation on order acceptance | P0 | KOT-001, REQ-ORDER-003 | ROS §10 |
| REQ-KOT-003 | Modified KOT | P0 | KOT-002 | ROS §10, §30.7 |
| REQ-KOT-004 | Cancelled KOT | P0 | KOT-002 | ROS §10 |
| REQ-KOT-005 | KOT reprint | P0 | KOT-002 | ROS §10 |
| REQ-KOT-006 | Partial KOT (course firing) | P1 | KOT-002 | ROS §10 |
| REQ-KOT-007 | Immutable KOT history | P0 | KOT-002 | ROS §10, §30.7 |
| REQ-KOT-008 | Kitchen printers | P1 | KOT-002 | ROS §12, ROS-OQ-05 |

### REQ-KOT-001 — Stations and routing
- AC1: An outlet defines kitchen stations (for example Pizza station, Main kitchen, Beverage, Dessert, Fryer) and one default station.
- AC2: Each menu item maps to one station (StationProductMapping). Unmapped items go to the default station.
- AC3: Each KOT item carries its station, preparation time and priority (ROS §10). Priority values (for example NORMAL, RUSH) are fixed in design.
- AC4: Combo lines are routed per slot item (REQ-PRODUCT-004 AC4). Example: Pizza → PIZZA_STATION, Fries → FRY_STATION, Coffee → BEVERAGE_STATION.

### REQ-KOT-002 — Generation
- AC1: When an order is accepted (online: restaurant accept T8; POS, captain, QR: send to kitchen), one KOT is created per station that has items.
- AC2: KOT numbers are sequential per outlet per business day. Concurrent orders never get the same number (concurrency test).
- AC3: Generation is idempotent per (order, round). A replayed `RestaurantAcceptedOrder` or `KitchenRoundSubmitted` event doesn't create duplicate KOTs.
- AC4: Each KOT shows order number, order type, table (dine-in), source, items with variant, add-ons, quantity and notes, and the time created.
- AC5: `KOTCreated` is published per KOT.

### REQ-KOT-003 — Modified KOT
- AC1: Items added to an order after a KOT create a **new** KOT for the added items only.
- AC2: A quantity reduction or item void after a KOT creates a modification KOT that references the original KOT and shows the change ("−1 Paneer Pizza").
- AC3: The original KOT is never edited (BR-R7). `KOTUpdated` is published.

### REQ-KOT-004 — Cancelled KOT
- AC1: Cancelling an order or all items of a KOT creates a cancellation record and marks the KOT CANCELLED, with reason and user.
- AC2: The station is alerted on the KDS (REQ-KDS-006) and on the printer if configured.

### REQ-KOT-005 — Reprint
- AC1: Any KOT can be reprinted. The print is marked "REPRINT n" and the reprint is audited.

### REQ-KOT-006 — Partial KOT
- AC1: A dine-in order can hold items back (for example mains) and send them later ("fire"). Each send creates its own KOTs.
- AC2: Held-back items are visible on the POS until fired or cancelled.

### REQ-KOT-007 — History
- AC1: Every KOT state change and modification is stored as an append-only event with user, device, time and reason.
- AC2: The full history of an order's KOTs can be viewed by managers and is kept for at least as long as orders are retained.

### REQ-KOT-008 — Printers
- AC1: A station can have a printer. v1 prints through the browser on a kitchen device (ROS-OQ-05).
- AC2: A print failure is visible on the POS and the KDS, and the KOT can be reprinted.

## Events
`KOTCreated`, `KOTUpdated`, `KOTCancelled`, `KOTReprinted` (kitchen.events.v1).

## Test obligations
- Unit: routing including combos and the default station; modification diff.
- Concurrency: KOT numbering under parallel orders.
- Integration: idempotent generation under event replay.
- E2E: add item after KOT → new KOT; void after KOT → modification KOT visible on the KDS.
