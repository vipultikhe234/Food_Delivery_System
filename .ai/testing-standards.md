# Testing Standards (summary of docs/16-testing-strategy.md)

- Every test is tagged with a requirement and AC: Java `@Requirement(id="REQ-…", ac={"AC1"})` + `@Tag`; Playwright `@REQ-… @AC1`; k6 tag `req`.
- **Layers:** unit (JUnit 5/Mockito, Vitest), ArchUnit, integration with **Testcontainers** (PostgreSQL+PostGIS, Kafka, Redis, MinIO), API (REST Assured), contract (JSON Schema events, openapi-diff, WireMock providers), web E2E (Playwright), mobile E2E (Appium/WebdriverIO), visual (pixelmatch/SSIM), a11y (axe), performance (k6), security (09 §10).
- **Gates:** ≥ 80% line coverage on domain and application packages (proposed); ≥ 90% branch coverage on state machines and pricing; no coverage regression.
- **Determinism:** injected `Clock`, seeded randomness, no real sleeps for deadlines, fresh data per test.
- **Must-have cases** per area: docs/16 §4 (idempotency, concurrency, saga failures, duplicate webhook, IDOR, role matrix).
- **Evidence:** CI run ID + commit + counts + artefacts (16 §7). Results without a run ID are invalid. Never report tests that were not executed.
- **Flaky tests:** quarantine within 24 h, then fix or delete within 7 days. Quarantined tests do not count as evidence.
- **Production:** read-only synthetic checks only. No destructive testing.
