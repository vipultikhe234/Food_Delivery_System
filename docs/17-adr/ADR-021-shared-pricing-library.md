# ADR-021: Shared, framework-free pricing library
- Status: Accepted
- Date: 2026-10-01
- Deciders: Project owner (ROS design approved 2026-10-01)
- Related requirements: REQ-BILL-001 (AC4 requires one pricing library shared with cart-service), REQ-CART-002 v2, REQ-PRODUCT-004, REQ-PRODUCT-005, REQ-PRODUCT-007, REQ-BILL-004, REQ-BILL-009

## Context
Online quotes (cart-service), the price-check (catalog-service) and in-store bills (pos-service) must compute prices, combo apportionment, discounts, charges, tax and round-off the same way. Copies of the logic in three services would drift, and a tax rule change would need three coordinated releases.

## Decision
- New module `backend/platform/pricing`: plain Java 21, no Spring, no I/O, no database access. Inputs and outputs are immutable records with `BigDecimal` amounts.
- Contents: price-layer resolution over supplied price rows; add-on validation results; combo price, discount and apportionment; bill computation in the order documented in [POS_ARCHITECTURE §6.2](../architecture/restaurant-os/POS_ARCHITECTURE.md#62-computation-req-bill-001-d-15); split by item and by amount with residue rules; round-off.
- Rounding policy in one place: 4 decimals for intermediates, 2 for stored amounts, HALF_UP.
- Consumers: catalog-service (price-check), cart-service (quote), pos-service (bills). order-service doesn't compute prices itself; it calls the price-check.
- The module has 100 % branch coverage as a gate (it's pure logic), plus property-based tests (for example: split totals always equal the parent total).
- Versioned with the backend release train; a change to the computation order needs an ADR or requirement change, because it changes customer-facing totals.

## Consequences
### Positive
- One implementation and one test suite for every total the platform shows or charges.
- Tax adviser feedback (D-15) becomes one library change.

### Negative / risks
- A shared library couples the release of three services when it changes. Mitigation: it changes rarely, and contract tests in each consumer pin the behaviour.
- It must stay free of framework and domain-service dependencies to remain reusable; enforced with ArchUnit.

## Alternatives considered
- **A pricing microservice:** one more network hop on every quote and bill recalculation, and one more deployable (MP §53).
- **Logic duplicated per service:** guaranteed drift between online and in-store totals.
