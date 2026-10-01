# Known Issues and Open Items

| ID | Item | Impact | Owner / next step |
|---|---|---|---|
| KI-014 | Branch protection for `main`/`develop` and CODEOWNERS (REQ-DEVOPS-002 AC3) can't be configured without a remote | AC3 not yet met | Configure when the GitHub remote exists |
| KI-015 | gitleaks is not installed locally; the pre-commit hook skips the local secret scan with a warning | CI still scans every PR | Optional: install gitleaks locally |
| KI-011 | pnpm 12 auto-added `minimumReleaseAgeExclude` for turbo 2.11.6 in `pnpm-workspace.yaml` (release newer than the supply-chain age threshold) | Bypasses one supply-chain check for turbo only | Remove the exclusion once 2.11.6 passes the threshold |
| KI-012 | Provider credentials (Razorpay from the Salon Management project; Firebase/FCM and OAuth2 from ApnaCart) | Not needed before Phase 9 (payments) and Phase 11 (notifications). Values must go only into a local, gitignored `.env` or the secret manager, never into the repo. Live keys of another production app should not be reused: use test-mode Razorpay keys and a separate Firebase project / OAuth client | Decided 2026-10-01: deferred; the project owner supplies separate test-mode keys in a local `.env` in Phases 9 and 11 |
| KI-013 | ROS addendum approved (IMPACT-0002, 2026-10-01) but not yet recorded in `requirements.json` | Docs and `requirements.json` disagree until registration | Register right after Phase 3, then run the validator |
| KI-006 | Location history at full design scale needs an external time-series store | Not needed at the verified load | Documented in 06 §2.11 and 11 §5 |
| KI-007 | Background location on mobile depends on a community plugin and store policy review | REQ-MOBILE-002 risk | Spike in Phase 15 |
| KI-008 | NFR targets (performance, availability, RPO/RTO) are design targets, not measured | Must not be claimed until Phases 21/24 | — |
| KI-009 | Legal review pending for retention periods and DPDP alignment | Retention config may change | Before v1.0.0 |

## Resolved
| ID | Item | Resolution |
|---|---|---|
| KI-005 | Spring Boot / Spring Cloud versions not pinned | Pinned 2026-10-01: Boot 4.1.1, Cloud 2025.1.3 (`backend/pom.xml`); `mvnw verify` green |
| KI-010 | Spotless failed with google-java-format 1.37.0 | google-java-format 1.37.0 fails inside spotless-maven-plugin 3.10.3 (works standalone); pinned to 1.36.1, the plugin default. Revisit when Spotless updates |
| KI-001 | IMPACT-0001 awaiting approval | Approved 2026-10-01; REQ-ORDER-002 v2 and REQ-RT-003 v2 APPROVED |
| KI-002 | OQ-21 refund after DELIVERY_FAILED | Decided 2026-10-01: support decision only (T24) |
| KI-003 | OQ-22 verified load profile | Decided 2026-10-01: recommended profile |
| KI-004 | OQ-23 refund thresholds | Decided 2026-10-01: ₹500 / ₹5,000 / SUPER_ADMIN |
