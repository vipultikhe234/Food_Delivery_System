# ADR-012: Git-versioned requirements until requirement-service
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-RMS-001..006, REQ-DEVAGENT-001

## Context
Requirement management (versioning, history, status, traceability) is Phase 1, but DevAgent and the admin dashboard only need an API from Phase 18.

## Decision
- `docs/requirements/requirements.json` in Git is the source of truth through Phase 17.
- Changes happen through pull requests.
- CI (from Phase 3) validates the JSON Schema, ID uniqueness, dependency integrity, transition rules and history immutability (by comparing against the target branch).
- In Phase 18, `requirement-service` imports the JSON, becomes the system of record and exports back to Git for review history.

## Consequences
### Positive
- Immutable history, review and blame come free with Git. No service is needed before it is useful.

### Negative / risks
- Concurrent edits to one large JSON file cause merge conflicts. Mitigated by keeping one requirement per object and small PRs. Split per module if conflicts grow.
- Evidence gates are enforced by CI scripts, not by an application, until Phase 18.

## Alternatives considered
- **requirement-service from Phase 1:** builds an application before the platform foundations exist.
- **External tool (Jira):** good UX, but outside the repository and agent-tooling scope.
