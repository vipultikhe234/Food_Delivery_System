# ADR-018: Restaurant OS service boundaries (25 deployables)
- Status: Proposed
- Date: 2026-10-01
- Deciders: Project owner (ROS-OQ-01 decided 2026-10-01); this ADR records the resulting design
- Related requirements: BR-11, REQ-POS-*, REQ-BILL-*, REQ-QR-*, REQ-KOT-*, REQ-KDS-*, REQ-INV-*, REQ-RECIPE-*, REQ-PURCHASE-*, REQ-SUPPLIER-*, REQ-PRODUCT-*, REQ-MENU-005..010, REQ-OUTLET-*
- Amends: [ADR-001](ADR-001-microservices.md) (service count 21 → 25; ADR-001 otherwise stands)

## Context
The Restaurant OS addendum adds POS, tables, billing, kitchen tickets and displays, recipes, inventory, purchasing, multi-outlet and QR ordering. MP §53 forbids unnecessary microservices, so each new boundary needs a reason: a distinct data owner, a distinct consistency boundary, or a distinct scaling or availability profile. The project owner chose four new services and the rename of menu-service (ROS-OQ-01).

## Decision
- **pos-service** (8102): floor (areas, tables, sessions), held orders, QR codes and sessions, bills and invoices. Bills have their own strong-consistency needs (gap-free invoice numbers, immutable finalised bills, settlement locking) that don't belong in order-service, which stays the single owner of orders.
- **kitchen-service** (8103): stations, KOTs, KDS state, printing. Kitchen traffic is bursty, latency-sensitive (NFR-PERF-006) and must keep working while reporting or purchasing are slow.
- **inventory-service** (8104): stock, ledger, consumption, costing, transfers, central kitchen, recipes. Recipes live here because consumption and costing are the main readers and must use the exact version that was effective.
- **procurement-service** (8105): suppliers and purchase documents. It holds supplier bank details (encrypted) and approval workflows, and has a different set of users (HQ, inventory managers).
- **menu-service is renamed catalog-service** before any code exists, and holds master products and outlet menus.
- Orders stay in order-service, payments in payment-service (now also in-store payments), reports in analytics-service, AI in ai-service, devices in restaurant-service (registry) and identity-service (credentials).
- Communication follows the approved rules: REST for queries and commands that need an immediate answer; Kafka with the outbox for facts. No synchronous call is on the order-completion path.

## Consequences
### Positive
- Each financial or stock record has one owner and one consistency boundary (bills, ledger, KOTs).
- Kitchen screens stay responsive regardless of back-office load.
- The phase plan can build and test each area separately (13A–13E).

### Negative / risks
- 25 deployables raise local resource use. Mitigation: Compose profiles per phase, and the existing cost-minimised AWS plan (ADR-016) with small requests for the new services.
- More cross-service flows (order ↔ kitchen ↔ pos ↔ inventory), each needing contract tests and idempotent consumers.

## Alternatives considered
- **Merge procurement into inventory (24 deployables):** fewer services, but mixes supplier PII and approval workflows with the high-write ledger. Kept as a fallback if operating cost becomes an issue.
- **Put tables and bills into order-service:** one fewer service, but makes order-service the owner of invoices and floor state, and couples the online saga to in-store settlement.
- **One "restaurant-ops" service for everything new:** simplest to deploy, but a single large data owner with very different load profiles (kitchen bursts vs nightly reconciliation).
