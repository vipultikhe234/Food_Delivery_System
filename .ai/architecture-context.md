# Architecture Context (summary — details in docs/04–16)

## Shape
- 3 infrastructure services: `api-gateway` (8080), `service-discovery` (8761), `config-server` (8888).
- 18 domain services: identity, user, audit, restaurant, menu (includes products), media, cart, promotion, order (saga orchestrator), payment (includes the wallet module), delivery, location, notification, review, search, analytics, admin (complaints + aggregation), realtime (WebSocket).
- Phase 17 services: recommendation, ai. Phase 18: requirement-service.
- Restaurant OS (ADR-018, approved 2026-10-01): menu-service is renamed **catalog-service** (8084); new pos-service (8102), kitchen-service (8103), inventory-service (8104), procurement-service (8105). 25 deployables in total. Web apps `web/apps/pos` and `web/apps/kds`. Tenant column `restaurant_id` with `@TenantId` + PostgreSQL RLS (ADR-019). Design: `docs/architecture/restaurant-os/`.
- Ports, routes and per-service cards: `docs/05-microservices.md`.

## Non-negotiable patterns
| Pattern | Rule | Doc |
|---|---|---|
| Database per service | No cross-service DB access or joins. Read models are built from events. | 06, ADR-003 |
| Transactional outbox | Every event is written in the same transaction as the state change (except `location.updates.v1`) | 08, ADR-005 |
| Idempotent consumers | `processed_events` per consumer; at-least-once delivery | 08 §4 |
| Idempotency-Key | Required on order, payment and refund POSTs | 07 §5 |
| Orchestrated saga | order-service drives the order flow via commands and events with deadlines in `saga_state` | ADR-007, order-state-machine.md |
| State machine | All order transitions go through the table T1–T26; anything else returns 409 | architecture/order-state-machine.md |
| Server-side pricing | Signed 10-min quotes; client prices ignored; payment amount comes from the order | 05 §3.7, 09 §7 |
| Never trust frontend payment status | Confirmation only via verified webhook or reconciliation | 09 §7 |
| Sync calls | RestClient + Resilience4j (timeouts, circuit breaker, bulkhead); no cycles | 05 §4, 14 §6 |
| Caching | Only per 10-caching.md. Never cache order, payment or wallet state. Evict after commit. | 10 |
| AI | The LLM never accesses databases. Tools call the gateway with the user's JWT. Confirmation is required for state changes. | 15, 09 §8 |

## Stack
- **Backend:** Java 21, Spring Boot / Spring Cloud (versions pinned in Phase 3), Maven multi-module, PostgreSQL + PostGIS, Flyway, Kafka (KRaft), Redis (separate cache and state), Spring Cloud Gateway, Eureka, Config Server, Resilience4j, Micrometer + OpenTelemetry.
- **Frontend:** React + TypeScript + Vite, TanStack Query, Zustand, React Hook Form + Zod, Tailwind; pnpm + Turborepo; Ionic React + Capacitor for mobile.
- **Agents:** TypeScript (Node LTS).

## Package layout (each service)
`com.fooddelivery.<service>.{api, application, domain, infrastructure}`. Shared code lives only in `backend/platform/*` (common-web, common-security, common-events, common-persistence, common-observability, event-contracts), and it is cross-cutting only.
