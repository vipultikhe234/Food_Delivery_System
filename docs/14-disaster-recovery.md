# 14 — Disaster Recovery and Resilience

| Field | Value |
|---|---|
| Version | 1.0.0 |
| Status | **Approved** 2026-10-01 |
| Requirements | REQ-DEVOPS-005, REQ-PLAT-007, NFR-REL-001, NFR-REL-002 (RPO ≤ 5 min, RTO ≤ 1 h; PROPOSED), NFR-AVAIL-001 |

---

## 1. Objectives

| Tier | Services and data | RPO | RTO |
|---|---|---|---|
| Tier 0, money and orders | order_db, payment_db (incl. wallet), identity_db | ≤ 5 min (PITR; in practice seconds with Multi-AZ synchronous standby) | ≤ 1 h (region-level); ≤ 5 min (AZ-level automatic failover) |
| Tier 1, operations | restaurant, menu, delivery, promotion, cart, user, notification, audit databases | ≤ 5 min | ≤ 1 h |
| Tier 2, rebuildable | search_db, analytics_db, recommendation_db (rebuilt from events and sources), Redis caches | N/A (rebuild) | ≤ 4 h (degraded features meanwhile) |
| Ephemeral | Redis state (OTP, rate limits, GEO), location stream | Accept loss | Minutes (re-populated by clients) |

These targets are **proposed** (NFR-REL-002) and are verified only by the DR drills in §5. They cannot be claimed beforehand.

## 2. Failure domains and responses

| Failure | Expected impact | Automatic response | Manual runbook |
|---|---|---|---|
| Pod crash | None (replicas ≥ 2) | Kubernetes restart and readiness gating | — |
| Node loss | Brief capacity dip | Rescheduling; PDBs; topology spread | — |
| AZ loss | Degraded capacity for minutes | RDS Multi-AZ failover (about 60–120 s), ElastiCache replica promotion, MSK RF3 across 3 AZs, EKS nodes in 3 AZs | Verify, then scale up the remaining AZs |
| PostgreSQL primary failure | Writes fail for about 1–2 min for that service | Multi-AZ failover; Hikari reconnect; outbox resumes; Kafka consumers retry | `runbooks/db-failover.md` |
| Kafka broker loss | None (RF3, min ISR 2) | Leader election | — |
| Kafka cluster outage | Events delayed; order creation still commits (outbox); async steps pause | Outbox accumulates; consumers resume from committed offsets | `runbooks/kafka-outage.md`; monitor outbox backlog |
| Redis state outage | See 10 §4 (assignment pauses, OTP unavailable, polling fallback) | Replica promotion | Rebuild assignment schedule from database |
| Payment gateway outage | Online payment unavailable | Circuit breaker; UI offers COD/wallet where eligible; pending payments reconciled later | `runbooks/payment-provider-outage.md` |
| SMS/push provider outage | Notification delay | Fallback channel for critical messages | Switch provider adapter via configuration |
| LLM provider outage | Assistant degraded | Circuit breaker; fallback to keyword search | — |
| Bad deployment | Errors or latency | Automatic rollback on smoke/SLO failure (13 §5.2) | `runbooks/rollback.md` |
| Data corruption or bad migration | Logical damage | — | PITR to a new instance, compare, repair; see §4 |
| Region loss | Full outage | — | Restore in a secondary region (§3.3) |
| Security incident (credential leak) | Varies | Alerts (09) | `runbooks/incident-response.md`: rotate keys, revoke sessions, audit review |

## 3. Backups

### 3.1 PostgreSQL
- RDS automated backups with **PITR**, retention 14 days (production), 7 days (staging when it exists).
- Daily snapshots copied **cross-region** (e.g. ap-south-1 to ap-southeast-1), retained 35 days. Monthly snapshots kept 1 year for Tier 0.
- Backups are KMS-encrypted; the cross-region copy uses a key in the destination region.
- Weekly automated **restore test**: restore the latest snapshot of order_db and payment_db to a temporary instance, run integrity checks (row counts vs. metrics, `refunded_amount <= captured_amount` invariants, audit hash-chain verification for audit_db), then destroy it. The result is recorded as evidence.

### 3.2 Other stores
| Store | Backup |
|---|---|
| S3 media | Versioning + cross-region replication for private documents; lifecycle to cheaper tiers |
| Kafka | Not backed up as a store. It is a transport; sources of truth are the databases (outbox). MSK configuration is in Terraform. |
| Redis | `redis-state` daily snapshot (optional; data is ephemeral or rebuildable); caches not backed up |
| Config repository | Git (hosted; mirrored) |
| Terraform state | S3 versioned + DynamoDB lock |
| Secrets | AWS Secrets Manager with multi-region replication for Tier 0 keys |
| Container images | ECR replication to the DR region |

### 3.3 Regional DR strategy: **backup and restore** (cost-appropriate)
Pilot-light or warm-standby across regions is not justified for the portfolio context (ADR-016). Region recovery procedure (target ≤ 1 h for Tier 0 once rehearsed):
1. Declare the incident and freeze deployments.
2. `terraform apply` the `prod-dr` environment in the DR region: network, EKS, RDS from the latest cross-region snapshots plus PITR where available, ElastiCache, MSK.
3. Deploy the current release via the pipeline (images already replicated).
4. Restore Tier 0, then Tier 1 databases. Tier 2 is rebuilt by replaying from sources (search reindex job, analytics backfill).
5. Switch DNS (Route 53 failover record) and CloudFront origins.
6. Reconcile payments with the gateway for the outage window (reconciliation job with an extended window).
7. Communicate status and run the post-incident review.

## 4. Data recovery scenarios

| Scenario | Procedure |
|---|---|
| Accidental deletion or bad update in one service database | PITR to a new instance at T−1 min, extract affected rows, repair via an audited admin script (reviewed PR), and re-emit corrective events if downstream read models are affected |
| Bad migration | Expand/contract makes most migrations reversible by redeploying the previous release. Destructive contract steps happen only after a backup checkpoint. |
| Corrupted read model (search, analytics) | Truncate and rebuild via the reindex/backfill job from source services and the Kafka retention window |
| Event replay | Consumers are idempotent, so offsets can be reset to a timestamp for a consumer group (`kafka-consumer-groups --reset-offsets --to-datetime`) within the 7-day retention |
| Lost outbox publication | Impossible by design. Rows stay until published, and the relay retries. |

## 5. DR drills (evidence for REQ-DEVOPS-005, Phase 24)

| Drill | Frequency (production) | Portfolio evidence |
|---|---|---|
| Pod and node kill during load (chaos) | Monthly | Phase 21 failure scenario |
| RDS failover (forced reboot with failover) | Quarterly | Once in staging, with measured write downtime |
| Kafka broker loss | Quarterly | Once in staging |
| Snapshot restore + integrity check | Weekly (automated) | At least 3 successful runs recorded |
| Full region restore (game day) | Yearly | Once, measuring actual RTO and RPO against targets |

Each drill records start and end times, the measured RTO and RPO, problems found and follow-up actions in `docs/releases/dr-drills/<date>.md`.

## 6. Resilience patterns (REQ-PLAT-007)

| Pattern | Default configuration (tunable per client) |
|---|---|
| Timeouts | Connect 500 ms; read 2 s (menu price check 500 ms; payment gateway 10 s) |
| Retry | Only idempotent operations (GET, or POST with an idempotency key); 2 retries, exponential backoff 200 ms with jitter; never retry on 4xx |
| Circuit breaker | Sliding window 50 calls; failure rate 50%; slow call above 2 s counts at 50%; open 30 s; half-open 5 calls |
| Bulkhead | Semaphore per downstream (e.g. 50 concurrent calls) so one slow dependency cannot exhaust request threads |
| Rate limiter (client side) | Provider limits (SMS, LLM) |
| Fallbacks | Cache for read-only data (menu, branch status, with a staleness flag); fail fast with a clear error for money-related checks; queue for later (notifications) |
| Load shedding | Gateway returns 503 with `Retry-After` when downstream circuits are open; low-priority routes (recommendations) are shed first |
