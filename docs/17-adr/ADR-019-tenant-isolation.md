# ADR-019: Tenant isolation with an application guard and PostgreSQL row-level security
- Status: Proposed
- Date: 2026-10-01
- Deciders: Pending project owner approval
- Related requirements: REQ-OUTLET-002, REQ-OUTLET-003 (AC4 asks for this ADR), BR-R11, REQ-AUTH-003 v2

## Context
Brands (tenants) share the services and databases. A missing filter in one query would leak another brand's sales, stock, recipes or suppliers. REQ-OUTLET-003 requires a non-null brand ID on every tenant-owned table, filtering by a shared persistence guard (not by hand), 404 on cross-tenant access, brand IDs in events, and an ADR on the technique.

## Decision
1. **Tenant column:** `restaurant_id uuid NOT NULL` (the brand) on every tenant-owned table in catalog, order, pos, kitchen, inventory and procurement. The name follows the approved code vocabulary (`restaurant`, `branch`; ROS-OQ-18).
2. **Application guard (primary):** `common-persistence` provides `TenantContext`, filled from the JWT for requests and from the event's `restaurantId` for consumers. Tenant-owned entities use Hibernate's `@TenantId`, which filters every query and stamps every insert automatically. A request without a tenant fails closed.
3. **Database guard (defence in depth):** PostgreSQL row-level security on the same tables with `FORCE ROW LEVEL SECURITY` and the policy `restaurant_id = current_setting('app.restaurant_id')::uuid`. `common-persistence` issues `SET LOCAL app.restaurant_id = …` at the start of every transaction. The runtime role `<service>_app` doesn't own the tables and has no `BYPASSRLS`, so the policy always applies to the application; without the setting, it sees no rows.
4. **Outlet scope** is checked in application services by `AccessPolicy` components (`branchId ∈ scopes` or a brand scope). IDs in request bodies are never trusted for scope.
5. **Cross-tenant access** returns 404. **Events** carry `restaurantId`; consumers reject mismatches to the DLT with an alert.
6. **Platform jobs** run per brand and set the context for each brand.
7. **Tests:** generated cross-brand read and write tests for every ROS endpoint; a repository test per service that runs raw SQL without the setting and expects zero rows.

## Consequences
### Positive
- Two independent layers: a forgotten filter in a native query is still blocked by the database.
- No per-query code; new entities get isolation by annotation.

### Negative / risks
- `SET LOCAL` per transaction adds a round trip (sub-millisecond on the same connection). Connection pools must not leak the setting: `SET LOCAL` is transaction-scoped, so it can't.
- `FORCE ROW LEVEL SECURITY` applies the policy to the table owner too. Flyway runs as `<service>_owner`, which is granted `BYPASSRLS` so that data migrations work across brands; that role is used only by the migration job and never by the running application (06 §1.1 already separates the two roles).
- Platform-wide admin reports need a per-brand loop or the analytics read model, never a bypass role.

## Alternatives considered
- **Schema per tenant:** strong isolation, but thousands of schemas complicate migrations and pooling.
- **Database per tenant:** strongest, but far beyond the portfolio's operating budget.
- **Application guard only:** simpler, but one native query without a filter leaks data.
- **RLS only:** protects the database, but errors surface late and every query pays for policy evaluation without an application-level fail-closed check.
