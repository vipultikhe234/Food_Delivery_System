# Multi-outlet and central kitchen requirements

| Field | Value |
|---|---|
| Module | OUTLET |
| Status | DRAFT (see [README](README.md)) |
| Sources | ROS §19, §20, §30.11, §30.12 |
| Proposed owner | restaurant-service (brand, outlets, staff), inventory-service (central kitchen transfers) |
| Proposed phase | 5–6 for tenancy and roles; 13D for central kitchen |
| Affects | REQ-RESTAURANT-002, REQ-RESTAURANT-004, REQ-AUTH-003 (IMPACT-0002) |

## Scope
One brand runs many outlets, warehouses and central kitchens. HQ manages products, recipes, menus, pricing, suppliers and reports. Outlet managers run local operations (ROS §19).

## Entities (ROS §19, §20)
RestaurantBrand (existing restaurant), Outlet (existing branch), Warehouse, Kitchen, CentralKitchen, KitchenInventory, OutletRequest, StockTransfer, TransferApproval, TransferDispatch, TransferReceipt, PosDevice.

## Requirements

| ID | Title | Priority | Depends on | Source |
|---|---|---|---|---|
| REQ-OUTLET-001 | Brand hierarchy and location types | P0 | REQ-RESTAURANT-002 | ROS §19 |
| REQ-OUTLET-002 | HQ-managed masters and outlet-level operations | P0 | OUTLET-001, REQ-AUTH-003 | ROS §19 |
| REQ-OUTLET-003 | Tenant data isolation | P0 | OUTLET-001, REQ-AUTH-003 | ROS §30.11 |
| REQ-OUTLET-004 | Staff, roles and outlet assignment | P0 | OUTLET-001, REQ-RESTAURANT-004 | ROS §19, ROS-OQ-07 |
| REQ-OUTLET-005 | Central kitchen requests, approval, dispatch and receipt | P1 | REQ-INV-009 | ROS §20 |
| REQ-OUTLET-006 | Consolidated HQ reporting | P1 | REQ-ANALYTICS-002 | ROS §19, §24 |
| REQ-OUTLET-007 | POS and kitchen device registration | P1 | OUTLET-001 | ROS §19 |

### REQ-OUTLET-001 — Hierarchy
- AC1: A brand has outlets (the existing branches), warehouses and central kitchens. Each is a location with its own address and time zone.
- AC2: Only outlets take customer orders. Warehouses and central kitchens hold and produce stock.

### REQ-OUTLET-002 — HQ vs outlet
- AC1: Brand-scope permissions (HQ) cover products, recipes, menus, pricing, suppliers and reports for all outlets of the brand.
- AC2: Outlet-scope permissions cover POS, tables, kitchen, local stock, counts, waste, purchase requests and allowed menu overrides (REQ-MENU-008) for assigned outlets only.
- AC3: Every API call is authorised against the user's brand and outlet scope from the JWT. Client-supplied IDs are never trusted for scope.

### REQ-OUTLET-003 — Tenant isolation (BR-R11)
- AC1: Every tenant-owned table has a non-null brand ID. Every query is filtered by it through a shared persistence guard (`common-persistence`), not by hand in each query.
- AC2: Cross-tenant access returns 404 (no existence leak). Automated security tests try cross-brand reads and writes on every ROS endpoint.
- AC3: Events carry the brand ID. Consumers reject events whose brand doesn't match the referenced entity.
- AC4: Isolation technique (row-level with a guard and/or PostgreSQL row-level security) is decided in design via an ADR.

### REQ-OUTLET-004 — Staff and roles
- AC1: The owner or HQ invites staff, assigns roles (ROS-OQ-07: CASHIER, CAPTAIN, KITCHEN_STAFF, INVENTORY_MANAGER, plus existing RESTAURANT_MANAGER) and the outlets they work at.
- AC2: Shared POS devices use a quick staff PIN on top of the device credential, so each action is attributed to a person.
- AC3: Role and assignment changes are audited and take effect within the access-token lifetime.

### REQ-OUTLET-005 — Central kitchen (ROS §20)
- AC1: Flow: outlet stock request → central kitchen review → approval (full, partial or rejected, with reason) → dispatch → outlet receipt.
- AC2: Dispatch posts `TRANSFER_OUT` at the central kitchen. Receipt posts `TRANSFER_IN` at the outlet (REQ-INV-009). Differences are recorded.
- AC3: Every step records user and time. `StockTransferred` is published on receipt.
- AC4: Production of semi-finished items follows REQ-RECIPE-006.

### REQ-OUTLET-006 — HQ reporting
- AC1: HQ sees every report (REQ-ANALYTICS-002) per outlet and consolidated, with outlet comparison.

### REQ-OUTLET-007 — Devices
- AC1: POS terminals and kitchen displays are registered to an outlet with a revocable device credential. Revoking a device ends its sessions immediately.

## Test obligations
- Security: cross-brand and cross-outlet access on every endpoint; PIN brute-force lockout.
- Integration: central-kitchen flow end to end with stock movements on both sides.
- Unit: scope resolution from JWT claims.
