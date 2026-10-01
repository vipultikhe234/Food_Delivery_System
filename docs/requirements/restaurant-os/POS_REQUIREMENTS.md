# POS requirements

| Field | Value |
|---|---|
| Modules | POS (new), ORDER (extends REQ-ORDER-001..006) |
| Status | DRAFT (see [README](README.md)) |
| Sources | ROS §6, §7, §8, §9, §28, §30 |
| Proposed owner | pos-service (new): tables, sessions, held orders, bills. order-service stays the single owner of all orders. |
| Proposed phase | 13A (backend). The POS web app is built in Phase 14 (ROS-OQ-02). |
| Affects | REQ-ORDER-001, -002, -003, REQ-RESTAURANT-004, REQ-AUTH-003, REQ-WEB-002 (IMPACT-0002) |

## Scope
A dedicated POS application for cashiers and captains. It covers dine-in, takeaway, pickup, phone and delivery orders entered at the outlet, plus table management. Every order, whatever its source, lives in order-service so there is one order history, one state machine and one set of reports.

## Order source and order type

| Field | Allowed values (ROS §7, §8) |
|---|---|
| `orderSource` | `POS`, `CUSTOMER_WEB`, `CUSTOMER_MOBILE`, `QR`, `CAPTAIN`, `WEBSITE`, `PHONE`, `AGGREGATOR`, `ADMIN` |
| `orderType` | `DINE_IN`, `TAKEAWAY`, `DELIVERY`, `PICKUP`, `QR_ORDER` |

## Entities
Table management (ROS §9): RestaurantArea, Table, TableSession, TableReservation, TableOrder, TableMerge, TableTransfer. POS: HeldOrder (draft cart), PosDevice. Bills are in [BILLING_REQUIREMENTS.md](BILLING_REQUIREMENTS.md).

## Requirements

| ID | Title | Priority | Depends on | Source |
|---|---|---|---|---|
| REQ-ORDER-007 | Order source and order type on every order | P0 | REQ-ORDER-001 | ROS §7, §8 |
| REQ-ORDER-008 | Order lifecycles per order type | P0 | REQ-ORDER-002, ORDER-007 | ROS §8, §30.8, final objective |
| REQ-ORDER-009 | Completed orders are immutable | P0 | ORDER-008, REQ-AUDIT-001 | ROS §30.2 |
| REQ-POS-001 | POS order creation | P0 | ORDER-007, REQ-MENU-006, REQ-OUTLET-004 | ROS §6, §28 |
| REQ-POS-002 | Product search, categories and shortcuts | P0 | REQ-MENU-006 | ROS §6 |
| REQ-POS-003 | Hold and resume orders | P0 | POS-001 | ROS §6 |
| REQ-POS-004 | Discounts, coupons and charges at the POS | P0 | POS-001, REQ-PROMO-002, REQ-BILL-001 | ROS §6 |
| REQ-POS-005 | Cancel orders and void items | P0 | POS-001, REQ-KOT-004 | ROS §6 |
| REQ-POS-006 | Areas, tables and table states | P0 | REQ-OUTLET-001 | ROS §9 |
| REQ-POS-007 | Table sessions, transfer and merge | P0 | POS-006 | ROS §9 |
| REQ-POS-008 | Table reservations | P2 | POS-006 | ROS §9, ROS-OQ-22 |
| REQ-POS-009 | Captain ordering | P1 | POS-001, POS-007 | ROS §7 |
| REQ-POS-010 | Phone orders | P1 | POS-001 | ROS §7 |
| REQ-POS-011 | Receipts and reprints | P0 | REQ-BILL-002 | ROS §6, ROS-OQ-05 |
| REQ-POS-012 | Refunds from the POS | P0 | REQ-BILL-007 | ROS §6 |

### REQ-ORDER-007 — Order source and type
- AC1: Every order has a non-null `orderSource` and `orderType` from the tables above. The API rejects anything else.
- AC2: The source is set by the server from the authenticated client (POS device, captain app, QR session, customer app). Clients can't choose it, so reports can trust it.
- AC3: Valid combinations are enforced. For example `QR` → `QR_ORDER` or `DINE_IN`; `DELIVERY` needs a delivery address; `DINE_IN` needs a table session.
- AC4: Both fields are in `OrderCreated` and in every report dimension (REQ-ANALYTICS-002).

### REQ-ORDER-008 — Lifecycles per order type (BR-R8)
- AC1: Delivery orders keep the approved state machine (T1–T26).
- AC2: Dine-in and QR orders: payment is deferred to the table bill. The order goes to the kitchen when accepted, and moves through PREPARING → READY → SERVED → COMPLETED. COMPLETED is reached when the bill is settled.
- AC3: Takeaway and pickup orders move through READY → HANDED_OVER → COMPLETED. Prepaid or pay-at-counter is configured per outlet.
- AC4: Every transition is validated against the type's transition table. Invalid transitions return 409 and are never applied.
- AC5: The exact states and transitions are designed in ORDER_ARCHITECTURE.md (design stage) and change REQ-ORDER-002 to version 3 (IMPACT-0002). `COMPLETED` for all types depends on ROS-OQ-17.

### REQ-ORDER-009 — Completed orders are immutable (BR-R2)
- AC1: Lines, prices, taxes and totals of a COMPLETED or CANCELLED order can't be changed by any API.
- AC2: Corrections after completion are separate, linked records (refund, credit note, complimentary adjustment), each audited.
- AC3: A database-level guard (trigger or constraint, chosen in design) rejects updates to completed order lines, in addition to the service check.

### REQ-POS-001 — POS order creation
- AC1: A cashier picks the order type, adds products with variant, add-ons and quantity, optionally attaches a customer (phone, name) and a table (dine-in), and submits.
- AC2: Prices, taxes and charges are recalculated server-side from the outlet's menu for the order's channel. Totals sent by the POS are ignored.
- AC3: Submission is idempotent (Idempotency-Key, REQ-PLAT-006). A retry after a timeout never creates a second order.
- AC4: On acceptance, KOTs are generated per kitchen station (REQ-KOT-002). Dine-in orders can be sent to the kitchen in rounds within the same table session.
- AC5: Traceability example (ROS §28): order-service (`orders`, `order_items`); POS web; tests POS-API-001, POS-E2E-001, POS-MOBILE-001 (if the Capacitor tablet build is approved, ROS-OQ-15).

### REQ-POS-002 — Search and navigation
- AC1: Search by name, short code or barcode returns matches in under the NFR target. Scanners act as keyboard input (ROS-OQ-05).
- AC2: Category navigation follows the outlet's channel menu order.
- AC3: Unavailable items are shown greyed out and can't be added.

### REQ-POS-003 — Hold and resume
- AC1: A cashier can hold an unsent order with a label. Held orders are stored server-side per outlet, so another POS device can resume them.
- AC2: A held order is re-priced when resumed. Price changes are shown before sending.
- AC3: Held orders expire at the outlet's business-day close and are logged.

### REQ-POS-004 — Discounts, coupons, charges
- AC1: Item- or order-level discounts (percentage or amount) need the `POS_DISCOUNT` permission and a reason. Discounts above an outlet threshold need manager approval (PIN or approval request).
- AC2: Coupons are validated by promotion-service (REQ-PROMO-002).
- AC3: Packaging charge (per item or per order) and service charge follow outlet configuration. Service charge follows ROS-OQ-13.
- AC4: Every discount and waived charge is audited and appears in the discount report.

### REQ-POS-005 — Cancel and void
- AC1: Before any KOT, an order or item can be cancelled with a reason.
- AC2: After a KOT, cancellation needs `POS_VOID` permission and a reason, and produces a cancelled or modified KOT (REQ-KOT-003, -004).
- AC3: If the item's KOT has reached PREPARING, the cancellation also records waste (ROS-OQ-09).
- AC4: Cancellations appear in the cancellation report with user, time, reason and amount.

### REQ-POS-006 — Areas and tables
- AC1: An outlet has areas (for example Ground floor, Terrace), each with tables (number, capacity).
- AC2: Table states: `AVAILABLE`, `OCCUPIED`, `RESERVED`, `CLEANING`, `BLOCKED`. Allowed transitions are defined in design. Invalid changes are rejected.
- AC3: The floor plan shows every table's state, elapsed session time and running bill amount in real time across devices.

### REQ-POS-007 — Sessions, transfer, merge
- AC1: Seating guests opens a table session (guest count, captain). The table becomes OCCUPIED. All dine-in orders of the session attach to it.
- AC2: Transfer moves an open session to an AVAILABLE table. History records both tables.
- AC3: Merge combines two or more open sessions into one bill. History keeps the original sessions.
- AC4: Concurrent actions on one table (two devices) are serialised. One wins, the other gets a conflict error. There is a concurrency test.
- AC5: Settling the bill closes the session and moves the table to CLEANING. Staff mark it AVAILABLE.

### REQ-POS-008 — Reservations
- AC1: Staff create a reservation (name, phone, guests, time, optional table). The table shows RESERVED from a configurable lead time.
- AC2: No-shows are released after a grace period and logged.

### REQ-POS-009 — Captain ordering
- AC1: A captain takes orders at the table on a handheld (POS web on a phone or tablet). The source is `CAPTAIN`.
- AC2: Captains can't apply discounts or settle bills unless their role grants it.

### REQ-POS-010 — Phone orders
- AC1: Staff enter phone orders (source `PHONE`) for takeaway, pickup or delivery. Delivery needs an address validated by location-service.
- AC2: Phone delivery orders use the same delivery flow as online orders (REQ-DELIVERY-*), with COD or a payment link.

### REQ-POS-011 — Receipts
- AC1: A receipt can be printed (80 mm layout) or sent digitally (SMS or email link) after settlement.
- AC2: Reprints are allowed, marked "DUPLICATE", counted and audited.

### REQ-POS-012 — Refunds
- AC1: Full or partial refunds against a settled bill need `ORDER_REFUND` within the role's limit (the approved thresholds: ₹500 support / ₹5,000 admin / above that SUPER_ADMIN apply to platform staff; outlet limits are configured per brand).
- AC2: The refund goes back to the original method. Online payments go through payment-service. Cash refunds are recorded as staff-attested (ROS-OQ-08).
- AC3: Historical bills don't change. The refund is a linked record (BR-R3).

## Events
`OrderCreated` (now with `orderSource`, `orderType`, `tableSessionId`), `OrderServed`, `OrderHandedOver`, `OrderCompleted`, `TableSessionOpened`, `TableSessionClosed`, `TableTransferred`, `TablesMerged`, `TableStatusChanged`.

## Test obligations
- Unit: source/type combinations; order-type transition tables; discount permissions.
- Concurrency: two devices transferring or merging the same table; duplicate submit with the same Idempotency-Key.
- E2E: dine-in round trip (seat → order → KOT → KDS ready → serve → bill → split pay → table CLEANING); takeaway; phone delivery.
- Security: cashier of outlet A can't act on outlet B; captain can't discount.
- Visual: POS order screen, floor plan, receipt layout.
