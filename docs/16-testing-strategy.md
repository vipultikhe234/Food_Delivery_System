# 16 — Testing Strategy

| Field | Value |
|---|---|
| Version | 1.0.0 |
| Status | **Approved** 2026-10-01 |
| Requirements | REQ-QA-001..004, REQ-TESTAGENT-001..003, REQ-RMS-003/005, NFR-MAINT-001 |

MP rules: never skip tests; never claim tests passed without running them; never mark a requirement completed without evidence. Branch protection enforces the required checks (13 §5.2).

---

## 1. Test pyramid and tools

| Layer | Scope | Tools | Runs |
|---|---|---|---|
| Unit | Domain rules, state machines, pricing, scoring, mappers, validators | JUnit 5, AssertJ, Mockito; Vitest + React Testing Library (web) | Every PR |
| Architecture | Layer rules, security annotations present, no forbidden dependencies | ArchUnit | Every PR |
| Integration (service) | Repositories, Flyway migrations, Kafka producers and consumers, Redis, outbox relay, idempotency | Spring Boot Test + **Testcontainers** (PostgreSQL+PostGIS, Kafka, Redis, MinIO) | Every PR (changed services) |
| API / component | Controller → database with real HTTP, security, error format, IDOR | REST Assured + Testcontainers; WireMock for downstream services and providers | Every PR |
| Contract | Event schemas (producer and consumer), OpenAPI compatibility, provider adapters (Razorpay) | JSON Schema validator, openapi-diff, WireMock with recorded provider responses | Every PR |
| Cross-service integration | Saga flows across real services | Docker Compose environment in CI (`all` profile, mock payment), API tests | Nightly + release candidates |
| Web E2E | Customer, restaurant, delivery and admin journeys | Playwright (Chromium, Firefox, WebKit) | Smoke per PR (mocked API); full on staging |
| Mobile E2E | Customer and delivery apps on Android/iOS | Appium + WebdriverIO (emulators locally; BrowserStack or AWS Device Farm for release) | Release candidates |
| Visual | Screens vs. UI spec and reference | Playwright screenshots + pixelmatch/SSIM (REQ-TESTAGENT-003) | PRs touching UI (changed screens) |
| Accessibility | WCAG 2.1 AA checks (NFR-A11Y-001) | axe-core via Playwright | Web E2E runs |
| Performance | NFR verification | k6 (ADR-014) | Phase 21 + before releases touching hot paths |
| Security | SAST, SCA, DAST, IDOR, abuse cases, AI red team | 09 §10 | PR + nightly + Phase 22 |
| Resilience / chaos | Failure injection during load | k6 + kill pods/brokers (Chaos Mesh or scripts) | Phase 21, DR drills |

## 2. Requirement traceability (REQ-RMS-005)

- Every test is tagged with requirement and acceptance criterion:
  - Java: `@Tag("REQ-ORDER-001")` plus a custom `@Requirement(id = "REQ-ORDER-001", ac = {"AC1","AC3"})` annotation (in a `test-support` platform module).
  - Playwright: `test('places order @REQ-ORDER-001 @AC1', …)` or `test.info().annotations`.
  - k6: tags `{ req: 'NFR-PERF-001' }`.
- A **traceability job** parses JUnit XML and Playwright JSON, maps results to requirement and AC, and updates `docs/requirements/traceability.md` and `traceability.json` (later requirement-service). Each row shows: requirement, AC, tests, last run ID, result, commit.
- Gate: a requirement cannot move from `TESTING` to `UAT` (or later to `READY_FOR_RELEASE` and `COMPLETED`) unless every AC has at least one linked passing test from a real CI run (evidence gates in `requirements.json` `statusModel`). Before UAT, every requirement has at least one automated test (NFR-MAINT-001).

## 3. Coverage and quality gates (REQ-QA-001)

| Gate | Threshold | Notes |
|---|---|---|
| Line coverage, domain + application packages | ≥ 80% (NFR-MAINT-001, proposed) | JaCoCo per module; infrastructure and DTO packages excluded |
| Branch coverage, state machines and pricing | ≥ 90% | Critical logic |
| Web packages coverage | ≥ 70% lines for `packages/*` and domain hooks | UI components rely on E2E and visual tests |
| New code coverage | Not lower than the module baseline | Prevents erosion |
| Mutation testing (optional, nightly) | PIT on order, payment and promotion domain | Report only, no gate in v1 |
| Static analysis | No new critical issues (SpotBugs/Error Prone or Sonar if used) | — |

Coverage is a floor, not the goal. Each AC needs a meaningful assertion.

## 4. Test design by area (must-have cases)

| Area | Cases |
|---|---|
| Auth | Register/login/OTP; OTP expiry and attempt limit; lockout after 5 failures; refresh rotation; **refresh reuse revokes the family**; logout-all; password reset invalidates sessions; enumeration-safe responses |
| Authorisation | Generated role × permission matrix tests (09 §3.3); IDOR for every resource endpoint (customer A vs. B, manager outside scope, partner after delivery) |
| Cart / pricing | Variant and add-on rules (min/max); HALF_UP rounding table tests; delivery fee slabs; coupon types; quote expiry (clock injected); **tampered quote rejected** |
| Promotion | Concurrent redemption at the limit (50 threads compete for the last 1 → exactly 1 success); per-user limit; release on cancel or failure |
| Order | All transitions T1–T26 and every invalid pair (order-state-machine §6); concurrency (accept vs. cancel); idempotent create (same key replays; different body gives 422) |
| Saga | Payment failure; payment timeout; late payment after cancel causes refund; restaurant reject and timeout; no partner; refund failure; **crash mid-saga** (stop the consumer container, restart, assert end state) |
| Payment | Webhook signature valid/invalid; **duplicate webhook processed once**; out-of-order webhooks; amount mismatch; reconciliation fixes a stuck PENDING; refunds never exceed captured; wallet never negative under concurrent debits |
| Delivery | Scoring formulas; radius expansion; 30 s expiry; never re-offer; first accept wins under concurrency; assignment failure path |
| Location | Validation drops (accuracy, speed, skew); GEO update; stale cleanup; history only for active deliveries |
| Real-time | Auth at CONNECT; forbidden subscribe; reconnect + REST resync; cross-instance delivery via Redis |
| Events | Outbox atomicity (rollback means no event); relay retry on broker down; consumer idempotency (same event twice → one effect); DLT on poison message |
| Notifications | Dedupe; preference respect; fallback channel |
| AI | Criteria extraction set; clarification; grounding (no fabricated items); confirmation required for `addToCart`; red-team corpus; budget fallback |
| Search | Radius correctness (PostGIS); typo tolerance; filters and sorts; stale event ignored (`source_version`) |
| Migrations | Every service: Flyway from empty → latest on Testcontainers; expand/contract compatibility test (old app version against new schema) for migrations flagged as contract-sensitive |

## 5. Test data and environments

| Environment | Data |
|---|---|
| Unit / integration | Builders and fixtures per test (`OrderTestData.anOrder().withStatus(...)`), Testcontainers fresh per test class (reused container, schema reset), no shared mutable state |
| Compose (CI nightly) | Seed scripts with synthetic restaurants, menus, users, partners; **no real personal data ever** |
| Staging | Synthetic data generated at scale for performance (scripts in `tests/performance/data/`); Razorpay **test mode** only |
| Production | **No destructive testing** (MP rule). Only read-only synthetic monitoring (health and smoke checks with a dedicated test account in a hidden test restaurant, excluded from analytics) |

Time is controlled through an injected `Clock` everywhere (timeouts, expiries, scheduling), so tests never sleep for real deadlines. Randomness is seeded.

## 6. CI integration

```text
PR:      unit → architecture → integration/API (changed services + dependents, parallel) → contract
         → web lint/type/unit → Playwright smoke (mocked) → security scans → requirement validation
         → coverage gate → traceability (dry run)
main:    same + build/push images + deploy dev + API smoke on dev
nightly: full backend suites, Compose cross-service saga tests, full web E2E on dev, ZAP baseline, mutation (report)
RC:      staging deploy → full web E2E → mobile E2E (device farm) → DAST → optional k6 profile → evidence bundle
```

Flaky tests are quarantined within 24 h (tag `@Flaky`, excluded from gates, issue created). They must be fixed or deleted within 7 days. A quarantined test does not count as evidence.

## 7. Evidence format (REQ-TESTAGENT-002)
```json
{
  "runId": "github-actions/1234567890",
  "commit": "a1b2c3d",
  "environment": "ci-testcontainers | dev | staging",
  "startedAt": "…", "durationSeconds": 412,
  "totals": { "executed": 318, "passed": 318, "failed": 0, "skipped": 0 },
  "requirements": [ { "id": "REQ-ORDER-001", "ac": "AC1", "tests": ["OrderCreateIT.createsOrderFromValidQuote"], "result": "PASSED" } ],
  "artifacts": [ "junit.xml", "jacoco.xml", "playwright-report.zip" ]
}
```
Evidence is stored as CI artefacts and referenced from the requirement `statusHistory` entry. Results without a run ID are invalid.

## 8. Definition of Done (per requirement)
1. Code merged via PR with review.
2. All ACs covered by automated tests linked by tag, and passing in CI (run ID recorded).
3. Coverage gates met; no new critical static or security findings.
4. API docs, event schemas, migrations and `docs/` updated.
5. Observability added (metrics and logs for new flows; alerts if critical).
6. Requirement status updated with evidence along the status model: `DEVELOPMENT` → `CODE_REVIEW` → `TESTING` → `UAT`.
7. UAT sign-off by the approver for user-facing features (`UAT` → `READY_FOR_RELEASE`). `COMPLETED` is set only after release, with evidence.
