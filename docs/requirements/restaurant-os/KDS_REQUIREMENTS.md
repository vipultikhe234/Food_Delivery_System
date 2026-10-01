# KDS requirements

| Field | Value |
|---|---|
| Module | KDS |
| Status | Approved 2026-10-01 (see [README](README.md)) |
| Sources | ROS §11, §12 |
| Proposed owner | kitchen-service (state), realtime-service (WebSocket push, REQ-RT-001) |
| Proposed phase | 13B (backend). The KDS web app is built in Phase 14. |
| Affects | REQ-RT-002 (new kitchen topics), REQ-ORDER-003 (ready can come from the KDS) |

## Scope
A real-time kitchen display per station that replaces paper tickets where the outlet uses screens.

## KDS columns (ROS §11)
`NEW`, `ACCEPTED`, `PREPARING`, `READY`, `SERVED`, `CANCELLED`.

## Requirements

| ID | Title | Priority | Depends on | Source |
|---|---|---|---|---|
| REQ-KDS-001 | Station board with KOT columns | P0 | REQ-KOT-002 | ROS §11 |
| REQ-KDS-002 | Real-time updates without refresh | P0 | KDS-001, REQ-RT-001 | ROS §11 |
| REQ-KDS-003 | Validated KOT transitions and order status roll-up | P0 | KDS-001, REQ-ORDER-008 | ROS §11, §30.8 |
| REQ-KDS-004 | Timers and priority highlighting | P1 | KDS-001 | ROS §10, §11 |
| REQ-KDS-005 | Kitchen display devices | P1 | KDS-001, REQ-OUTLET-007 | ROS §12 |
| REQ-KDS-006 | Modifications and cancellations flagged | P0 | KDS-001, REQ-KOT-003, REQ-KOT-004 | ROS §10, §11 |

### REQ-KDS-001 — Board
- AC1: Each display shows KOTs for its station(s) in the six columns, oldest first within a column, with RUSH items on top.
- AC2: Each card shows KOT number, order type, table or customer name, items with modifiers and notes, and elapsed time.
- AC3: An expeditor view can show all stations of an outlet.

### REQ-KDS-002 — Real time
- AC1: New, modified and cancelled KOTs appear on the board without a page refresh, within the NFR target (ROS-OQ-19).
- AC2: Updates go over WebSocket through realtime-service. Subscriptions are authorised per outlet and station (REQ-RT-001).
- AC3: After a reconnect the board resynchronises from the server and shows no stale or duplicate cards (sequence or version check).
- AC4: A visible indicator shows when the connection is lost.

### REQ-KDS-003 — Transitions and roll-up
- AC1: Allowed transitions: NEW → ACCEPTED → PREPARING → READY → SERVED; NEW, ACCEPTED or PREPARING → CANCELLED (only through REQ-KOT-004). Everything else is rejected.
- AC2: Transitions can be made per KOT or per item. A KOT is READY when all its items are READY.
- AC3: When every KOT of an order (current round) is READY, order-service moves the order to READY (`OrderReady`). For delivery orders this is the existing T13.
- AC4: Concurrent bumps from two screens are serialised. The second gets the current state, not an error page.
- AC5: Each transition publishes `KOTUpdated` and is stored in KOT history (REQ-KOT-007).

### REQ-KDS-004 — Timers
- AC1: A card shows elapsed time against the item's preparation time and changes colour when it is late (thresholds configurable per outlet).
- AC2: Average preparation time per station and item is available in reports.

### REQ-KDS-005 — Devices
- AC1: A kitchen display is registered to an outlet and one or more stations with a device credential that a manager can revoke.
- AC2: A display can't see other outlets' KOTs (BR-R11).

### REQ-KDS-006 — Changes flagged
- AC1: A modified KOT is highlighted with the change ("−1", "+1", note changed) until it is acknowledged.
- AC2: A cancelled KOT shows a prominent alert with an optional sound until it is acknowledged.

## Test obligations
- Unit: transition table; roll-up to order READY.
- Integration: WebSocket push and reconnect resynchronisation.
- Concurrency: two screens bumping the same KOT.
- E2E: KOT from the POS appears on the right station without refresh; bump to READY updates the POS and the customer's order status.
- Visual: KDS board in each state.
