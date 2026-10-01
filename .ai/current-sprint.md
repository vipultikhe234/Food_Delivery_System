# Current Sprint

```text
TASK ID:            TASK-ROS-002
REQUIREMENT ID:     Restaurant OS addendum (97 requirements, IMPACT-0002), NFR-PERF-005..007
CURRENT STATUS:     Design PROPOSED 2026-10-01, awaiting project owner approval (ROS §31 design stage)
WHAT WAS CHANGED:   docs/architecture/restaurant-os/: README (service boundaries, ports, gateway routes, Kafka topics,
                    event catalogue, tenancy, roles and permissions, devices and PINs, real-time channels, phase
                    mapping, decisions D-01..D-23), CATALOG_, ORDER_, POS_, KITCHEN_ and INVENTORY_ARCHITECTURE.md
                    (ERDs, state machines, API contracts, events, sequence diagrams, concurrency, test obligations);
                    ADR-018..021 (Proposed); pending-change notes in docs 05, 06, 08, 09 and order-state-machine;
                    docs/README and ADR index; KI-016 (tax adviser confirmation)
DATABASE CHANGES:   Designed only (catalog_db, order_db changes, pos_db, kitchen_db, inventory_db, procurement_db)
API CHANGES:        Designed only
TESTS EXECUTED:     mermaid.parse on all 31 diagrams in the ROS docs and order-state-machine: 0 failures
                    relative link and anchor check on all new and edited docs: OK
NOT EXECUTED:       No code exists for these designs yet
KNOWN ISSUES:       KI-006..009, KI-011, KI-012, KI-014..016
NEXT ACTION:        Project owner reviews the ROS design and decisions D-01..D-23. On approval: ADR-018..021 become
                    Accepted, docs 05/06/08/09 and order-state-machine are updated (v2.0.0), REQ-ORDER-002 v3 design
                    is linked, screen inventory gets POS/KDS/QR/HQ screens; then Phase 4 (infrastructure).
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
