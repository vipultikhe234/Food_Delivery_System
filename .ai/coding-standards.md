# Coding Standards

## Java / Spring
- Java 21. Use records for DTOs and value objects. No Lombok on JPA entities (explicit constructors, equals and hashCode on ID). Lombok, if adopted in Phase 3, is limited to `@Slf4j`-style helpers.
- **Layers:** `api` → `application` → `domain`; `infrastructure` implements ports. Controllers never use repositories. Domain code has no Spring web or Kafka imports. Enforced by ArchUnit.
- Constructor injection only. No field injection.
- **Transactions:** `@Transactional` on application services, not controllers or repositories. Events go to the outbox in the same transaction.
- **Money:** `BigDecimal` + currency, scale 2, `RoundingMode.HALF_UP`, via a `Money` value object. Never use `double`.
- **Time:** inject `java.time.Clock`. Never call `Instant.now()` directly in domain or application code. Store UTC.
- **IDs:** UUIDv7 generated in the application.
- **Errors:** throw domain exceptions mapped to Problem Details with a stable `code` (07 §3). Never return stack traces.
- **Validation:** Bean Validation on request DTOs. Reject unknown JSON properties on writes.
- **Security:** every controller method has `@PreAuthorize` (or is listed as public). Ownership checks happen in the application layer.
- **Logging:** SLF4J with parameterised messages. No PII or secrets. Business milestones at INFO.
- **Config:** `@ConfigurationProperties` classes with validation. Secrets only via `${ENV_VAR}`.
- **Formatting:** Spotless (google-java-format or palantir, chosen in Phase 3). Build fails on violations.
- **Naming:** `*Controller`, `*Service` (application), `*Repository`, `*Client` (HTTP), `*Listener` (Kafka), `*Publisher`, `*Mapper`. Events in past tense (`OrderConfirmed`). Commands are imperative (`RegisterCodPayment`) or end in `Requested` (`RefundRequested`, `AssignDeliveryRequested`), as catalogued in docs/08 §3.2.

## TypeScript / React
- Strict TypeScript (`strict: true`, `noUncheckedIndexedAccess`). No `any` without a justification comment.
- Server state via TanStack Query hooks from `packages/api-client` (generated). No ad-hoc fetch calls in components.
- Client state via Zustand (small stores). Forms via React Hook Form + Zod schemas.
- Styling only via design tokens (Tailwind preset). No raw hex colours (lint rule).
- Components: function components, accessible by default (see design-system §5). Tests use React Testing Library.
- Money formatted via `packages/domain` helpers (`en-IN`, INR).

## General
- Small PRs referencing requirement IDs. Conventional Commits.
- No new dependency without a reason in the PR description (MP §53).
- No commented-out code. Comments only for constraints the code cannot express.
