# 07 — API Design

| Field | Value |
|---|---|
| Version | 1.0.2 |
| Status | **Approved** 2026-10-01. v1.0.1: `USER_ALREADY_EXISTS` replaces `PHONE_ALREADY_REGISTERED` / `EMAIL_ALREADY_REGISTERED` to match REQ-AUTH-001 AC1 (owner decision 2026-10-02, approved). v1.0.2: HTTP statuses of the auth codes in §3.1 approved (owner 2026-10-02); §6.1 documents the access administration endpoints already listed in [05 §1.2](05-microservices.md) (REQ-AUTH-003) |
| Requirements | REQ-PLAT-001, REQ-PLAT-004, REQ-PLAT-006, REQ-SEC-001, REQ-SEC-002 |
| Endpoint inventory | [05 Microservices §3](05-microservices.md) |

The OpenAPI 3.1 specifications are generated from code (springdoc-openapi) and also committed per service to `docs/api/<service>.yaml` at release. CI fails on breaking changes against the previous release (openapi-diff). The web and mobile API client (`web/packages/api-client`) is generated from these specs.

---

## 1. Conventions

| Topic | Rule |
|---|---|
| Base URL | `https://api.<domain>/api/v1` (gateway). Local: `http://localhost:8080/api/v1` |
| Versioning | URI major version (`/v1`). Additive changes stay in v1. A breaking change requires `/v2` with both versions live for at least one release (deprecation header `Sunset`). |
| Resources | Plural nouns, kebab-case (`/addon-groups`). Actions that are not CRUD use sub-resource verbs: `POST /orders/{id}/cancel`, `POST /partner/orders/{id}/accept`. |
| Audience prefixes | `/partner/...` restaurant staff, `/delivery/...` partner, `/admin/...` back office, `/internal/v1/...` service-to-service (not routed by the gateway) |
| Format | JSON, UTF-8, `camelCase` fields. Timestamps ISO-8601 UTC (`2026-10-01T07:30:00Z`). Money as a **string** decimal plus currency: `{"amount":"249.00","currency":"INR"}`. IDs are UUID strings. |
| Enums | UPPER_SNAKE strings. Clients must tolerate unknown values (forward compatibility). |
| Nulls | Omitted fields mean "not provided". Explicit `null` is used only for PATCH-to-clear. |
| PATCH | JSON Merge Patch (`application/merge-patch+json`) where partial update is supported |
| Validation | Bean Validation on DTOs. Unknown properties are rejected on write endpoints (`FAIL_ON_UNKNOWN_PROPERTIES`), which prevents mass assignment. |
| Request size | Default limit 1 MB (gateway); uploads never pass through APIs (pre-signed URLs) |

## 2. Standard headers

| Header | Direction | Purpose |
|---|---|---|
| `Authorization: Bearer <jwt>` | Request | Access token |
| `X-Correlation-Id` | Both | Created by the gateway if absent; returned on every response; logged everywhere |
| `traceparent` | Both | W3C trace context (OpenTelemetry) |
| `Idempotency-Key` | Request | **Required** on `POST /orders`, `POST /payments`, `POST /admin/refunds`, and complaint resolutions with a refund. UUID recommended; 24 h retention. |
| `Accept-Language` | Request | Locale for messages (`en-IN` default) |
| `X-Client-App` / `X-Client-Version` | Request | `customer-web/1.4.0`, `delivery-mobile/1.2.0`. Used for metrics and minimum-version enforcement (`426 UPGRADE_REQUIRED`). |
| `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`, `Retry-After` | Response | Rate limiting (REQ-SEC-001) |
| `ETag` / `If-None-Match` | Both | Public menu and restaurant reads (304 support) |
| `Cache-Control` | Response | `no-store` on authenticated responses; short `public, max-age` on public catalogue reads |

## 3. Error format (REQ-PLAT-004)

RFC 9457 Problem Details, extended:

```json
{
  "type": "https://docs.fooddelivery.example/errors/INVALID_ORDER_TRANSITION",
  "title": "Invalid order transition",
  "status": 409,
  "code": "INVALID_ORDER_TRANSITION",
  "detail": "Order cannot move from DELIVERED to CANCELLED.",
  "instance": "/api/v1/orders/0192.../cancel",
  "correlationId": "c0a8012e-5b1f-4c7e-9f0a-2a1d3e4b5c6d",
  "timestamp": "2026-10-01T07:30:00Z",
  "errors": [
    { "field": "items[0].quantity", "code": "MAX", "message": "must be at most 50" }
  ]
}
```

Rules:
- No stack traces, SQL, class names or internal hostnames ever appear in responses (MP rule). They are logged with the correlation ID.
- `code` is stable and machine-readable; clients switch on `code`, not on `detail`.
- 5xx responses use generic text (`"An unexpected error occurred"`) plus the correlation ID.

### 3.1 Status code usage
| Status | When |
|---|---|
| 200 / 201 / 204 | Success. 201 with a `Location` header for creates. 202 for accepted asynchronous work (e.g. refund requested). |
| 400 `VALIDATION_FAILED` | Malformed or invalid input; `PASSWORD_POLICY_VIOLATION` (v1.0.1) |
| 401 `UNAUTHENTICATED` | Missing, invalid or expired token; `INVALID_CREDENTIALS`, `REFRESH_TOKEN_INVALID`, `REFRESH_TOKEN_REUSED` (v1.0.1) |
| 403 `FORBIDDEN` | Authenticated but lacking a permission **or** ownership (IDOR checks return 404 when revealing existence would leak data, e.g. another customer's order); `ACCOUNT_BLOCKED` (v1.0.1, only after a correct password) |
| 404 `NOT_FOUND` | Resource missing or not visible to the caller |
| 409 | State conflicts: `INVALID_ORDER_TRANSITION`, `ORDER_NOT_CANCELLABLE`, `CONCURRENT_MODIFICATION`, `CART_BRANCH_CONFLICT`, `IDEMPOTENCY_IN_PROGRESS`, `USER_ALREADY_EXISTS` (v1.0.1) |
| 410 | `QUOTE_EXPIRED` |
| 422 | Business rule violations: `QUOTE_INVALID`, `COUPON_NOT_APPLICABLE`, `COUPON_EXHAUSTED`, `BRANCH_CLOSED`, `ITEM_UNAVAILABLE`, `ADDRESS_NOT_SERVICEABLE`, `MIN_ORDER_NOT_MET`, `COD_NOT_ELIGIBLE`, `REFUND_EXCEEDS_CAPTURED`, `IDEMPOTENCY_KEY_REUSED` |
| 423 | `ACCOUNT_LOCKED` (with `Retry-After`) |
| 426 | `UPGRADE_REQUIRED` (client version too old) |
| 429 | `RATE_LIMITED` |
| 503 | `DEPENDENCY_UNAVAILABLE` (circuit open), `SERVICE_UNAVAILABLE` |

### 3.2 Error code catalogue (initial)
Owned in `backend/platform/common-web` as `ErrorCode` enums per domain, and documented at `docs/api/error-codes.md` (generated).

| Domain | Codes |
|---|---|
| Common | `VALIDATION_FAILED`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `CONCURRENT_MODIFICATION`, `RATE_LIMITED`, `IDEMPOTENCY_IN_PROGRESS`, `IDEMPOTENCY_KEY_REUSED`, `DEPENDENCY_UNAVAILABLE`, `INTERNAL_ERROR`, `UPGRADE_REQUIRED` |
| Auth | `INVALID_CREDENTIALS`, `ACCOUNT_LOCKED`, `ACCOUNT_BLOCKED`, `OTP_INVALID`, `OTP_EXPIRED`, `OTP_RATE_LIMITED`, `REFRESH_TOKEN_INVALID`, `REFRESH_TOKEN_REUSED`, `PASSWORD_POLICY_VIOLATION`, `USER_ALREADY_EXISTS` (v1.0.1; the response does not say whether the e-mail address or the phone number matched) |
| Restaurant / menu | `RESTAURANT_NOT_APPROVED`, `BRANCH_CLOSED`, `ITEM_UNAVAILABLE`, `ADDON_SELECTION_INVALID`, `INVALID_HOURS` |
| Cart / quote | `CART_BRANCH_CONFLICT`, `CART_EMPTY`, `MIN_ORDER_NOT_MET`, `ADDRESS_NOT_SERVICEABLE`, `QUOTE_EXPIRED`, `QUOTE_INVALID`, `PRICE_CHANGED` |
| Promotion | `COUPON_NOT_FOUND`, `COUPON_EXPIRED`, `COUPON_NOT_APPLICABLE`, `COUPON_EXHAUSTED`, `COUPON_USER_LIMIT_REACHED` |
| Order | `INVALID_ORDER_TRANSITION`, `ORDER_NOT_CANCELLABLE`, `ORDER_CLOSED` |
| Payment / wallet | `PAYMENT_ALREADY_EXISTS`, `PAYMENT_AMOUNT_MISMATCH`, `COD_NOT_ELIGIBLE`, `WEBHOOK_SIGNATURE_INVALID`, `REFUND_EXCEEDS_CAPTURED`, `WALLET_INSUFFICIENT_BALANCE` |
| Delivery | `PARTNER_NOT_VERIFIED`, `OFFER_EXPIRED`, `OFFER_NOT_AVAILABLE`, `DELIVERY_OTP_INVALID`, `PARTNER_HAS_ACTIVE_DELIVERY` |
| AI | `ASSISTANT_BUDGET_EXCEEDED`, `ASSISTANT_UNAVAILABLE`, `TOOL_CONFIRMATION_REQUIRED` |

## 4. Pagination, filtering, sorting

- **Cursor pagination** (default for feeds and large lists: orders, notifications, reviews, admin lists):
  `GET /orders?limit=20&cursor=eyJjIjoiMjAyNi0xMC0wMVQwNzozMDowMFoiLCJpZCI6Ii4uLiJ9`
  ```json
  { "items": [ ... ], "page": { "limit": 20, "nextCursor": "…", "hasMore": true } }
  ```
  The cursor is an opaque base64 encoding of `(sortKey, id)`. It is stable under inserts.
- **Offset pagination** only for small admin tables where jumping to a page is needed: `?page=0&size=50` returns `page.totalElements`.
- `limit` max 100 (default 20).
- Filters are plain query parameters (`?status=CONFIRMED&branchId=…&from=2026-10-01`). Sort uses `?sort=createdAt,desc` with an allow-list per endpoint.

## 5. Idempotency (REQ-PLAT-006)

1. The client sends `Idempotency-Key`. The server hashes the canonical request body (SHA-256).
2. `INSERT … ON CONFLICT DO NOTHING` into `idempotency_keys` with status `IN_PROGRESS`:
   - A new key proceeds to the business logic. The response is stored as `COMPLETED` in the same transaction as the business change.
   - An existing key with the same hash and `COMPLETED` replays the stored status and body (header `Idempotent-Replayed: true`).
   - An existing key with the same hash and `IN_PROGRESS` returns `409 IDEMPOTENCY_IN_PROGRESS` (`Retry-After: 1`).
   - An existing key with a different hash returns `422 IDEMPOTENCY_KEY_REUSED`.
3. Keys are scoped per user and endpoint, with a 24 h TTL.

Webhooks are deduplicated by `provider_event_id`, and Kafka consumers by `eventId`. That gives three layers of protection against double charging (NFR-CONS-002).

## 6. Authentication and authorisation on APIs
- Public endpoints are explicitly listed in the gateway (§05 1.2); everything else requires a JWT.
- Method security: `@PreAuthorize("hasAuthority('ORDER_CANCEL')")`, plus an ownership check in the application service (`order.customerId == principal.userId`, or a staff scope that contains `order.branchId`).
- Internal endpoints require a service token (client-credentials JWT with `aud=internal` and scope `svc:<caller>`) and are blocked by NetworkPolicy from outside the namespace.
- No token, or an invalid one, is 401 `UNAUTHENTICATED` with `WWW-Authenticate: Bearer`; a valid token without the permission is 403 `FORBIDDEN`. Endpoints without a declared permission or `@PermitAll` fail the build (REQ-AUTH-003 AC3).

### 6.1 Access administration (identity-service, REQ-AUTH-003, v1.0.2)
Every change takes a `reason` and writes an `AuditRecorded` event (`audit.events.v1`) in the same transaction. Role changes reach access tokens at the next refresh (at most 15 minutes).

| Method and path | Permission | Result |
|---|---|---|
| `GET /api/v1/admin/permissions` | `PERMISSION_MANAGE` | 200: `[{code, category, description}]` |
| `GET /api/v1/admin/roles` | `ROLE_MANAGE` | 200: `[{code, description, scopeTypes, permissions}]` |
| `PUT /api/v1/admin/roles/{code}` body `{permissions, reason}` | `PERMISSION_MANAGE` | 200 with the role. Unknown permission codes are 400; `SUPER_ADMIN` cannot be changed (403) |
| `GET /api/v1/admin/users/{userId}/roles` | `ROLE_MANAGE` | 200: `[{id, role, scopeType, scopeId, grantedBy, grantedAt}]`; 404 for an unknown user |
| `POST /api/v1/admin/users/{userId}/roles` body `{role, scopeType?, scopeId?, reason}` | `ROLE_MANAGE` | 201 for a new assignment, 200 when it already exists. `scopeType` defaults to `GLOBAL`; `RESTAURANT` and `BRANCH` need `scopeId` and must be allowed for the role (400 otherwise) |
| `DELETE /api/v1/admin/users/{userId}/roles/{assignmentId}?reason=` | `ROLE_MANAGE` | 204. The last `SUPER_ADMIN` cannot be removed (403) |

Only a caller holding `SUPER_ADMIN` grants or revokes `ADMIN` and `SUPER_ADMIN` (403 otherwise, AC5). The first `SUPER_ADMIN` comes from `IDENTITY_BOOTSTRAP_SUPER_ADMIN`, which names an account that is already registered. It takes effect only while no `SUPER_ADMIN` exists, and the grant is audited with actor `SYSTEM` (owner decision 2026-10-02).

## 7. Rate limits (initial, REQ-SEC-001)
| Bucket | Key | Limit |
|---|---|---|
| OTP send | phone | 3 / 10 min, 10 / day |
| OTP verify | phone | 5 attempts per OTP |
| Login | identifier + IP | 10 / 15 min (then progressive lockout per REQ-AUTH-005) |
| Authenticated default | userId | 120 / min |
| Anonymous default | IP | 60 / min |
| Search | userId or IP | 60 / min |
| Location ingest | partnerId | 30 / min (batches) |
| Assistant messages | userId | 20 / min, plus a daily token budget |
| Webhooks | provider IP allow-list | Not rate-limited, signature-verified |

Limits are configured in config-server per environment and are not hardcoded.

## 8. Sample contracts (key flows)

### 8.1 Create quote
`POST /api/v1/cart/quote`
```json
{ "addressId": "0192...a1", "couponCode": "WELCOME50", "paymentMethod": "ONLINE" }
```
`200 OK`
```json
{
  "quoteId": "0192...q1",
  "expiresAt": "2026-10-01T07:40:00Z",
  "branchId": "0192...b1",
  "lines": [
    { "productId": "0192...p1", "name": "Paneer Butter Masala", "variant": "Full", "quantity": 1,
      "addons": [{ "name": "Butter Naan", "price": { "amount": "45.00", "currency": "INR" } }],
      "lineTotal": { "amount": "324.00", "currency": "INR" } }
  ],
  "breakdown": {
    "itemTotal":   { "amount": "324.00", "currency": "INR" },
    "packagingFee":{ "amount": "15.00",  "currency": "INR" },
    "deliveryFee": { "amount": "30.00",  "currency": "INR" },
    "platformFee": { "amount": "5.00",   "currency": "INR" },
    "taxTotal":    { "amount": "17.20",  "currency": "INR" },
    "discount":    { "amount": "-50.00", "currency": "INR" },
    "grandTotal":  { "amount": "341.20", "currency": "INR" }
  },
  "estimatedDeliveryMinutes": 35,
  "warnings": []
}
```
The signature is not exposed. The quote is stored server-side, and order-service verifies it by ID plus HMAC over the stored content.

### 8.2 Place order
`POST /api/v1/orders` with headers `Idempotency-Key: 5d0c…` and the body `{ "quoteId": "0192...q1", "paymentMethod": "ONLINE", "instructions": "Ring the bell" }`

`201 Created`, `Location: /api/v1/orders/0192...o1`
```json
{ "orderId": "0192...o1", "orderNumber": "FD-20261001-7K3Q9", "status": "PAYMENT_PENDING",
  "grandTotal": { "amount": "341.20", "currency": "INR" }, "paymentDeadline": "2026-10-01T07:45:00Z" }
```
Errors: `410 QUOTE_EXPIRED`, `422 QUOTE_INVALID`, `422 COUPON_EXHAUSTED`, `422 BRANCH_CLOSED`, `409 IDEMPOTENCY_IN_PROGRESS`.

### 8.3 Initiate payment
`POST /api/v1/payments` with `Idempotency-Key` and the body `{ "orderId": "0192...o1", "method": "UPI" }`

`201`
```json
{ "paymentId": "0192...y1", "status": "PENDING", "provider": "RAZORPAY",
  "checkout": { "providerOrderId": "order_Nx…", "keyId": "rzp_test_…", "amount": 34120, "currency": "INR" } }
```
The amount comes from order-service, never from the client. `keyId` is the **publishable** key; the secret never leaves the server. The client's success callback is informational only: the order is confirmed **only** by the verified webhook (MP rule: never trust frontend payment status).

### 8.4 Restaurant accept
`POST /api/v1/partner/orders/{orderId}/accept` with `{ "prepTimeMinutes": 20 }` returns `200` and the order summary with `status: RESTAURANT_ACCEPTED`. Possible error: `409 INVALID_ORDER_TRANSITION`.

### 8.5 Partner location batch
`POST /api/v1/locations/batch`
```json
{ "points": [ { "lat": 12.9716, "lng": 77.5946, "accuracyM": 8.5, "speedMps": 6.2, "heading": 90, "recordedAt": "2026-10-01T07:31:05Z" } ] }
```
`202 Accepted`. Points with accuracy above 100 m, a timestamp more than 2 min away from server time, or an implied speed above 40 m/s are dropped and counted in metrics.

### 8.6 Order tracking (polling fallback)
`GET /api/v1/orders/{id}/tracking`
```json
{ "orderId": "…", "status": "OUT_FOR_DELIVERY",
  "partner": { "name": "Ravi", "vehicleType": "SCOOTER", "rating": 4.7, "maskedPhone": "+91 ******3210" },
  "partnerLocation": { "lat": 12.97, "lng": 77.59, "recordedAt": "…" },
  "eta": { "minutes": 9, "updatedAt": "…" },
  "timeline": [ { "status": "CONFIRMED", "at": "…" } ] }
```
The customer never receives the partner's real phone number. Calls go through a masked-number provider (backlog) or the in-app call flow.

## 9. WebSocket (STOMP) contract summary (REQ-RT-001)
- Connect: `wss://api.<domain>/ws` with a STOMP `CONNECT` header `Authorization: Bearer <jwt>`. An expired token gets `ERROR` and the client refreshes, then reconnects.
- Subscriptions are authorised per destination (owner or scope). An unauthorised `SUBSCRIBE` gets `ERROR` `FORBIDDEN`.
- Message envelope: `{ "type": "ORDER_STATUS_CHANGED", "version": 1, "occurredAt": "…", "data": { … } }`.
- Clients resync with REST after a reconnect (gap-safe); messages carry `orderVersion` for ordering.

## 10. Documentation
- Swagger UI per service in local and dev only (disabled in production). An aggregated view runs at the gateway in dev.
- Each endpoint documents its permissions (`x-required-permissions`), idempotency requirement and error codes.
