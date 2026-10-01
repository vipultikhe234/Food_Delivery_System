# ADR-017: TypeScript for DevAgent and TestAgent
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-DEVAGENT-001..003, REQ-TESTAGENT-001..003

## Context
The agents orchestrate Git and GitHub operations, run Maven/Playwright/Appium suites, parse reports, compare screenshots and call an LLM. They are development-time tools, not runtime services.

## Decision
- Implement `ai-agents/*` in **TypeScript on Node.js (LTS)**.
- Libraries: Playwright (screenshots and web E2E), WebdriverIO (Appium client), pixelmatch/SSIM (visual diff), Octokit or `gh` CLI (GitHub), Ajv (JSON Schema validation of agent reports and requirements), and a provider-agnostic LLM SDK.
- Agents have **no production credentials**. They act only through branches, pull requests, CI and requirement-service APIs (REQ-DEVAGENT-002).

## Consequences
### Positive
- Same language as the web/mobile stack and the E2E tooling. Strong JSON/schema ecosystem.

### Negative / risks
- A second backend language in the repository, limited to tooling.
- Agents call backend APIs over HTTP rather than sharing Java code.

## Alternatives considered
- **Java with Spring AI:** consistent with the backend, but weaker integration with Playwright, WebdriverIO and image tooling.
- **Python:** a strong AI ecosystem, but a third language.
