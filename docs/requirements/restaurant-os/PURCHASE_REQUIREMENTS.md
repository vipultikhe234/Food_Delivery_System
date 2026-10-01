# Purchase requirements

| Field | Value |
|---|---|
| Module | PURCHASE |
| Status | Approved 2026-10-01 (see [README](README.md)) |
| Sources | ROS §17, §18, §24, §26 |
| Proposed owner | procurement-service (new, ROS-OQ-01) |
| Proposed phase | 13D |

## Scope
From low stock to stock on the shelf (ROS §18):
Low stock → Purchase suggestion → Purchase order → Supplier → Goods receipt → Inventory.

## Entities (ROS §18)
Supplier (see [SUPPLIER_REQUIREMENTS.md](SUPPLIER_REQUIREMENTS.md)), PurchaseOrder, PurchaseOrderItem, GoodsReceipt, PurchaseInvoice, PurchaseReturn, SupplierPayment.

## Requirements

| ID | Title | Priority | Depends on | Source |
|---|---|---|---|---|
| REQ-PURCHASE-001 | Purchase suggestions from low stock | P1 | REQ-INV-011, REQ-SUPPLIER-002 | ROS §17, §18 |
| REQ-PURCHASE-002 | Purchase order lifecycle with approval | P1 | REQ-SUPPLIER-001 | ROS §18 |
| REQ-PURCHASE-003 | Goods receipt into inventory | P1 | PURCHASE-002, REQ-INV-003 | ROS §18 |
| REQ-PURCHASE-004 | Purchase invoices matched to PO and receipt | P1 | PURCHASE-003 | ROS §18 |
| REQ-PURCHASE-005 | Purchase returns | P1 | PURCHASE-003 | ROS §18 |
| REQ-PURCHASE-006 | Supplier payment records | P2 | PURCHASE-004 | ROS §18 |

### REQ-PURCHASE-001 — Suggestions
- AC1: For each `LowStockDetected` item, a suggestion proposes quantity = max(reorder quantity, maximum − current − on order) and the preferred supplier.
- AC2: Suggestions never create a purchase order by themselves. A user converts selected suggestions into a draft PO. AI suggestions follow REQ-AI-005.

### REQ-PURCHASE-002 — Purchase orders
- AC1: States: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `SENT`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CLOSED`, `CANCELLED`. Transitions are validated (design stage).
- AC2: POs above a configured value need approval by a user with `PURCHASE_APPROVE`. Approval is audited.
- AC3: An approved PO's lines and prices can't be edited. Changes need a cancellation and a new PO, or a revision with history.
- AC4: The PO can be shared with the supplier as a PDF or email (v1: download or email link).
- AC5: `PurchaseCreated` is published when a PO is approved.

### REQ-PURCHASE-003 — Goods receipt
- AC1: A goods receipt records received quantities per PO line, with batch and expiry where tracked (REQ-INV-006). Partial receipts are allowed.
- AC2: Posting a receipt creates `PURCHASE` movements at the receiving location at the PO unit cost (or invoice cost once matched, per REQ-INV-010), in one transaction.
- AC3: Posting is idempotent: re-submitting the same receipt doesn't add stock twice.
- AC4: `PurchaseReceived` is published.

### REQ-PURCHASE-004 — Invoices
- AC1: A supplier invoice (number, date, lines, taxes, total) is recorded against a PO and its receipts.
- AC2: Quantity or price differences between PO, receipt and invoice beyond a tolerance are flagged for approval before the invoice is marked payable (PROPOSED three-way match).

### REQ-PURCHASE-005 — Returns
- AC1: Returning received goods posts `RETURN` (stock out) with reason and reference to the receipt, and creates a debit note against the supplier.
- AC2: Returns can't exceed the received quantity still in stock.

### REQ-PURCHASE-006 — Supplier payments
- AC1: Payments to suppliers are **recorded** (amount, date, method, reference) against invoices. The platform doesn't move money to suppliers.
- AC2: Outstanding per supplier = invoices − payments − debit notes (REQ-SUPPLIER-003).

## Events
`PurchaseCreated`, `PurchaseReceived`, `PurchaseReturned`, `PurchaseInvoiceRecorded` (procurement.events.v1).

## Test obligations
- Unit: suggestion quantity; PO state transitions; tolerance matching.
- Integration: receipt posts stock once under retry; partial receipts move the PO to PARTIALLY_RECEIVED then RECEIVED.
- E2E: low stock → suggestion → PO → approval → receipt → stock increases → alert clears.
