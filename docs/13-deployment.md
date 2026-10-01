# 13 — Deployment

| Field | Value |
|---|---|
| Version | 1.0.0 |
| Status | **Approved** 2026-10-01 |
| Depends on | [ADR-011](17-adr/ADR-011-discovery-and-config.md), [ADR-016](17-adr/ADR-016-aws.md) |
| Requirements | REQ-DEVOPS-001..005, NFR-DEPLOY-001, NFR-SEC-002 |

MP rule: **never deploy directly from a local machine.** Every deployment to development, staging or production goes through the CI/CD pipeline.

---

## 1. Environments

| Env | Purpose | Runtime | Data | Deploy trigger | Cost policy (ADR-016) |
|---|---|---|---|---|---|
| local | Developer machine | Docker Compose profiles | Containers (PostgreSQL+PostGIS, Redis, Kafka KRaft, MinIO, Mailpit, ClamAV) | `docker compose up` | — |
| dev | Integration of merged work | EKS namespace `dev` (small node group) or a single VM with Compose | Shared small RDS (one instance, many databases), one Redis, single-broker Kafka | Automatic on merge to `main` | Scale to zero outside working hours |
| staging | Production-like verification: E2E, performance, DAST, DR drills, UAT | EKS `staging` (Terraform, **on demand**) | RDS Multi-AZ, ElastiCache, MSK (3 brokers) | Automatic after dev succeeds, for release candidates | Created for verification windows, destroyed afterwards |
| production | Live | EKS multi-AZ (3 AZs) | RDS Multi-AZ per critical service group, ElastiCache replication groups, MSK 3 brokers, S3 + CloudFront | Manual approval (GitHub Environment protection) | Minimal footprint, HPA |

## 2. Local development (REQ-DEVOPS-001, Phase 4)

`infrastructure/docker/docker-compose.yml` with profiles:

| Profile | Services |
|---|---|
| `infra` | postgres (PostGIS, init script creates one database and user per service from env), redis, kafka (KRaft) + kafka-ui, minio, mailpit, clamav |
| `platform` | config-server, service-discovery, api-gateway |
| `core` | identity, user, audit, restaurant, menu, media |
| `commerce` | cart, promotion, order, payment |
| `fulfilment` | delivery, location, notification, realtime |
| `insights` | review, search, analytics, admin, recommendation, ai |
| `observability` | otel-collector, prometheus, grafana, loki, tempo |
| `all` | everything |

- `.env.example` lists every variable with placeholder values. `.env` is git-ignored.
- Developers usually run `infra` + `platform` in Compose and the services they are working on from the IDE.
- Seed data scripts live in `infrastructure/docker/seed/` (demo restaurants, menus, partners); they are not migrations.
- Memory budget: `infra` about 3 GB; `all` needs about 12–16 GB (documented, with JVM flags `-XX:MaxRAMPercentage=60` and small heaps for local).

## 3. Container images

- Multi-stage build: Maven build stage (with cached `~/.m2`), Spring Boot **layered jar** extraction, runtime on `eclipse-temurin:21-jre` (or distroless java21).
- Runs as non-root UID 10001, read-only root filesystem (writable `/tmp` emptyDir), `HEALTHCHECK` for Compose, `JAVA_TOOL_OPTIONS` with container-aware memory and the OTel agent.
- **Tags:** `<service>:<git-sha>` (immutable) plus `<service>:<semver>` on release. `latest` is never used in Kubernetes.
- Images are scanned with Trivy, an SBOM is produced (CycloneDX), and images are signed with cosign (keyless OIDC). Registry: ECR with scan-on-push and immutable tags.
- Frontend apps: Vite build to static assets, uploaded to S3 + CloudFront (per app, per environment). Environment config is injected at deploy time via `/config.json`, not rebuilt per environment.

## 4. Kubernetes design (REQ-DEVOPS-004, Phase 23)

One shared Helm chart `infrastructure/kubernetes/charts/fd-service` with per-service values files (`values/<service>.yaml`, `values/<env>/<service>.yaml`).

| Resource | Design |
|---|---|
| Deployment | Rolling update `maxSurge 25%`, `maxUnavailable 0`; `terminationGracePeriodSeconds 45`; preStop sleep 5 s; topology spread across AZs; pod anti-affinity |
| Probes | startup (120 s), readiness, liveness (12 §7) |
| Resources | Requests and limits per service (CPU limit omitted or generous to avoid throttling; memory limit = request) |
| HPA | CPU 70% default; custom metrics via Prometheus Adapter (RPS for gateway and location, connections for realtime); KEDA optional for Kafka-lag scaling of consumers |
| PDB | `minAvailable: 1` (or 50% for services with 4+ replicas) |
| Service | ClusterIP for all; only the gateway and realtime are exposed via Ingress (AWS Load Balancer Controller, ALB; WebSocket idle timeout raised to 3600 s for realtime) |
| ConfigMap | Non-secret environment such as `SPRING_PROFILES_ACTIVE` and the config-server URL |
| ExternalSecret | DB, Redis and Kafka credentials, keys (09 §5) |
| ServiceAccount | One per service, IRSA role for its secrets and S3 prefixes |
| NetworkPolicy | Default deny; allow from the gateway/ingress and declared callers; egress to its database, Redis, Kafka and allowed external hosts |
| Migrations | Helm pre-upgrade hook Job running Flyway with the owner role; must succeed before rollout |
| Namespaces | `platform` (gateway, discovery, config, realtime), `services`, `observability`, `ops` |

Minimum replicas in production: 2 for every user-facing service (3 for gateway, order, payment and realtime), 1–2 for back-office consumers.

## 5. CI/CD (REQ-DEVOPS-002 Phase 3 baseline; REQ-DEVOPS-003 Phase 23 full)

### 5.1 Git strategy
- **Trunk-based** on `main`, with short-lived branches `feat/REQ-<MODULE>-<NNN>-<slug>`, `fix/…` and `chore/…`.
- Conventional Commits that reference requirement IDs (`feat(order): REQ-ORDER-001 create order from quote`).
- `main` is protected: PR required, at least 1 approval (CODEOWNERS for platform, security and infrastructure), all checks green, linear history (squash merge), signed commits recommended.
- Releases are tagged `vX.Y.Z` per the roadmap. Release notes are generated from commits plus requirement status (REQ-RMS-006).

### 5.2 Pipelines (GitHub Actions)
| Workflow | Trigger | Jobs |
|---|---|---|
| `ci-backend.yml` | PR / push touching `backend/**` | Detect changed modules, then build + unit tests + integration tests (Testcontainers) for changed modules and their dependents, JaCoCo coverage gate, Spotless check, ArchUnit, OpenAPI diff, event schema compatibility |
| `ci-web.yml` | `web/**`, `mobile/**` | pnpm install (cache), lint, type-check, unit tests (Vitest), build; Playwright smoke against mocked API |
| `ci-requirements.yml` | `docs/requirements/**` | Validate `requirements.json` against its schema, plus uniqueness, dependencies, transitions, history immutability vs. base branch, evidence gates (ADR-012) |
| `ci-security.yml` | Every PR + nightly | gitleaks, CodeQL, dependency scan, Trivy (filesystem/config), Checkov (Terraform) |
| `ci-commits.yml` | Every PR | PR title and commit subjects follow Conventional Commits (REQ-DEVOPS-002 AC4) |
| `cd.yml` | Merge to `main` | Build and push images (changed services), sign, deploy to **dev** (Helm), smoke tests |
| `cd-staging.yml` | Release candidate tag `vX.Y.Z-rc.N` | Deploy staging, full E2E (web Playwright, mobile Appium on emulators/BrowserStack), ZAP scan, optional k6 profile; publish reports |
| `cd-production.yml` | Release tag `vX.Y.Z` + manual approval | Verify signatures, run migrations job, rolling deploy, smoke tests; **automatic rollback** (`helm rollback`) on failed smoke tests or SLO burn within 15 min |
| `mobile-release.yml` | Mobile tag | Capacitor build, Fastlane signing (keys from secrets), upload to internal testing tracks |

**Phase 3 baseline (implemented):** `ci-backend` runs `mvnw verify` (build, unit tests, Spotless, JaCoCo report, enforcer). `ci-web` runs turbo lint/test/build. `ci-requirements` runs the validator tests and the validator, with history checks against the PR base or the previous commit. `ci-security` runs gitleaks, CodeQL (Java and JavaScript/TypeScript, no build) and a Trivy filesystem scan. `ci-commits` checks Conventional Commits. Change-detection per module, Testcontainers, ArchUnit, OpenAPI diff, event-schema checks, the coverage gate and Checkov are added when the code they check exists (Phases 4+). Dependabot (`.github/dependabot.yml`) updates Maven, npm and GitHub Actions weekly against `develop`.

**Required PR checks:** build, unit, integration (changed services), coverage gate, lint, security scans and requirement validation. "Never skip tests" is enforced by branch protection, not by convention.

### 5.3 Zero-downtime database changes (NFR-DEPLOY-001)
1. **Expand** (release N): add a column or table (nullable or with a default) and deploy code that writes both old and new.
2. **Migrate:** backfill job (batched, throttled).
3. **Switch reads** (release N+1).
4. **Contract** (release N+2): drop the old column.

Rules: never rename in place; never add a NOT NULL without a default in one step; create indexes `CONCURRENTLY` (Flyway `executeInTransaction=false` for those scripts).

## 6. Infrastructure as code (REQ-DEVOPS-005, Phase 24)
- Terraform in `infrastructure/terraform/`:
  - `modules/`: vpc, eks, rds, elasticache, msk, s3-cdn, ecr, iam-oidc, secrets, waf
  - `envs/{dev,staging,prod}`
- Remote state in S3 + DynamoDB lock, encrypted.
- Plans run in CI on PR (comment with plan), apply via a workflow with environment approval. Drift detection runs weekly.
- Staging can be fully created and destroyed via the `staging-up` and `staging-down` workflows (cost policy).

## 7. Configuration flow
```text
config-repo (Git, non-secret)  ──►  config-server  ──►  service (profile: <env>)
AWS Secrets Manager ──► External Secrets ──► K8s Secret ──► env vars ──► ${PLACEHOLDER} in config
```
Precedence: environment variables override config-server values, which override `application.yml` defaults. Feature flags live in config-server (e.g. `features.ai-assistant.enabled`) and are refreshable via `/actuator/refresh` (Spring Cloud Bus is not used in v1; services restart rolling or poll every 60 s).

## 8. Release checklist (summary; full template in Phase 23)
Requirements for the release are `READY_FOR_RELEASE` with evidence; the changelog is generated; migrations are reviewed for expand/contract; dashboards and alerts are updated; runbooks exist for new alerts; the rollback plan is confirmed; security scan is clean; the performance report is attached (for releases touching hot paths); approvers have signed off (REQ-RMS-006).
