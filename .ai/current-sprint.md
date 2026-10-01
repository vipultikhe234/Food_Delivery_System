# Current Sprint

```text
TASK ID:            TASK-PHASE4A-001
REQUIREMENT ID:     REQ-PLAT-001, REQ-PLAT-002, REQ-PLAT-003, REQ-OBS-001, REQ-DEVOPS-001 (release v0.1.0 Foundation)
CURRENT STATUS:     DEVELOPMENT (recorded in requirements.json); Phase 4A done locally, awaiting review.
                    Phase 4B (REQ-PLAT-004..008) not started.
WHAT WAS CHANGED:   backend/platform/common-observability (JSON logging, PII masking, correlation ID, tracing
                    defaults); backend/services/config-server (native backend, HTTP Basic), service-discovery
                    (Eureka, fast eviction), api-gateway (routes, JWT via JWKS, CORS, security headers, correlation,
                    access log, error handler); infrastructure/config-repo; backend/Dockerfile;
                    infrastructure/docker (Compose profiles infra/media/platform/observability, Postgres init,
                    observability configs); .env.example; README; docs 05 -> v1.1.1, 13 -> v1.1.0
DATABASE CHANGES:   Local Postgres init script only (one database plus owner/app roles per service)
API CHANGES:        Gateway route table (docs/05 §1.2, infrastructure/config-repo/api-gateway.yml)
TESTS EXECUTED:     mvnw verify BUILD SUCCESS: common-observability 32, config-server 3, service-discovery 1,
                    api-gateway 16 (incl. load balancing across 2 instances); requirements validator OK;
                    docker compose config for all profiles; manual local run of the three services as jars
                    (probes, auth, 401/503 bodies, headers, Eureka registration, crash removal 15.2 s)
NOT EXECUTED:       Image build, compose up, Testcontainers, Loki traceId search (KI-021); graceful
                    deregistration (KI-023); GitHub Actions for this change (not pushed)
KNOWN ISSUES:       KI-018..023 (new), KI-006..009, KI-011, KI-012, KI-014..017
NEXT ACTION:        Owner decides KI-019 (error format) and approves docs 05 v1.1.1 / 13 v1.1.0; then Phase 4B
```

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
