# Supplier requirements

| Field | Value |
|---|---|
| Module | SUPPLIER |
| Status | Approved 2026-10-01 (see [README](README.md)) |
| Sources | ROS §18, §19 |
| Proposed owner | procurement-service (new, ROS-OQ-01) |
| Proposed phase | 13D |

## Scope
The brand's supplier master and what each supplier supplies. HQ manages suppliers (ROS §19). Outlets order from the suppliers assigned to them.

## Requirements

| ID | Title | Priority | Depends on | Source |
|---|---|---|---|---|
| REQ-SUPPLIER-001 | Supplier master | P1 | REQ-OUTLET-001 | ROS §18, §19 |
| REQ-SUPPLIER-002 | Supplier item catalogue | P1 | SUPPLIER-001, REQ-INV-001 | ROS §18 |
| REQ-SUPPLIER-003 | Supplier ledger | P2 | SUPPLIER-001, REQ-PURCHASE-006 | ROS §18 |
| REQ-SUPPLIER-004 | Supplier status and audit | P1 | SUPPLIER-001, REQ-AUDIT-001 | ROS §30.12 |

### REQ-SUPPLIER-001 — Master
- AC1: A supplier has name, GSTIN (format validated), contact people, phone, email, address, payment terms and the outlets it serves.
- AC2: Suppliers belong to one brand. Other brands can't see them (BR-R11).
- AC3: Bank details, if stored, are encrypted at rest and masked in the UI and logs (MP security rules).

### REQ-SUPPLIER-002 — Item catalogue
- AC1: For each supplier, the stock items it supplies with purchase unit, last purchase price (updated from invoices) and an optional lead time in days (PROPOSED).
- AC2: One supplier per item and location can be marked preferred, which purchase suggestions use.

### REQ-SUPPLIER-003 — Ledger
- AC1: A supplier statement lists invoices, debit notes and payments, with a running outstanding balance.

### REQ-SUPPLIER-004 — Status and audit
- AC1: Suppliers can be ACTIVE or INACTIVE. Inactive suppliers can't receive new POs. Historical POs stay readable.
- AC2: Every create, update and status change is audited.

## Events
`SupplierCreated`, `SupplierUpdated`.

## Test obligations
- Unit: GSTIN validation; preferred-supplier uniqueness.
- Security: cross-brand access denied; bank details masked in API responses and logs.
