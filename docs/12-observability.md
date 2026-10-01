# 12 — Observability

| Field | Value |
|---|---|
| Version | 1.0.0 |
| Status | **Approved** 2026-10-01 |
| Requirements | REQ-OBS-001 (Phase 4), REQ-OBS-002/003 (Phase 20), REQ-ADMIN-004, NFR-OBS-001 |

Stack: **OpenTelemetry** (instrumentation and collector), **Prometheus** (metrics), **Grafana** (dashboards and alerting UI), **Loki** (logs), **Tempo** (traces), **Alertmanager** (routing). Locally these run in the Compose profile `observability`. In the cloud they are self-hosted on EKS (cheapest option for the portfolio) or Amazon Managed Prometheus/Grafana (decided in Phase 20 by cost).

---

## 1. Logging (REQ-OBS-001, Phase 4)

- **Format:** JSON to stdout (Logback + logstash-encoder, or Spring Boot structured logging). One event per line.
- **Mandatory fields:**
  `timestamp`, `level`, `service`, `version`, `env`, `traceId`, `spanId`, `correlationId`, `userId` (when authenticated; ID only), `logger`, `thread`, `message`, plus event-specific structured fields (`orderId`, `eventType`, …).
- **Correlation:**
  1. The gateway accepts or creates `X-Correlation-Id`, and the OTel context propagates `traceparent`.
  2. Services put both into MDC via `common-observability` filters.
  3. Kafka producers copy them into headers, and consumers restore MDC from the headers.
  4. Scheduled jobs create a new correlation ID per run.
- **Levels:** ERROR = needs action; WARN = degraded but handled (fallback, retry); INFO = business milestones (order state change, payment captured), not per-request noise; DEBUG is off in production (dynamic per-logger enabling via actuator in staging).
- **Masking:** PII and secret masking converters (09 §4). Masking is unit-tested with sample payloads.
- **Access logs:** gateway only (method, path template, status, latency, client app/version, user ID), with no query strings for auth endpoints.
- **Retention:** Loki 14 days (development and staging), 30 days (production). Audit records are not logs; they are kept in audit-service.

## 2. Metrics (REQ-OBS-002)

Micrometer exposes `/actuator/prometheus` on the management port. Common tags: `service`, `env`, `version`, `instance`.

### 2.1 Standard metrics (all services)
HTTP server (`http_server_requests_seconds` histogram with `uri` template, `method`, `status`, `outcome`), HTTP client calls, JVM (heap, GC, threads, virtual threads), HikariCP pool, Kafka producer and consumer (lag via `kafka_consumer_fetch_manager_records_lag_max` plus the Kafka exporter), Resilience4j (circuit state, calls), Redis (Lettuce command latency), cache hit/miss, outbox metrics (08 §6), scheduled job duration and failures.

### 2.2 Business metrics
| Metric | Type | Labels |
|---|---|---|
| `orders_created_total` | counter | city, payment_method |
| `order_state_transitions_total` | counter | from, to |
| `order_time_to_confirm_seconds` | histogram | payment_method |
| `order_time_to_accept_seconds` | histogram | auto_accept |
| `order_delivery_duration_seconds` | histogram | city |
| `orders_cancelled_total` | counter | by_type, reason |
| `payments_total` | counter | provider, method, status |
| `webhook_signature_failures_total` | counter | provider |
| `refunds_total` / `refund_amount_inr_total` | counter | destination, status |
| `assignment_time_seconds`, `assignment_failed_total`, `offer_outcome_total` | histogram/counter | city, outcome |
| `location_updates_received_total`, `location_updates_dropped_total` | counter | reason |
| `realtime_connections` | gauge | instance |
| `realtime_push_latency_seconds` (event occurredAt → socket send) | histogram | type |
| `notifications_sent_total` | counter | channel, status |
| `assistant_requests_total`, `assistant_tokens_total`, `assistant_cost_usd_total`, `assistant_tool_calls_total` | counter | model, tool, outcome |
| `coupon_redemptions_total` | counter | offer_type |

High-cardinality values (user IDs, order IDs) are **never** metric labels; they belong in logs and traces. Exemplars link latency histograms to trace IDs.

## 3. Tracing (REQ-OBS-003)

- The OpenTelemetry Java agent (or Micrometer Tracing with the OTel bridge) covers HTTP server and client, JDBC, Kafka (context in headers), Redis and scheduled jobs. Web and mobile clients send `traceparent` from the API client (generated per user action).
- **Sampling:** parent-based. 100% in local and development. In staging and production, 10% head sampling plus tail sampling at the collector that keeps all errors and all traces slower than 1 s.
- Spans carry `order.id`, `payment.id` and similar attributes for searchability (not PII).
- The **saga view** links all spans for one order via correlation ID and order ID across asynchronous hops (Kafka span links).
- NFR-OBS-001: 100% of requests carry a trace ID in logs, even when not sampled for export.

## 4. SLOs and SLIs

| SLO | SLI | Target (design; verified in Phase 21) | Requirement |
|---|---|---|---|
| Core API latency | p95 of `http_server_requests_seconds` for order, cart and auth routes | < 300 ms | NFR-PERF-001 |
| Discovery and menu latency | p95 for search, discovery and menu routes | < 300 ms (stretch < 200 ms) | NFR-PERF-002 |
| Location freshness | p95 of partner `recordedAt` → customer socket send | < 2 s | NFR-PERF-003/004 |
| Event propagation | p95 of outbox `created_at` → consumer processed | < 5 s (proposed) | NFR-CONS-003 |
| Availability | Successful (non-5xx) gateway responses ÷ total, monthly | 99.9% (design) | NFR-AVAIL-001 |
| Order success | Orders reaching CONFIRMED ÷ orders created (excluding customer cancel and payment failure by customer) | Tracked, with a target set after baseline | BR-04 |

Error budget policy (production): if the burn rate exceeds 2× for 1 h, non-critical deploys are paused until the cause is fixed.

## 5. Dashboards (Grafana, provisioned as code in `infrastructure/observability/dashboards/`)
1. **Platform overview:** RPS, error rate, p95 per service, saturation (CPU, memory), pods.
2. **Service detail (template):** RED metrics, JVM, database pool, Kafka lag, circuit breakers.
3. **Order funnel:** created → confirmed → accepted → delivered; cancellations by reason; time per stage.
4. **Payments:** success rate by method, webhook latency and failures, refunds, reconciliation mismatches.
5. **Delivery operations:** assignment time, failures by city, partner online count, offer acceptance.
6. **Real-time and location:** connections, push latency, ingest rate, dropped updates.
7. **Kafka:** throughput, lag per group, DLT counts, outbox backlog.
8. **Data stores:** PostgreSQL (connections, slow queries, replication lag), Redis (memory, evictions, latency).
9. **AI:** requests, latency, tokens, cost vs. budget, tool usage, guardrail blocks (REQ-AI-003).
10. **SLO:** error budget burn per SLO.

The admin dashboard "System health" (REQ-ADMIN-004) shows a curated subset through an admin-service endpoint that queries Prometheus with a read-only token. It does not embed Grafana with admin credentials.

## 6. Alerts (Prometheus rules in Git; routed by Alertmanager)

| Alert | Condition | Severity |
|---|---|---|
| HighErrorRate | 5xx above 2% for 5 min (per service) | critical |
| LatencySLOBurn | Multi-window burn rate (1 h / 5 min) above 14.4× | critical |
| PodCrashLooping / NotReady | Above 3 restarts in 10 min / replicas below minimum for 5 min | critical |
| KafkaConsumerLagHigh | Lag above threshold for 10 min (per group; order saga 1,000, analytics 100K) | warning/critical |
| DLTMessages | Any new DLT message (order, payment, delivery topics) | critical |
| OutboxStuck | Oldest unpublished row above 60 s | critical |
| PaymentWebhookSignatureFailures | Above 5 in 10 min | critical (security) |
| ReconciliationMismatch | Above 0 after a run | warning |
| RefundFailed | Any | warning |
| AssignmentFailureRate | Above 5% for 15 min per city | critical |
| LocationIngestDrop | Drop ratio above 5% for 10 min | warning |
| RedisStateEvictions | Above 0 | critical |
| DBConnectionsSaturated | Pool usage above 90% for 5 min | warning |
| RefreshTokenReuseSpike / LockoutSpike | Above baseline × 5 | warning (security) |
| AIBudgetBurn | Daily spend above 80% of budget | warning |
| CertificateExpiry | Less than 14 days | warning |

**Routing:** critical alerts go to the on-call channel (e-mail or Slack webhook) with a runbook link (`docs/runbooks/<alert>.md`, written in Phases 20 and 23). Warnings go to the team channel.

## 7. Health checks
- Liveness covers only the process (no dependency checks), so dependency outages do not cause restart storms.
- Readiness checks the database, Kafka producer readiness and Redis (for services where these are mandatory), plus a warm-up flag (caches primed, consumers assigned).
- The startup probe allows slow JVM start (up to 120 s).
