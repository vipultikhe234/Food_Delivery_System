# Inventory requirements

| Field | Value |
|---|---|
| Module | INV |
| Status | DRAFT (see [README](README.md)) |
| Sources | ROS §14, §15, §16, §17, §30.1, §30.5 |
| Proposed owner | inventory-service (new, ROS-OQ-01) |
| Proposed phase | 13C |
| Affects | REQ-MENU-003 (product-level stock is replaced by ingredient inventory, IMPACT-0002) |

## Scope
Ingredient-level stock per location, an immutable movement ledger, automatic recipe consumption, costing, and stock-level alerts.

## Entities (ROS §14)
Inventory (stock item master), Warehouse (location), Stock (balance per item and location), StockBatch, StockMovement, StockAdjustment, StockTransfer, StockConsumption, Waste, Expiry.

## Movement types (ROS §14)
`PURCHASE`, `SALE`, `CONSUMPTION`, `TRANSFER_IN`, `TRANSFER_OUT`, `WASTE`, `ADJUSTMENT`, `RETURN`.

`SALE` applies to items sold as they are (for example a bottled drink). Prepared items post `CONSUMPTION` from their recipe.

## Requirements

| ID | Title | Priority | Depends on | Source |
|---|---|---|---|---|
| REQ-INV-001 | Stock items with units and conversions | P0 | REQ-OUTLET-001 | ROS §14 |
| REQ-INV-002 | Stock locations and balances | P0 | INV-001, REQ-OUTLET-001 | ROS §14, §19 |
| REQ-INV-003 | Immutable stock movement ledger | P0 | INV-002 | ROS §14, §30.5 |
| REQ-INV-004 | Automatic, idempotent consumption on order completion | P0 | INV-003, REQ-RECIPE-002, REQ-ORDER-008 | ROS §15 |
| REQ-INV-005 | Negative stock policy | P0 | INV-003 | ROS §30.1, ROS-OQ-10 |
| REQ-INV-006 | Batches and expiry | P1 | INV-003 | ROS §14, §17 |
| REQ-INV-007 | Stock counts and adjustments | P0 | INV-003, REQ-AUDIT-001 | ROS §14 |
| REQ-INV-008 | Waste recording | P1 | INV-003 | ROS §14, §24 |
| REQ-INV-009 | Transfers between locations | P1 | INV-003 | ROS §14, §20 |
| REQ-INV-010 | Configurable costing | P1 | INV-003 | ROS §16, ROS-OQ-11 |
| REQ-INV-011 | Stock levels and alerts | P0 | INV-002 | ROS §17 |

### REQ-INV-001 — Stock items
- AC1: A stock item (for example Paneer) has a brand-level definition: name, category, base unit (g, ml or pcs), purchase units with conversion factors (1 kg = 1000 g; 1 box = 12 pcs), and an optional barcode.
- AC2: Quantities are stored as decimals in the base unit. Floating-point types are never used.

### REQ-INV-002 — Locations and balances
- AC1: Every outlet has at least one store location. Warehouses and central kitchens are locations too (REQ-OUTLET-001).
- AC2: The balance per (item, location) always equals the sum of its ledger movements. A reconciliation check proves it and runs in CI tests and as a nightly job.

### REQ-INV-003 — Ledger (BR-R5)
- AC1: Every stock change is a movement: type, item, location, signed quantity, unit cost, reference (order, GRN, transfer, adjustment, waste), user or system, time and correlation ID.
- AC2: Movements are append-only. Updates and deletes are blocked at the service and database level. Mistakes are corrected with a reversing movement that references the original.
- AC3: Balances are updated in the same transaction as the movement.

### REQ-INV-004 — Automatic consumption
- AC1: On `OrderCompleted` (ROS-OQ-09), each line's recipe version (REQ-RECIPE-002 AC2), including add-ons and combo slots (REQ-RECIPE-005), is expanded into ingredient quantities × order quantity. Example: 2 Paneer Pizza → Paneer −200 g, Cheese −160 g, Sauce −100 ml.
- AC2: Consumption is **idempotent**. A unique key on (order ID, order line ID, recipe version) means a duplicate or replayed event never deducts twice (ROS §15). There is a test that replays the event.
- AC3: Lines without a recipe post nothing and are listed in a "products without recipes" report.
- AC4: `InventoryConsumed` is published with the order ID and the movements.
- AC5: Orders cancelled after preparation post waste instead (REQ-INV-008, ROS-OQ-09).

### REQ-INV-005 — Negative stock (BR-R1)
- AC1: Default: manual movements (transfer out, waste, adjustment, return) that would make a balance negative are rejected.
- AC2: Outlets can explicitly allow negative stock. The setting is audited.
- AC3: Consumption for a completed order always posts. Where negative stock is not allowed, the handling in ROS-OQ-10 applies (raise `STOCK_DISCREPANCY`, never reject or rewrite ledger entries). Final rule to be confirmed.

### REQ-INV-006 — Batches and expiry
- AC1: Receipts can create batches with a batch number and expiry date. FIFO costing and consumption use batches in expiry order.
- AC2: Batches expiring within the item's warning window raise `EXPIRING_SOON`. Expired stock is moved to waste by a user action, never automatically.

### REQ-INV-007 — Counts and adjustments
- AC1: A stock count records counted quantities. Differences post `ADJUSTMENT` movements with a reason.
- AC2: Adjustments above a value threshold need manager approval before posting.
- AC3: AI-proposed adjustments are only proposals until approved (REQ-AI-005).

### REQ-INV-008 — Waste
- AC1: Users record waste with item, quantity and reason (expired, spoiled, preparation error, cancelled after preparation). It posts `WASTE`.
- AC2: Waste value uses the costing method and feeds the waste report.

### REQ-INV-009 — Transfers
- AC1: A transfer posts `TRANSFER_OUT` at the source on dispatch and `TRANSFER_IN` at the destination on receipt. Goods in transit are visible.
- AC2: Received quantity differences are recorded with a reason.
- AC3: Central-kitchen requests and approvals are in REQ-OUTLET-005.

### REQ-INV-010 — Costing (ROS §16)
- AC1: A brand selects `WEIGHTED_AVERAGE` or `FIFO` (ROS-OQ-11). Changing the method takes effect from a period start, never retroactively.
- AC2: Each movement stores the unit cost used. Inventory valuation, food cost, recipe cost and gross margin reports use these stored costs.
- AC3: Costs are `BigDecimal` with documented rounding.

### REQ-INV-011 — Levels and alerts (ROS §17)
- AC1: Per item and location: minimum stock, reorder level, maximum stock, reorder quantity.
- AC2: Crossing below the reorder level publishes `LowStockDetected` (LOW_STOCK). Reaching zero publishes OUT_OF_STOCK. Batch expiry publishes EXPIRING_SOON. Each alert fires once per crossing, not on every movement.
- AC3: Alerts feed purchase suggestions (REQ-PURCHASE-001), inventory-linked menu availability (REQ-MENU-009) and notifications to the inventory manager.

## Events
`InventoryConsumed`, `StockMovementPosted`, `LowStockDetected`, `OutOfStockDetected`, `ExpiringSoonDetected`, `StockTransferred`, `StockDiscrepancyDetected` (inventory.events.v1).

## Test obligations
- Unit: unit conversion; FIFO and weighted average; recipe expansion.
- Concurrency: parallel consumptions and adjustments on the same item never lose updates (balance = ledger sum).
- Integration: replayed `OrderCompleted` deducts once; ledger rows can't be updated or deleted (database test).
- Security: one outlet can't read or move another brand's stock.
