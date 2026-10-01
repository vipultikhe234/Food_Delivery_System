# Project Context

**Project:** Food Delivery Platform (Zomato/Swiggy-like), production-grade, AI-assisted. Portfolio/learning context (OQ-01): designed for REF scale, with a modest verified load.
**Repository root:** this folder (monorepo; Git initialised in Phase 3).

## Current stage
- Step 1 (Requirement analysis) is **approved** (2026-10-01).
- Phase 2 (System design) is **approved** (2026-10-01), together with [IMPACT-0001](../docs/requirements/impact/IMPACT-0001.md) and OQ-21..23.
- No application code exists yet. **Next: Phase 3** (Git init, monorepo skeleton, parent POM, web/mobile workspaces, baseline CI). No business logic in Phase 3.

## Sources of truth
| Artefact | Location |
|---|---|
| Requirements (canonical) | `docs/requirements/requirements.json` |
| Requirements (readable) | `docs/requirements/requirements.md` |
| Impact reports | `docs/requirements/impact/` |
| Documentation index | `docs/README.md` |
| Architecture | `docs/04-system-architecture.md` … `docs/16-testing-strategy.md`, `docs/architecture/*` |
| Decisions | `docs/17-adr/` |
| UI | `docs/ui/design-system.md`, `docs/ui/screen-inventory.md` |
| Reference design notes | `docs/reference/system-design-notes/` (cited as `REF p.n`) |
| Project brief | The master prompt (cited as `MP §n`) |

## Context files (read the relevant ones before changing anything)
`architecture-context.md`, `requirements-context.md`, `coding-standards.md`, `testing-standards.md`, `security-rules.md`, `deployment-context.md`, `current-sprint.md`, `known-issues.md`.

## Rules every agent must follow (MP §53)
- Never invent requirements. Anything new is marked `PROPOSED` (or raised as an open question) and needs approval.
- Never silently change requirements. Never overwrite requirement history. Changes create a new version plus an impact report.
- Never delete existing functionality without approval. Never modify unrelated modules.
- Never claim tests passed, a requirement completed, or production readiness without stored evidence from a real run.
- Never skip tests. Never hardcode secrets or credentials. Never expose secrets.
- Never add unnecessary services or dependencies.
- Never deploy from a local machine. Never test destructively against production.
- Use the standard agent output format (MP §56) for every task report.
