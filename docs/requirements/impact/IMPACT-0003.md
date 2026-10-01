# IMPACT-0003: Error response format follows RFC 9457 (REQ-PLAT-004)

| Field | Value |
|---|---|
| Date | 2026-10-01 |
| Raised by | BackendAgent (Phase 4A, api-gateway error handler) |
| Requirements changed | REQ-PLAT-004 v1 → v2 (AC1 only) |
| Breaking change | No. No client exists yet; the gateway (only implementation so far) already returns the v2 format. |
| Status | **Approved** 2026-10-01 by project owner (Phase 4A checkpoint decision) |

## Change detected
REQ-PLAT-004 v1 AC1 and the approved API design (`docs/07-api-design.md` §3) describe two different error bodies:

| v1 AC1 | doc 07 §3 |
|---|---|
| `{success:false, code, message, timestamp, traceId, details?}` | RFC 9457 Problem Details: `type, title, status, code, detail, instance, correlationId, timestamp, errors[]`, content type `application/problem+json` |

Both cannot hold at once, and the shared error library (Phase 4B) needs one format.

## Resolution (v2)
- Every error response is RFC 9457 Problem Details as specified in doc 07 §3, with content type `application/problem+json`.
- The machine-readable `code` stays stable (same intent as v1).
- `detail` replaces v1 `message`; `errors[]` (field, code, message) replaces v1 `details`.
- `correlationId` replaces v1 `traceId` in the body. The same value is in the `X-Correlation-Id` response header. Logs carry both `correlationId` and `traceId`, so support can still jump from an error to the trace.
- `success:false` is dropped. The HTTP status already says the call failed, and RFC 9457 clients don't expect it.
- AC2 to AC5 are unchanged.

## Impact

| Area | Impact |
|---|---|
| Services | api-gateway: already compliant (`ErrorResponses`). All other services: the shared error library in Phase 4B implements v2 |
| APIs | Error schema in every OpenAPI document is `ProblemDetail` (doc 07 §3); no endpoint is implemented yet |
| Database | None |
| Events | None |
| Web screens | API client maps `code` to messages and reads `errors[]` for form fields (not built yet) |
| Mobile screens | Same as web (not built yet) |
| Tests | Gateway tests already assert the v2 body; Phase 4B error-format tests assert v2 |
| Documentation | None: doc 07 §3 already describes v2 |

## Regression scope
Gateway error tests (`GatewayIntegrationTest`), which already pass against v2. No other implementation exists.

## Approval
- [x] Approved by project owner on 2026-10-01. REQ-PLAT-004 v2 is APPROVED (recorded in `requirements.json` statusHistory).
