# Billing requirements

| Field | Value |
|---|---|
| Module | BILL |
| Status | Approved 2026-10-01 (see [README](README.md)) |
| Sources | ROS §6, §22, §23, §30.3, §30.6 |
| Proposed owner | pos-service billing module (bills, splits, invoices); payment-service (gateway payments, refunds, webhooks) |
| Proposed phase | 13A |
| Affects | REQ-CART-002 (shared pricing rules, IMPACT-0002). REQ-WALLET-002 (online wallet + gateway split) stays separate (ROS-OQ-16 correction). |

## Scope
Computing, issuing, splitting, merging and settling bills for in-store orders, and keeping issued bills immutable. Online delivery orders keep their existing quote → payment flow; their tax invoice uses the same invoice rules (REQ-BILL-002).

## Bill components (ROS §22)
Subtotal, Tax, Discount, Coupon, Packaging, Service charge, Delivery fee, Round-off, Grand total.

## Payment methods (ROS §22)
`CASH`, `UPI`, `CARD`, `WALLET`, `ONLINE`, `SPLIT` (= more than one of the others).

## Requirements

| ID | Title | Priority | Depends on | Source |
|---|---|---|---|---|
| REQ-BILL-001 | Server-side bill computation | P0 | REQ-PRODUCT-005, REQ-PRODUCT-007 | ROS §22 |
| REQ-BILL-002 | Tax invoice issuance and numbering | P0 | BILL-001 | ROS §22, ROS-OQ-14 |
| REQ-BILL-003 | Settlement with multiple payment methods | P0 | BILL-002, REQ-PAYMENT-006 | ROS §22, §23 |
| REQ-BILL-004 | Split bill | P0 | BILL-001, BILL-003 | ROS §23 |
| REQ-BILL-005 | Merge bills | P1 | BILL-001, REQ-POS-007 | ROS §6 |
| REQ-BILL-006 | Issued bills are immutable | P0 | BILL-002, REQ-AUDIT-001 | ROS §30.3 |
| REQ-BILL-007 | Refunds and credit notes | P0 | BILL-006, REQ-PAYMENT-005 | ROS §6, §30.6 |
| REQ-BILL-008 | Digital and printed receipts | P1 | BILL-002, REQ-NOTIF-001 | ROS §6 |
| REQ-BILL-009 | Service charge compliance | P0 | BILL-001 | ROS §22, ROS-OQ-13 |

### REQ-BILL-001 — Computation
- AC1: Grand total = subtotal − discounts − coupon + packaging + service charge + delivery fee + tax ± round-off. The exact order of applying discounts before or after tax is fixed in design and documented, per tax rules.
- AC2: Tax is calculated per line from the line's tax class (inclusive or exclusive), with a per-rate tax summary on the bill.
- AC3: Round-off to the nearest rupee is configurable per outlet and shown as its own line.
- AC4: All amounts are `BigDecimal` with HALF_UP. The calculation shares one pricing library with cart-service, so online and in-store totals follow the same rules.
- AC5: Client-sent totals are never trusted. The server recalculates on every change.

### REQ-BILL-002 — Invoice
- AC1: Finalising a bill issues an invoice number, sequential and gap-free per outlet per financial year (ROS-OQ-14). Concurrent finalisations never get duplicate or skipped numbers (concurrency test).
- AC2: The invoice shows the outlet's legal name, address, GSTIN, invoice number and date, lines, tax summary and totals.

### REQ-BILL-003 — Settlement
- AC1: A bill is settled when the sum of successful payments equals the grand total. Partial payment leaves it PARTIALLY_PAID.
- AC2: Cash: amount tendered and change are recorded. Card terminal: last 4 digits or reference. UPI: gateway dynamic QR confirmed by webhook, or staff-attested static UPI (ROS-OQ-08). Wallet and online: payment-service, confirmed by webhook only.
- AC3: Payment status is never taken from the client (MP rule). Staff-attested payments record the cashier and device and are audited.
- AC4: `PaymentCompleted` per payment and `BillSettled` per bill are published. Settlement completes the order (REQ-ORDER-008).

### REQ-BILL-004 — Split bill (ROS §23)
- AC1: Split by item: lines (or quantities of a line) are assigned to sub-bills.
- AC2: Split by amount: the total is divided into amounts or equal shares. Rounding differences go to the last share.
- AC3: Split by customer: each guest's items go to their sub-bill (QR orders already carry the guest).
- AC4: Each sub-bill can be paid with a different method or several methods.
- AC5: The sum of sub-bills always equals the original total, tax included. A split can be undone until any sub-bill has a payment.

### REQ-BILL-005 — Merge
- AC1: Bills of merged table sessions (REQ-POS-007) combine into one bill before any payment. The merge is recorded in history.

### REQ-BILL-006 — Immutable bills (BR-R3)
- AC1: After an invoice number is issued, the bill's lines, prices, taxes and totals can't be changed by any API. A database guard backs up the service check.
- AC2: Corrections are made with a credit note (full or partial) that references the original invoice.
- AC3: Every view, reprint and correction of a finalised bill is audited.

### REQ-BILL-007 — Refunds and credit notes
- AC1: A refund creates a credit note and a refund request. Online methods are refunded through payment-service (REQ-PAYMENT-005). Cash refunds are recorded as staff-attested.
- AC2: Refunds above the role limit need approval (REQ-POS-012).
- AC3: Gateway refund webhooks are processed idempotently (BR-R6, existing REQ-PAYMENT-002).
- AC4: `RefundCreated` is published (existing event).

### REQ-BILL-008 — Receipts
- AC1: A receipt can be printed (80 mm) or sent as an SMS or email link to a signed, expiring receipt URL.

### REQ-BILL-009 — Service charge (ROS-OQ-13)
- AC1: Service charge is disabled by default and is configured per outlet with a percentage.
- AC2: When enabled it is shown as a separate, clearly labelled line, and staff can remove it from a bill at the guest's request. The removal is recorded.
- AC3: Service charge is never included in item prices.

## Events
`BillCreated`, `BillSplit`, `BillsMerged`, `BillFinalised`, `BillSettled`, `CreditNoteIssued`; `PaymentCompleted` and `RefundCreated` (existing).

## Test obligations
- Unit: every total component; inclusive and exclusive tax; round-off; split by amount rounding; sum of splits = total.
- Concurrency: invoice numbering under parallel finalisation; two cashiers settling the same bill.
- Integration: webhook replay doesn't double-settle; database guard rejects updates to finalised bills.
- E2E: split by item with cash + UPI; refund with credit note.
