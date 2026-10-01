# ADR-020: Immutable stock ledger with balances, idempotent consumption and configurable costing
- Status: Proposed
- Date: 2026-10-01
- Deciders: Pending project owner approval (ROS-OQ-09, -10, -11 decided 2026-10-01)
- Related requirements: REQ-INV-002..006, REQ-INV-010, REQ-RECIPE-002, REQ-RECIPE-004, REQ-PURCHASE-003, BR-R1, BR-R5, NFR-PERF-007

## Context
Stock must be auditable (movements are immutable, BR-R5), always consistent (balance = sum of movements, REQ-INV-002 AC2), safe under concurrency (parallel orders on the same ingredient), never deducted twice (replayed events), and costed by weighted average or FIFO per brand. ROS-OQ-10 also requires that consumption for a completed order always posts, while BR-R1 says stock can't go negative unless allowed.

## Decision
1. **Ledger plus balance:** an append-only `stock_movements` table (monthly partitions) and a `stock_balances` row per (item, location) updated in the same transaction. `UPDATE`/`DELETE` on movements are revoked and blocked by a trigger. Corrections are reversing movements.
2. **One posting service** locks balance rows in (item, location) order, applies the negative-stock rule, posts movements, updates balances, evaluates alerts and writes outbox events.
3. **Negative stock:** manual outbound movements are rejected when they would go negative. System movements from completed orders and kitchen cancellations always post; if the result is negative where it isn't allowed, a system `ADJUSTMENT` (`AUTO_CLAMP`) brings the balance to zero in the same transaction and `StockDiscrepancyDetected` asks for a count. The ledger stays complete and balance = ledger sum holds.
4. **Idempotency by data:** consumption uses a unique key (order ID, order item ID, recipe version ID); goods receipts and returns use `external_postings`. These keys work even after `processed_events` rows are purged.
5. **Costing:** brand-level `WEIGHTED_AVERAGE` (moving average on receipt) or `FIFO` (batches, expiry first), effective from a period start, never retroactive. Each movement stores its unit cost and value; reports read stored values. Posted movements are never revalued; later invoice price differences are reported as purchase price variance.
6. **Recipes in the same service**, versioned and read-only once published; consumption uses the version effective at order placement.
7. **Reconciliation:** a nightly job per brand and every integration test assert balance = ledger sum.

## Consequences
### Positive
- Full audit trail; any balance can be rebuilt from the ledger.
- No double deduction under retries or replays; no lost updates under concurrency.
- Valuation and food cost come from stored costs, so reports are reproducible.

### Negative / risks
- Hot items at a busy outlet serialise on one balance row. At the design scale (portfolio, a few orders per second per outlet), one row lock per ingredient per order is acceptable; it will be measured in the Phase 13C concurrency tests.
- The clamp adjustment can hide real shortages in valuation until someone counts; the discrepancy alert and report make it visible.
- No retroactive revaluation means invoice price differences don't flow into past food cost.

## Alternatives considered
- **Balance-only (no ledger):** simple, but no audit trail and no way to prove correctness.
- **Event-sourced balances (compute on read):** pure, but slow reads for the POS availability checks and reports.
- **Reject consumption when stock is short:** violates ROS-OQ-10 (food is already served) and loses the record of what was used.
- **Clamp without a compensating movement:** breaks balance = ledger sum (REQ-INV-002 AC2).
