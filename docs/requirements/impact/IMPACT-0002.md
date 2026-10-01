# IMPACT-0002: Restaurant Operating System addendum (POS, kitchen, inventory, purchasing)

| Field | Value |
|---|---|
| Date | 2026-10-01 |
| Raised by | RequirementAgent (analysis of the ROS addendum, [index](../restaurant-os/README.md)) |
| Trigger | Project owner's ROS brief, 2026-10-01 |
| New requirements | 97 across PRODUCT, MENU (005–010), POS, ORDER (007–009), KOT, KDS, RECIPE, INV, PURCHASE, SUPPLIER, OUTLET, QR, BILL, plus REQ-ANALYTICS-002, REQ-AI-004, REQ-AI-005, REQ-PAYMENT-006 |
| New business requirement | BR-11 (in-store restaurant operations) |
| Approved requirements that change | Listed below. Each gets version N+1 and returns to ANALYSIS until re-approved. |
| Breaking change | No code exists for any affected requirement (the project is in Phase 3). The approved Phase 2 design changes, so explicit approval is needed. |
| Status | **Approved** 2026-10-01 by project owner |

## Why approved requirements change
The ROS brief introduces the master product vs menu item split, channels, order sources and types, in-store order lifecycles, ingredient inventory and staff roles. Those overlap requirements that are already APPROVED. Under the versioning rules they can't be edited in place: each one gets a new version, this impact report, and approval before implementation.

## Affected approved requirements

| Requirement | Current | Proposed change | New version |
|---|---|---|---|
| REQ-AUTH-003 (RBAC) | v1 APPROVED | Add roles CASHIER, CAPTAIN, KITCHEN_STAFF, INVENTORY_MANAGER (ROS-OQ-07) and brand/outlet-scoped permissions (REQ-OUTLET-002) | v2 |
| REQ-RESTAURANT-002 (branches, settings) | v1 | Location types: outlet, warehouse, central kitchen (REQ-OUTLET-001) | v2 |
| REQ-RESTAURANT-004 (staff) | v1 | Outlet assignment, staff PIN on shared devices (REQ-OUTLET-004) | v2 |
| REQ-MENU-001 (categories, products) | v1 | Split into brand master products (REQ-PRODUCT-001) and outlet menu items (REQ-MENU-005) | v2 |
| REQ-MENU-002 (variants, add-ons) | v1 | Variants and add-on groups defined at master level; outlet/channel price overrides (REQ-PRODUCT-002, -003, -007) | v2 |
| REQ-MENU-003 (availability and inventory) | v1 | Product-level tracked stock replaced by ingredient inventory and inventory-linked availability (REQ-MENU-009, REQ-INV-*). The manual toggle stays. | v2 |
| REQ-MENU-004 (menu caching) | v1 | Cache key adds channel and menu version (REQ-MENU-010) | v2 |
| REQ-CART-002 (pricing) | v1 | Price check passes the channel. Pricing logic shared with billing (REQ-BILL-001 AC4) | v2 |
| REQ-ORDER-001 (create order) | v1 | Orders can also be created by POS, captain, QR, phone and admin without a cart quote; `orderSource` and `orderType` are mandatory (REQ-ORDER-007) | v2 |
| REQ-ORDER-002 (state machine) | v2 (IMPACT-0001) | Lifecycles per order type; SERVED, HANDED_OVER and COMPLETED states; deferred payment for dine-in (REQ-ORDER-008, ROS-OQ-17) | v3 |
| REQ-ORDER-003 (restaurant accept/ready) | v1 | Acceptance generates KOTs; READY can come from the KDS roll-up (REQ-KOT-002, REQ-KDS-003) | v2 |
| REQ-RT-002 (real-time updates) | v1 | New kitchen, table and QR-session topics (REQ-KDS-002) | v2 |
| REQ-WEB-002 (restaurant dashboard) | v1 | Gains HQ catalogue, recipe, inventory and purchasing screens. POS and KDS become separate apps (new REQ-WEB-005, REQ-WEB-006, defined in design) | v2 |
| REQ-WALLET-002 (split payment, P2, ANALYSIS) | v1 | Superseded by REQ-BILL-004 → CANCELLED (ROS-OQ-16) | — |

**Not changed:** REQ-PAYMENT-002 webhook idempotency already covers BR-R6. REQ-AUDIT-001 covers BR-R12. REQ-PLAT-006 (outbox, idempotent consumers) covers ROS §27. REQ-AI-002 (controlled tools, no database access) already covers the restaurant tools. Delivery, location, review, search and notification requirements are only touched through new event fields.

## Impact on the approved Phase 2 design

| Area | Impact |
|---|---|
| Services | +4 new (pos-service, kitchen-service, inventory-service, procurement-service), menu-service renamed catalog-service (ROS-OQ-01). 21 → 25 deployables. |
| Databases | 4 new service databases; catalogue, order and restaurant schemas extended. ERD in the design stage. |
| APIs | New gateway routes for POS, kitchen, inventory, procurement and QR; channel parameter on menu and price-check APIs. |
| Events | New topics `kitchen.events.v1`, `inventory.events.v1`, `procurement.events.v1`, `pos.events.v1`, `catalog.events.v1`. `OrderCreated` gains `orderSource`, `orderType` and `tableSessionId` (additive, backward-compatible). |
| State machines | Order (per type), KOT, table, bill, purchase order, transfer. |
| Security | New roles and brand/outlet scope; device credentials and staff PINs; tenant isolation ADR. |
| Web apps | New `web/apps/pos` and `web/apps/kds`; QR ordering web; restaurant dashboard extended. |
| Mobile | No change to customer or partner apps. Optional Capacitor tablet build of POS (ROS-OQ-15). |
| Phases | New phases 13A–13E; Phases 6 and 8 grow (ROS-OQ-02). Phases 3–5 are not affected, except REQ-AUTH-003 v2 in Phase 5. |
| Documents to update in design | 04, 05, 06, 07, 08, 09, 13 (deployment), order-state-machine, screen inventory, ADR index (new ADRs: service boundaries for ROS, tenant isolation, inventory costing). |
| Tests | Requirement-level test obligations in each ROS document. New concurrency and tenant-isolation suites. |

## Regression scope
Nothing is implemented yet. Once implemented: the online ordering E2E suite (UJ-01 to UJ-08) must keep passing on the DELIVERY channel after the catalogue split and the order lifecycle change.

## Approval
- [x] ROS requirement documents approved, with ROS-OQ-01..22 decided as recommended (2026-10-01)
- [x] Version bumps of the affected requirements above approved (2026-10-01)
- [x] REQ-WALLET-002 cancellation approved (2026-10-01)

Pending: recording all of this in `requirements.json`, scheduled after Phase 3 completes (project owner's choice). At that point `requirements.json` gets the new requirements (ANALYSIS → APPROVED) and the version bumps with their history entries, and the validator is run. Then the design stage produces POS_ARCHITECTURE.md, INVENTORY_ARCHITECTURE.md, ORDER_ARCHITECTURE.md, KITCHEN_ARCHITECTURE.md, the ERD, service boundaries, API contracts, Kafka events, state machines and sequence diagrams (ROS §31).
