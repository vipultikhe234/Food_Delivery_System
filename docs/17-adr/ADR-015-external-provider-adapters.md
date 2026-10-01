# ADR-015: Ports and adapters for external providers; initial providers
- Status: Accepted
- Date: 2026-10-01
- Related requirements: REQ-PAYMENT-001..005, REQ-LOCATION-002..003, REQ-NOTIF-001, REQ-AI-001..003, REQ-MEDIA-001, REQ-PLAT-007

## Context
External providers have costs, sandbox limits and outages, and may change. Tests must not depend on them.

## Decision
Each external dependency sits behind a port (interface) in the owning service. Every port has a **fake/mock adapter** used locally and in automated tests, plus WireMock contract tests for the real adapter.

| Port | Owner | Initial real adapter | Local/test adapter |
|---|---|---|---|
| `PaymentGateway` | payment-service | **Razorpay** (test mode) | `MockPaymentGateway` (success, failure, delay, duplicate webhook scenarios) |
| `RoutingProvider` | location-service | **Haversine × road factor** (in-process) | Same |
| `SmsSender` | notification-service | MSG91 or Twilio (chosen at Phase 11 by cost) | Logging fake |
| `EmailSender` | notification-service | Amazon SES | Mailpit (SMTP) |
| `PushSender` | notification-service | Firebase Cloud Messaging (Android + iOS via APNs) | Recording fake |
| `LlmClient` | ai-service | Spring AI abstraction; default provider chosen at Phase 17 by an evaluation (quality, latency, cost) | Scripted fake model for deterministic tests |
| `ObjectStorage` | media-service | Amazon S3 + CloudFront | MinIO |
| `MalwareScanner` | media-service | ClamAV container | Always-clean fake (tests inject infected samples) |

Map **display** in clients uses OpenStreetMap tiles via MapLibre/Leaflet, with no per-request cost. A paid map SDK can replace it later.

## Consequences
### Positive
- No provider lock-in. Deterministic tests. Development works offline.
- Provider choice can be deferred to the phase that needs it.

### Negative / risks
- Adapters must be kept faithful to real behaviour. Contract tests against recorded provider responses mitigate this.

## Alternatives considered
- **Calling provider SDKs directly:** faster to start, but untestable and locks in the provider.
