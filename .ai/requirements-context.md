# Requirements Context

- **Canonical file:** `docs/requirements/requirements.json` (110 functional, 22 non-functional, 23 open questions). Readable view: `docs/requirements/requirements.md`.
- **ID format:** `REQ-<MODULE>-<NNN>`, `NFR-<AREA>-<NNN>`, `OQ-<NN>`, `BR-<NN>`. IDs are never reused.
- **Statuses:** DRAFT, ANALYSIS, APPROVED, DESIGN, DEVELOPMENT, CODE_REVIEW, TESTING, BUG_FOUND, REWORK, UAT, READY_FOR_RELEASE, RELEASED, COMPLETED, BLOCKED, CANCELLED. Allowed transitions and evidence gates are in `statusModel`.
- **Current state (2026-10-01):** 107 APPROVED (REQ-ORDER-002 and REQ-RT-003 at v2 via IMPACT-0001). The P2 backlog (REQ-PROMO-004, REQ-WALLET-002, REQ-REC-002) is in ANALYSIS.
- **Priorities:** P0 = required for v1.0.0; P1 = mandated, not core path; P2 = backlog.
- **Phase-order notes** (`phaseOrderNotes`): temporary stubs, e.g. fake SMS port before Phase 11 and payment contract stub in Phase 8.

## Change rules
1. Any content change creates version N+1, re-enters ANALYSIS, needs an impact report in `docs/requirements/impact/IMPACT-NNNN.md`, and needs approval before implementation.
2. `changeHistory` and `statusHistory` are append-only. Previous values are preserved in `previousValues`.
3. A status beyond TESTING requires evidence: a CI run ID, commit, and test counts.
4. Open questions do not become requirements until decided by the project owner.

## Open questions
All 23 are decided. Phase 2 added: OQ-21 (refund after DELIVERY_FAILED is a support decision), OQ-22 (verified load profile: 300 RPS / 10 orders/s / 2,000 location updates/s / 5,000 WebSockets), OQ-23 (refund thresholds: support ₹500, admin ₹5,000, then SUPER_ADMIN).
