# Current Sprint

```text
TASK ID:            TASK-PHASE4B-001
REQUIREMENT ID:     REQ-PLAT-004 (v2), REQ-PLAT-005, REQ-PLAT-006, REQ-PLAT-007, REQ-PLAT-008 (release v0.1.0)
CURRENT STATUS:     DEVELOPMENT (recorded in requirements.json). Code complete on feature/phase-4b-platform-libraries;
                    Docker-backed tests not yet executed anywhere (local: no WSL; CI: needs a PR)
WHAT WAS CHANGED:   platform/test-support (@RequiresDocker: skipped locally without Docker, always on in CI; pinned
                    PostGIS/Kafka containers); common-web (error catalogue, RFC 9457 advice and /error controller, web
                    defaults, resilient RestClient factory); common-persistence (UUIDv7, BaseEntity, Money, JPA auditing,
                    per-feature platform migrations, IdempotencyService); common-events (envelope + JSON Schema,
                    OutboxPublisher/OutboxRelay, IdempotentEventProcessor, retry + DLT, topic helper, Kafka defaults);
                    ci-backend "Report test results" step (failing tests as annotations, totals as a notice);
                    .gitleaksignore (KI-024); config-server CSRF re-enabled (KI-027); docs 06 and 08 -> v1.1.1
COMMITS:            7d0ee88 common-web, 3e5d1c1 common-persistence, 50794c3 common-events, plus the verify commit
DATABASE CHANGES:   Platform migrations db/fdp/{idempotency,outbox,consumer}/V1 (idempotency_keys + response_headers,
                    outbox_events, processed_events), each with its own Flyway history table
API CHANGES:        None to endpoints. Error body is RFC 9457 (REQ-PLAT-004 v2); Idempotency-Key handling available
TESTS EXECUTED:     mvnw verify BUILD SUCCESS (2026-10-02): 123 run, 0 failures, 23 skipped (Docker). Per module:
                    common-observability 32, common-web 21, test-support 2, common-persistence 12 (+15 skipped),
                    common-events 13 (+8 skipped), config-server 4 (incl. CSRF 403), service-discovery 1, api-gateway 16.
                    Requirements validator OK incl. history check against origin/main. gitleaks 8.30.1 history scan clean.
                    CI evidence for Phase 4A: ci-backend verify green on acc18ce
NOT EXECUTED:       DatabaseIsolationTest (4), PersistenceIntegrationTest (11), EventsIntegrationTest (8): need Docker
                    (KI-021); they run in ci-backend once a PR exists. Compose stack still not run
DECISIONS NEEDED:   Branch flow (KI-025); approve docs 06/08 v1.1.1; dismiss the 2 gateway CodeQL alerts (KI-027)
KNOWN ISSUES:       KI-024..027 (new), KI-014, KI-015, KI-021 updated
NEXT ACTION:        Open the 4B PR, read ci-backend annotations, fix anything the Docker tests find; then Phase 4
                    exit check (Compose stack runs + CI green) once Docker works
```

```text
TASK ID:            TASK-PHASE4A-001
REQUIREMENT ID:     REQ-PLAT-001, REQ-PLAT-002, REQ-PLAT-003, REQ-OBS-001, REQ-DEVOPS-001 (release v0.1.0 Foundation)
CURRENT STATUS:     DEVELOPMENT (recorded in requirements.json). Merged into main by the owner via PR #1 on
                    2026-10-01 (merge b73f65f); ci-backend green on acc18ce.
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
DECISIONS:          2026-10-01 checkpoint: error format RFC 9457 (REQ-PLAT-004 v2, IMPACT-0003, KI-019 resolved);
                    docs 05 v1.1.1 and 13 v1.1.0 approved; branch pushed with a PR into develop
KNOWN ISSUES:       KI-018, KI-020..023 (new), KI-006..009, KI-011, KI-012, KI-014..017
NEXT ACTION:        Phase 4B (REQ-PLAT-004..008); Compose run as soon as Docker works (KI-021)
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
