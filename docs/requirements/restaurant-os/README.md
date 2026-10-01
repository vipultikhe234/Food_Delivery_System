# Restaurant Operating System addendum — requirements index

| Field | Value |
|---|---|
| Source | Project owner's "Additional module — Advanced Restaurant POS + Product + Inventory Platform" brief, received 2026-10-01. Cited as **ROS §n** (section numbers below). |
| Stage | Requirement analysis (ROS §31, step 1). No architecture or code yet. |
| Status | **Approved 2026-10-01** by the project owner, with all 22 open questions decided as recommended (table below). Registered in `requirements.json` as APPROVED with history; phases use `phase` 13 plus `subPhase` A–E for 13A–13E. |
| Impact on the approved baseline | [IMPACT-0002](../impact/IMPACT-0002.md) |
| Constraint | Independent design. Commercial POS products are used only as a reference for publicly observable capabilities. No code, UI, branding, API or schema is copied (ROS preamble). |

## ROS section index

| § | Title | § | Title |
|---|---|---|---|
| 1 | Product catalog | 17 | Low stock |
| 2 | Master product vs menu product | 18 | Purchase management |
| 3 | Menu service | 19 | Multi outlet |
| 4 | Product customization | 20 | Central kitchen |
| 5 | Combo engine | 21 | QR ordering |
| 6 | Restaurant POS | 22 | Billing |
| 7 | Order source | 23 | Split bill |
| 8 | Order type | 24 | Reporting |
| 9 | Table management | 25 | AI restaurant operations agent |
| 10 | KOT system | 26 | AI action approval |
| 11 | Kitchen display system | 27 | Event architecture |
| 12 | Kitchen station | 28 | Requirement traceability |
| 13 | Recipe management | 29 | Testing |
| 14 | Inventory | 30 | Important business rules (BR-R1..BR-R12 below) |
| 15 | Automatic inventory consumption | 31 | First implementation phase |
| 16 | Inventory cost | — | Final objective |

## Documents

| Document | Module code | Requirements | Proposed owning service |
|---|---|---|---|
| [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) | PRODUCT | REQ-PRODUCT-001..009 | menu-service, renamed catalog-service (ROS-OQ-01) |
| [MENU_REQUIREMENTS.md](MENU_REQUIREMENTS.md) | MENU | REQ-MENU-005..010 (001..004 exist) | menu-service / catalog-service |
| [POS_REQUIREMENTS.md](POS_REQUIREMENTS.md) | POS, ORDER | REQ-POS-001..012, REQ-ORDER-007..009 | pos-service (new), order-service |
| [KOT_REQUIREMENTS.md](KOT_REQUIREMENTS.md) | KOT | REQ-KOT-001..008 | kitchen-service (new) |
| [KDS_REQUIREMENTS.md](KDS_REQUIREMENTS.md) | KDS | REQ-KDS-001..006 | kitchen-service, realtime-service |
| [RECIPE_REQUIREMENTS.md](RECIPE_REQUIREMENTS.md) | RECIPE | REQ-RECIPE-001..006 | inventory-service (new) |
| [INVENTORY_REQUIREMENTS.md](INVENTORY_REQUIREMENTS.md) | INV | REQ-INV-001..011 | inventory-service |
| [PURCHASE_REQUIREMENTS.md](PURCHASE_REQUIREMENTS.md) | PURCHASE | REQ-PURCHASE-001..006 | procurement-service (new) |
| [SUPPLIER_REQUIREMENTS.md](SUPPLIER_REQUIREMENTS.md) | SUPPLIER | REQ-SUPPLIER-001..004 | procurement-service |
| [MULTI_OUTLET_REQUIREMENTS.md](MULTI_OUTLET_REQUIREMENTS.md) | OUTLET | REQ-OUTLET-001..007 | restaurant-service, inventory-service |
| [QR_ORDER_REQUIREMENTS.md](QR_ORDER_REQUIREMENTS.md) | QR | REQ-QR-001..006 | pos-service |
| [BILLING_REQUIREMENTS.md](BILLING_REQUIREMENTS.md) | BILL | REQ-BILL-001..009 | pos-service (billing module), payment-service |

**Cross-cutting requirements** that don't belong to one of the 12 documents:

| ID | Title | Priority | Source | Notes |
|---|---|---|---|---|
| REQ-ANALYTICS-002 | Restaurant operations reports, generated asynchronously | P1 | ROS §24 | Daily, hourly, product, category, outlet, payment, tax, discount, cancellation, refund, inventory, purchase, food cost, waste, gross margin, net sales. Large reports run as background jobs and produce a downloadable file in object storage (not PostgreSQL). |
| REQ-AI-004 | Restaurant operations assistant with read-only controlled tools | P1 | ROS §25 | Tools: getSales, getInventory, getProductSales, getFoodCost, getWaste, getPurchases, getLowStock, getOutletPerformance, getOrderStatistics. Tenant- and outlet-scoped by the caller's JWT. No database access (REQ-AI-002). Extends ai-service; no new service. |
| REQ-AI-005 | AI action proposals with explicit human authorisation | P1 | ROS §26, BR-R10 | Price change, product disable, purchase order, recipe change, inventory change, refund: the AI produces Recommendation, Reason, Expected impact and Required approval. Nothing executes until a user with the matching permission approves it. The approval is audited and executes through the owning service's normal API. |
| REQ-PAYMENT-006 | In-store payment recording (cash, card terminal, UPI) | P0 | ROS §6, §22 | Trust model in ROS-OQ-08. |

## Business rules (ROS §30)

| ID | Rule | Enforced by |
|---|---|---|
| BR-R1 | Inventory cannot go negative unless the outlet explicitly allows it | REQ-INV-005 |
| BR-R2 | Completed orders cannot be silently modified | REQ-ORDER-009 |
| BR-R3 | Historical bills cannot change | REQ-BILL-006 |
| BR-R4 | Historical recipes remain available | REQ-RECIPE-002 |
| BR-R5 | Stock movements are immutable | REQ-INV-003 |
| BR-R6 | Payment webhooks are idempotent | REQ-PAYMENT-002 (existing) |
| BR-R7 | KOT modifications keep history | REQ-KOT-003, REQ-KOT-007 |
| BR-R8 | Order state transitions are validated | REQ-ORDER-002 (v3, IMPACT-0002) |
| BR-R9 | Menu availability reflects inventory where configured | REQ-MENU-009 |
| BR-R10 | AI cannot modify critical data without authorisation | REQ-AI-005 |
| BR-R11 | Multi-tenant data is isolated | REQ-OUTLET-003 |
| BR-R12 | Every important admin operation is audited | REQ-AUDIT-001 (existing), referenced per document |

New business requirement: **BR-11** — Restaurants run their in-store operations on the platform: POS, tables, kitchen, inventory, purchasing, multi-outlet and central kitchen (ROS preamble, final objective).

## Terminology

| Term | Meaning |
|---|---|
| Brand / tenant | A restaurant business (`restaurants` in restaurant-service). The tenant boundary for BR-R11. "HQ" = users with brand-wide scope. |
| Outlet | A branch. The approved baseline calls this a **branch** (assumption A4). ROS-OQ-18 proposes keeping `branch` in code and APIs, and "outlet" in the UI. |
| Location | Any place that holds stock: an outlet store, a warehouse or a central kitchen. |
| Master product | Brand-level product definition (ROS §2). |
| Menu item | Outlet- and channel-specific listing of a master product (price, availability, tax, prep time, station). |
| KOT | Kitchen order ticket: the set of items one kitchen station must prepare for one order at one moment. |

## Event additions (ROS §27)
ROS §27 asks for idempotency, retry, DLQ, versioning and correlation IDs. These already exist in the approved baseline (REQ-PLAT-006, ADR on the outbox and idempotent consumers), so no new platform requirement is needed. New events are listed in each document. Two names in ROS §27 match events that already exist under other names. Approved event names don't change (event versioning), so the mapping is:

| ROS §27 name | Existing event |
|---|---|
| OrderAccepted | `RestaurantAcceptedOrder` (order.events.v1) |
| OrderReady | `OrderReady` (unchanged) |
| OrderCreated, PaymentCompleted, RefundCreated, ProductUpdated, MenuUpdated | Unchanged |

## Testing obligations (ROS §29)
Each document lists its test obligations. All ROS modules need unit, integration, API, E2E and regression tests. Concurrency tests are mandatory for stock consumption, table sessions, split payments and KOT sequencing. Security tests are mandatory for tenant isolation and staff permissions. Performance targets need NFRs (ROS-OQ-19). Visual tests apply to the POS, KDS and QR screens.

## Open questions
**All decided 2026-10-01 by the project owner: every recommendation below was accepted.** The "Recommendation" column is therefore the decision.

| ID | Question | Recommendation |
|---|---|---|
| ROS-OQ-01 | Service boundaries. MP §53 forbids unnecessary microservices. | 4 new services: **pos-service** (tables, sessions, QR, hold orders, bills), **kitchen-service** (stations, KOT, KDS, printers), **inventory-service** (stock, recipes, costing, transfers, central kitchen), **procurement-service** (suppliers, purchasing). menu-service becomes **catalog-service** (master products + menus). Orders stay in order-service. Payments stay in payment-service. Reports go to analytics-service and AI to ai-service. That makes 25 deployables instead of 21. Alternative: merge procurement into inventory (24). |
| ROS-OQ-02 | Where do these modules go in the 24-phase plan? | Catalogue changes fold into Phase 6, and order source/type into Phase 8. New phases **13A** (POS, tables, billing), **13B** (kitchen: KOT, KDS), **13C** (inventory, recipes, consumption), **13D** (purchasing, suppliers, central kitchen) and **13E** (QR ordering) come before Phase 14, which then also builds the POS and KDS web apps. Restaurant AI goes in Phase 17. Existing phase numbers don't change. |
| ROS-OQ-03 | Scope. The portfolio scale chosen earlier roughly doubles with this addendum. | Use the priorities in each document. P0 = POS order, tables, billing and split, KOT/KDS, master/menu split, recipes, inventory consumption, low stock, tenant isolation. P1 = purchasing, suppliers, central kitchen, QR ordering, costing, AI operations. P2 = reservations, dynamic pricing, supplier ledger. |
| ROS-OQ-04 | Does the POS need to work offline? | v1 is **online-only** with a clear "connection lost" state and a safe retry (Idempotency-Key). Offline order capture is a P2 backlog item, because it needs local storage, conflict resolution and offline KOT printing. |
| ROS-OQ-05 | Printing and hardware (KOT printers, receipt printers, barcode scanners, cash drawer) | v1: browser printing of 80 mm KOT and receipt layouts. Barcode scanners work as keyboard input. A local ESC/POS print bridge and cash-drawer kick are P2. |
| ROS-OQ-06 | Aggregator orders (`orderSource = AGGREGATOR`) | v1 supports the enum plus an `AggregatorAdapter` port with a mock adapter. Real aggregator integration needs partner API agreements, so it's out of scope until those exist. |
| ROS-OQ-07 | New staff roles | Add `CASHIER`, `CAPTAIN`, `KITCHEN_STAFF` and `INVENTORY_MANAGER`. They are implied by ROS §6, §7, §11 and §14 but not named in the brief. The existing `RESTAURANT_OWNER` acts as HQ and `RESTAURANT_MANAGER` as the outlet manager (ROS §19). |
| ROS-OQ-08 | Trust model for in-store payments. "Never trust frontend payment status" (MP). | Cash and card-terminal payments are **staff-attested**: recorded server-side by an authenticated cashier, audited, and reconciled at day close. They are never inferred from client state. UPI at the counter uses a gateway dynamic QR confirmed by webhook. Static UPI is staff-attested. |
| ROS-OQ-09 | When is stock consumed? ROS §15 says "when order is completed". | Consume when the order is **completed** (served and settled, picked up, or delivered), as the brief says. Orders cancelled after their KOT reached PREPARING record the same quantities as **waste**, with reason `CANCELLED_AFTER_PREP`. |
| ROS-OQ-10 | BR-R1 (no negative stock) conflicts with consumption on completion: the food is already served when consumption runs. | Check availability **when the order is placed** (REQ-MENU-009 hides or blocks items without stock). At completion, consumption always posts. If it would take stock negative and the outlet disallows negative stock, the balance is clamped at zero and a `STOCK_DISCREPANCY` alert is raised for a count. Ledger entries are never rejected or rewritten. |
| ROS-OQ-11 | ROS §16 lists "Average cost" and "Weighted average" separately | **WEIGHTED_AVERAGE** (moving average, recalculated on each receipt) and **FIFO** (by batch), configurable per brand. "Average cost" is read as the reported unit cost, not a third method. |
| ROS-OQ-12 | QR orders: does the kitchen get them directly, and how does the guest pay? | After OTP verification the order goes to the KOT automatically if the outlet enables "QR auto-accept". Otherwise a captain or cashier accepts it first (default). Payment: pay at the table session's bill (default), or online payment if the outlet enables it. |
| ROS-OQ-13 | Service charge | Since the 2022 CCPA guidelines in India, a service charge can't be added automatically or compulsorily. Proposal: it's off by default, can be configured per outlet, is shown separately and can be removed per bill. |
| ROS-OQ-14 | Invoice numbering | Sequential, gap-free invoice numbers per outlet per financial year, which GST invoicing requires (assumption A1, India-first). |
| ROS-OQ-15 | POS platform ("dedicated POS application", ROS §6) | React web app `web/apps/pos`, optimised for tablet and touch, installable as a PWA. A Capacitor Android tablet build is optional later. |
| ROS-OQ-16 | REQ-WALLET-002 (split payment, P2 backlog) overlaps REQ-BILL-004 | Approved as "cancel REQ-WALLET-002, superseded by REQ-BILL-004". **Corrected during registration:** REQ-BILL-004 covers in-store bills only, while REQ-WALLET-002 covers online checkout (wallet + gateway). Cancelling it would remove functionality, so REQ-WALLET-002 stays in the backlog unchanged. |
| ROS-OQ-17 | Order completion. ROS §27 names `OrderCompleted`; the approved delivery flow ends at DELIVERED. | Add a terminal `COMPLETED` status for every order type. Delivery orders go DELIVERED → COMPLETED automatically. |
| ROS-OQ-18 | Outlet vs branch naming | Keep `branch` in code and APIs (approved baseline); show "Outlet" in the POS and HQ UI. |
| ROS-OQ-19 | POS and KDS performance targets are not in the brief | PROPOSED NFRs: POS order submit p95 < 500 ms; KOT visible on KDS p95 < 2 s after acceptance; consumption posted p95 < 10 s after completion. These will be measured, not claimed (MP). |
| ROS-OQ-20 | Cashier shifts and cash-drawer reconciliation are not in the brief, but cash payments need them for control | Add as a P1 requirement (REQ-POS-013) only if you approve it. Not included until then. |
| ROS-OQ-21 | Recipes for add-ons and variants, and sub-recipes (semi-finished goods such as dough made in the central kitchen) | Include both (REQ-RECIPE-005, REQ-RECIPE-006, marked PROPOSED). Without them, consumption of "Extra cheese" and central-kitchen production can't be tracked. |
| ROS-OQ-22 | Table reservations (ROS §9 lists `TableReservation`) | P2: create, assign table, no-show. No online booking widget in v1. |
