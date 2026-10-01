# Tests

Cross-service suites. Unit and integration tests live next to the code they test.

| Path | Contents | Phase |
|---|---|---|
| `e2e/` | Playwright (web) and Appium (mobile) end-to-end tests | 14–15 |
| `performance/` | k6 load tests against the verified load profile | 21 |
| `security/` | OWASP ZAP and tenant-isolation suites | 22 |

Empty until Phase 14. Strategy: [docs/16-testing-strategy.md](../docs/16-testing-strategy.md).
