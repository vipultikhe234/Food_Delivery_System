# ADR-013: Java 21 LTS, Maven multi-module, Spring Boot release train
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-DEVOPS-001, REQ-DEVOPS-002, REQ-QA-001

## Context
The backend is Java / Spring Boot / Spring Cloud. All services should share dependency versions and build conventions.

## Decision
- **Java 21 LTS** as the baseline (virtual threads available). Moving to Java 25 LTS is evaluated once the full dependency set supports it, via a new ADR.
- **Maven** multi-module build: `backend/pom.xml` is the parent with `dependencyManagement` importing the Spring Boot and Spring Cloud BOMs. `backend/platform/*` holds shared libraries. Each service is a module.
- Spring Boot and Spring Cloud use the **latest mutually compatible stable release train**. Exact versions are pinned in Phase 3 after a compatibility check, and recorded in the parent POM and `.ai/architecture-context.md`.
- Maven Wrapper is committed. Enforcer and Spotless (formatting) plugins, plus JaCoCo for coverage.

## Consequences
### Positive
- One place to upgrade versions. Reproducible builds. Wide ecosystem familiarity.

### Negative / risks
- Maven builds of many modules are slower than Gradle's incremental builds. Mitigated by CI path filters and `-pl/-am` partial builds.

## Alternatives considered
- **Gradle (Kotlin DSL):** faster incremental builds, but more variance in build-script style.
- **Java 25 now:** newest LTS, but higher risk of dependency incompatibility at project start.
