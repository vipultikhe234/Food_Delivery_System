# QR ordering requirements

| Field | Value |
|---|---|
| Module | QR |
| Status | Approved 2026-10-01 (see [README](README.md)) |
| Sources | ROS §21, §3, §7, §8 |
| Proposed owner | pos-service (QR codes and sessions); order-service (orders); catalog-service (QR menu) |
| Proposed phase | 13E |

## Scope
Guests scan a code on their table, browse the outlet's QR menu, and order to that table.

Flow (ROS §21): Scan → Menu → Cart → Order → OTP / verification → KOT → Kitchen.

## Entities (ROS §21)
TableQR, QRSession, QROrder (an order with `orderSource = QR`).

## Requirements

| ID | Title | Priority | Depends on | Source |
|---|---|---|---|---|
| REQ-QR-001 | Table QR codes | P1 | REQ-POS-006 | ROS §21 |
| REQ-QR-002 | QR sessions linked to the table session | P1 | QR-001, REQ-POS-007 | ROS §21 |
| REQ-QR-003 | QR menu and cart | P1 | QR-002, REQ-MENU-006 | ROS §21 |
| REQ-QR-004 | OTP verification before ordering | P1 | QR-003, REQ-AUTH-002 | ROS §21 |
| REQ-QR-005 | QR order to kitchen | P1 | QR-004, REQ-KOT-002 | ROS §21, ROS-OQ-12 |
| REQ-QR-006 | QR order payment | P1 | QR-005, REQ-BILL-003 | ROS-OQ-12 |

### REQ-QR-001 — Codes
- AC1: Each table has a QR code with a signed, opaque token bound to (brand, outlet, table). It doesn't contain guessable sequential IDs.
- AC2: Managers can regenerate a table's code, which invalidates the old one.
- AC3: Codes can be printed from the POS admin screen.

### REQ-QR-002 — Sessions
- AC1: Scanning a valid code joins the table's open session, or opens one if the outlet allows guests to open sessions (otherwise "please wait for staff").
- AC2: Several guests on the same table share one session and one running bill.
- AC3: A QR session can't order after the table session is closed, transferred or merged. Transferred and merged sessions follow the table to its new state.
- AC4: QR orders are always attached to the correct table (ROS §21). Tokens for another outlet or a revoked code are rejected.

### REQ-QR-003 — Menu and cart
- AC1: The guest sees the outlet's `QR` channel menu with current availability, allergens and prices (REQ-MENU-006).
- AC2: The cart is per guest device. Prices are re-checked server-side on submit.
- AC3: Responsive web, no app install, usable on low-end phones (NFR-COMPAT-001).

### REQ-QR-004 — Verification
- AC1: Before the first order of a session, the guest verifies a phone number by OTP (reusing identity-service OTP, rate-limited).
- AC2: Verification lasts for the table session. Further orders don't need a new OTP.
- AC3: Submissions are rate-limited per session and device to prevent prank orders.

### REQ-QR-005 — To the kitchen
- AC1: With "QR auto-accept" enabled, a verified QR order is accepted and KOTs are generated immediately.
- AC2: Otherwise (default, ROS-OQ-12) the order appears on the POS and captain devices for acceptance, and the guest sees "waiting for confirmation".
- AC3: The guest sees the status of their items (received, preparing, served) in real time.

### REQ-QR-006 — Payment
- AC1: Default: QR orders are added to the table bill and paid at settlement (REQ-BILL-003).
- AC2: If the outlet enables online payment, the guest can pay the session bill through payment-service, confirmed only by webhook (never by the browser).

## Events
`QrSessionStarted`, `QrOrderSubmitted`; orders publish the existing order events with `orderSource = QR`.

## Test obligations
- Security: forged, revoked and cross-outlet tokens are rejected; OTP rate limits; session takeover is not possible after the table closes.
- E2E: scan → OTP → order → KOT → KDS → served, for both auto-accept and staff-accept.
- Visual: QR menu and cart on a small phone viewport.
