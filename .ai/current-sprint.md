# Current Sprint

```text
TASK ID:            TASK-ROS-002
REQUIREMENT ID:     Restaurant OS addendum (97 requirements, IMPACT-0002), NFR-PERF-005..007
CURRENT STATUS:     Design APPROVED 2026-10-01 by the project owner (decisions D-01..D-23 as proposed; D-23 keeps a
                    READY KOT of a cancelled order READY with a cancellation alert, no requirement change)
WHAT WAS CHANGED:   docs/architecture/restaurant-os/: README (service boundaries, ports, gateway routes, Kafka topics,
                    event catalogue, tenancy, roles and permissions, devices and PINs, real-time channels, phase
                    mapping, decisions D-01..D-23), CATALOG_, ORDER_, POS_, KITCHEN_ and INVENTORY_ARCHITECTURE.md
                    (ERDs, state machines, API contracts, events, sequence diagrams, concurrency, test obligations);
                    ADR-018..021 (Accepted); docs 05, 06, 08, 09 -> v1.1.0; order-state-machine -> v2.0.0
                    (SERVED, HANDED_OVER, COMPLETED, T27-T34); screen inventory v1.1.0 (POS, KDS, QR, HQ screens);
                    docs/README and ADR index; KI-016 (tax adviser confirmation)
DATABASE CHANGES:   Designed only (catalog_db, order_db changes, pos_db, kitchen_db, inventory_db, procurement_db)
API CHANGES:        Designed only
TESTS EXECUTED:     mermaid.parse on every diagram in the edited docs; relative link and anchor check (see commit)
NOT EXECUTED:       No code exists for these designs yet
KNOWN ISSUES:       KI-006..009, KI-011, KI-012, KI-014..017
NEXT ACTION:        Phase 4 (infrastructure, MP §60), after the project owner confirms the start.
```

```text
TASK ID:            TASK-ROS-001
REQUIREMENT ID:     Restaurant OS addendum (97 requirements); IMPACT-0002
CURRENT STATUS:     Done. Registered in requirements.json 2026-10-01 (207 FR, 25 NFR; 200 APPROVED, 4 DEVELOPMENT,
                    3 ANALYSIS). Validator OK with the history check against the previous commit.
COMMITS:            1b1d1a1 docs(requirements): register restaurant OS requirements and IMPACT-0002 versions
```

```text
TASK ID:            TASK-PHASE3-001
REQUIREMENT ID:     REQ-DEVOPS-002, REQ-RMS-001, REQ-RMS-002, REQ-RMS-003
CURRENT STATUS:     DEVELOPMENT (recorded in requirements.json); Phase 3 done, awaiting review
COMMITS:            9296536 build: phase 3 repository skeleton and restaurant OS requirements
TESTS EXECUTED:     validator tests 20/20; mvnw verify BUILD SUCCESS (8 modules, no tests yet); hooks verified
NOT EXECUTED:       GitHub Actions (no remote); gitleaks (not installed locally)
```
