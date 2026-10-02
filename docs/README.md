# Documentation Index — Food Delivery Platform

| Stage | Status (2026-10-01) |
|---|---|
| Step 1: Requirement analysis | **Approved** (project owner instruction "continue") |
| Phase 2: System design | **Approved** 2026-10-01, together with [IMPACT-0001](requirements/impact/IMPACT-0001.md) and OQ-21..23 |
| Phase 3: Repository structure | **Done** 2026-10-01, awaiting review (monorepo skeleton, backend parent POM and platform modules, Maven wrapper, pnpm workspace, requirements validator, CI workflows, git hooks) |
| Restaurant OS addendum: requirements | **Approved** 2026-10-01 and registered in `requirements.json` ([restaurant-os/](requirements/restaurant-os/README.md), [IMPACT-0002](requirements/impact/IMPACT-0002.md)) |
| Restaurant OS addendum: design | **Approved** 2026-10-01 ([architecture/restaurant-os/](architecture/restaurant-os/README.md), ADR-018..021, decisions D-01..D-23 as proposed) |
| Phase 4A: Infrastructure (Compose, config, discovery, gateway, logging) | **Merged** into `main` 2026-10-01 (PR #1, ci-backend green); Compose stack not yet run (Docker unavailable, KI-021); docs 05 v1.1.1 and 13 v1.1.0 approved; error format decided ([IMPACT-0003](requirements/impact/IMPACT-0003.md)) |
| Phase 4B: Platform libraries (errors, persistence, events/outbox, idempotency, resilience) | **In review** 2026-10-02: `mvnw verify` green (123 tests, 23 Docker tests run in CI only); docs 06 and 08 v1.1.1 approved; PR into `develop` |

## Requirements (sources of truth)
| # | Document | Content |
|---|---|---|
| 01 | [requirements/requirements.md](requirements/requirements.md) §1–4 | Vision, scope, assumptions, business requirements, actors |
| 02 | [requirements/requirements.json](requirements/requirements.json) | **Canonical** machine-readable requirements (207 functional, 25 non-functional, status model, roadmap, open questions) |
| 03 | [requirements/requirements.md](requirements/requirements.md) §5–13 | Journeys, catalogue, dependency graph, phase-order notes, roadmap, open questions |
| — | [requirements/impact/](requirements/impact/) | Change impact reports (IMPACT-0001: order status / early partner assignment, approved; IMPACT-0002: Restaurant OS addendum, approved; IMPACT-0003: RFC 9457 error format, approved) |
| — | [requirements/restaurant-os/](requirements/restaurant-os/README.md) | Restaurant OS addendum: 12 requirement documents (POS, product, menu, KOT, KDS, inventory, recipe, purchase, supplier, multi-outlet, QR, billing), 97 requirements, decided open questions ROS-OQ-01..22 |
| — | [requirements/requirements.schema.json](requirements/requirements.schema.json) | JSON Schema for `requirements.json`, enforced in CI by `tools/requirements-validator` |

## System design (Phase 2)
| # | Document | Content |
|---|---|---|
| 04 | [04-system-architecture.md](04-system-architecture.md) | Approved architecture: drivers, decomposition, communication, data, security, real-time, deployment, risks, decisions |
| 05 | [05-microservices.md](05-microservices.md) | Per-service responsibility, data, APIs, events, dependencies, scaling; ports; gateway routes |
| 06 | [06-database-design.md](06-database-design.md) | Conventions, common tables, per-service schemas, retention, capacity |
| 07 | [07-api-design.md](07-api-design.md) | REST conventions, error format and codes, pagination, idempotency, rate limits, sample contracts, WebSocket |
| 08 | [08-event-driven-architecture.md](08-event-driven-architecture.md) | Envelope, topics, event catalogue, commands, consumers, retry/DLT, outbox relay, schema evolution |
| 09 | [09-security.md](09-security.md) | Threat model, authentication, permission matrix, ownership, data protection, secrets, OWASP mapping, payment and AI security |
| 10 | [10-caching.md](10-caching.md) | Redis deployments, cache and state key catalogue, invalidation, failure behaviour |
| 11 | [11-scalability.md](11-scalability.md) | Capacity model, scaling per component, verified load profile (proposed), load scenarios |
| 12 | [12-observability.md](12-observability.md) | Logging, metrics, tracing, SLOs, dashboards, alerts |
| 13 | [13-deployment.md](13-deployment.md) | Environments, Compose, images, Kubernetes, CI/CD, IaC, zero-downtime migrations |
| 14 | [14-disaster-recovery.md](14-disaster-recovery.md) | RPO/RTO, failure responses, backups, regional DR, drills, resilience patterns |
| 15 | [15-ai-architecture.md](15-ai-architecture.md) | Food assistant, tools, guardrails, recommendations, DevAgent/TestAgent |
| 16 | [16-testing-strategy.md](16-testing-strategy.md) | Test layers, traceability, gates, must-have cases, evidence, Definition of Done |
| 17 | [17-adr/](17-adr/README.md) | Architecture Decision Records ADR-001…ADR-021 |

## Detailed designs
| Document | Content |
|---|---|
| [architecture/order-state-machine.md](architecture/order-state-machine.md) | States, transitions T1–T26, edge cases, status labels, tests |
| [architecture/delivery-assignment-algorithm.md](architecture/delivery-assignment-algorithm.md) | Trigger, candidates, filters, scoring, offers, concurrency, availability states |
| [architecture/restaurant-os/](architecture/restaurant-os/README.md) | Restaurant OS design: service boundaries, ports, routes, topics, event catalogue, tenancy, roles; catalogue, order, POS, kitchen, inventory and procurement architectures with ERDs, state machines, API contracts and sequence diagrams |
| [ui/design-system.md](ui/design-system.md) | Tokens (with measured contrast), typography, components, accessibility, platform variants |
| [ui/screen-inventory.md](ui/screen-inventory.md) | Screens per app with IDs, routes, backing requirements, navigation |

## Planned (later phases)
`api/` (OpenAPI per service, generated), `database/` (ER diagrams from migrations), `runbooks/` (Phases 20/23), `releases/` (release notes, evidence, DR drills), `ui/specs/` (per-screen UI specs).

## Reference
[reference/system-design-notes/](reference/system-design-notes/) holds the 16 reference note images, cited as "REF p.N" by printed page label.
