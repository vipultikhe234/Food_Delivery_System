# Food Delivery Platform

A requirement-driven food delivery platform with a restaurant operating system: customer ordering, restaurant POS and kitchen, inventory and purchasing, delivery, payments, analytics and AI assistants.

Built phase by phase. Each phase starts only after the previous one is approved. Current state: [docs/README.md](docs/README.md).

## Repository layout

| Path | Contents | Phase |
|---|---|---|
| `docs/` | Requirements, architecture, ADRs, UI design | 1–2 |
| `docs/requirements/requirements.json` | Source of truth for requirements, statuses and history ([ADR-012](docs/17-adr/README.md)) | 1 |
| `backend/` | Java 21 / Spring Boot microservices (Maven multi-module) | 3+ |
| `backend/platform/` | Shared libraries: web, security, events, persistence, observability, event contracts | 3–4 |
| `web/` | React web apps (customer, restaurant, POS, KDS, delivery, admin) | 14 |
| `mobile/` | React + Capacitor apps (customer, delivery partner) | 15 |
| `ai-agents/` | DevAgent and TestAgent | 18–19 |
| `tools/requirements-validator/` | CI validator for `requirements.json` | 3 |
| `infrastructure/` | Docker Compose, Kubernetes, Terraform | 4, 23–24 |
| `tests/` | Cross-service E2E, performance and security suites | 14+ |
| `.ai/` | Context files for AI agents | — |

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 (Temurin) | Set `JAVA_HOME`. The Maven wrapper downloads Maven 3.9.16 with a pinned checksum. |
| Node.js | ≥ 24 | |
| pnpm | 12.8.1 | Via corepack: `corepack pnpm …` (version pinned in `package.json`) |
| Docker | Current | From Phase 4 |

## Common commands

```bash
# Backend: build, tests, format check, coverage report
cd backend && ./mvnw verify          # Windows: .\mvnw.cmd verify
cd backend && ./mvnw spotless:apply  # fix formatting

# Workspace
corepack pnpm install
corepack pnpm validate:requirements
corepack pnpm --filter @fdp/requirements-validator test

# Git hooks (once per clone): secret scan, requirement validation, Conventional Commits
git config core.hooksPath .githooks
```

## Configuration and secrets
Copy `.env.example` to `.env` for local development. `.env` is gitignored. No credentials are committed. Shared and production secrets come from the secret manager ([docs/09-security.md](docs/09-security.md)).

## Branches and commits
`main` and `develop` are long-lived. Work happens on `feature/*`, `bugfix/*`, `release/*` and `hotfix/*`. Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) and are checked by the `commit-msg` hook and CI.
