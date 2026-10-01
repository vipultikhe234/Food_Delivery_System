# Known Issues and Open Items

| ID | Item | Impact | Owner / next step |
|---|---|---|---|
| KI-021 | Docker engine not running on the development machine (CLI present; WSL 2 / Docker Desktop being installed by the owner). Not executed: image build, `docker compose up` for any profile, Testcontainers, Loki search by traceId (REQ-OBS-001 AC3), config change without rebuild (REQ-PLAT-003 AC3) | REQ-DEVOPS-001, REQ-OBS-001 AC3 and the Phase 4 exit criterion "Compose stack runs" are unverified. Only `docker compose config` was run | Run the Compose stack (infra + platform + observability) and the image build as soon as Docker works, then record the evidence |
| KI-020 | Gateway rate limiting and the `revoked_sessions` deny-list are not in Phase 4A; they need Redis-backed identity state and are planned with identity-service in Phase 5 | REQ-PLAT-001 description mentions rate limiting (no AC yet covers it); revoked access tokens stay valid until expiry | Implement with identity-service in Phase 5 |
| KI-023 | Graceful deregistration on SIGTERM (REQ-PLAT-002 AC1) not verified: Windows cannot send SIGTERM to a JVM process. Crash removal was measured (15.2 s registry side, ~21 s calculated worst case with client caches) | AC1 deregistration half unverified; client-side removal time calculated, not measured | Verify with `docker compose stop api-gateway` once Docker works (KI-021) |
| KI-018 | MinIO: `minio/minio` is no longer published on Docker Hub; local Compose uses the frozen `bitnamilegacy/minio:2025.7.23-debian-12-r5` image (owner decision 2026-10-01, local only) | Frozen image receives no security updates; acceptable for local development only | Revisit before Phase 6 (media-service): choose a maintained S3-compatible image or build MinIO from source |
| KI-022 | Spring Cloud Gateway's own properties classes trigger Hibernate Validator warning HV000271 at startup | Log noise only; our properties no longer trigger it | Re-check after the next Spring Cloud update |
| KI-016 | Bill computation order (discounts before tax, taxable packaging and service charge, round-off last; ROS design D-15) is based on the usual GST treatment but not confirmed by a tax adviser | In-store totals and invoices could be wrong for some tax situations | Project owner to confirm with a tax adviser before Phase 13A UAT; a change is one pricing-library change (ADR-021) |
| KI-014 | Branch protection for `main`/`develop` and CODEOWNERS (REQ-DEVOPS-002 AC3) are not configured yet | AC3 not yet met | The GitHub remote now exists (`vipultikhe234/Food_Delivery_System`); the owner enables branch protection in the repository settings |
| KI-017 | Event type name `DeviceRegistered` is used both on `user.events.v1` (customer push device) and on `restaurant.events.v1` (POS/kitchen device, ROS design) | Same event type on two topics can confuse schema subjects and consumers | Decide a distinct name for one of them when the event contracts are written (Phase 5/6), via a small design note |
| KI-015 | gitleaks is not installed locally; the pre-commit hook skips the local secret scan with a warning | CI still scans every PR | Optional: install gitleaks locally |
| KI-011 | pnpm 12 auto-added `minimumReleaseAgeExclude` for turbo 2.11.6 in `pnpm-workspace.yaml` (release newer than the supply-chain age threshold) | Bypasses one supply-chain check for turbo only | Remove the exclusion once 2.11.6 passes the threshold |
| KI-012 | Provider credentials (Razorpay from the Salon Management project; Firebase/FCM and OAuth2 from ApnaCart) | Not needed before Phase 9 (payments) and Phase 11 (notifications). Values must go only into a local, gitignored `.env` or the secret manager, never into the repo. Live keys of another production app should not be reused: use test-mode Razorpay keys and a separate Firebase project / OAuth client | Decided 2026-10-01: deferred; the project owner supplies separate test-mode keys in a local `.env` in Phases 9 and 11 |
| KI-006 | Location history at full design scale needs an external time-series store | Not needed at the verified load | Documented in 06 §2.11 and 11 §5 |
| KI-007 | Background location on mobile depends on a community plugin and store policy review | REQ-MOBILE-002 risk | Spike in Phase 15 |
| KI-008 | NFR targets (performance, availability, RPO/RTO) are design targets, not measured | Must not be claimed until Phases 21/24 | — |
| KI-009 | Legal review pending for retention periods and DPDP alignment | Retention config may change | Before v1.0.0 |

## Resolved
| ID | Item | Resolution |
|---|---|---|
| KI-019 | Error body conflict between REQ-PLAT-004 v1 AC1 and doc 07 §3 | Decided 2026-10-01: RFC 9457 per doc 07; REQ-PLAT-004 v2 via [IMPACT-0003](../docs/requirements/impact/IMPACT-0003.md) |
| KI-005 | Spring Boot / Spring Cloud versions not pinned | Pinned 2026-10-01: Boot 4.1.1, Cloud 2025.1.3 (`backend/pom.xml`); `mvnw verify` green |
| KI-010 | Spotless failed with google-java-format 1.37.0 | google-java-format 1.37.0 fails inside spotless-maven-plugin 3.10.3 (works standalone); pinned to 1.36.1, the plugin default. Revisit when Spotless updates |
| KI-013 | ROS addendum not yet in `requirements.json` | Registered 2026-10-01 (207 requirements; validator OK with history check). REQ-WALLET-002 kept, not cancelled (ROS-OQ-16 correction) |
| KI-001 | IMPACT-0001 awaiting approval | Approved 2026-10-01; REQ-ORDER-002 v2 and REQ-RT-003 v2 APPROVED |
| KI-002 | OQ-21 refund after DELIVERY_FAILED | Decided 2026-10-01: support decision only (T24) |
| KI-003 | OQ-22 verified load profile | Decided 2026-10-01: recommended profile |
| KI-004 | OQ-23 refund thresholds | Decided 2026-10-01: ₹500 / ₹5,000 / SUPER_ADMIN |
