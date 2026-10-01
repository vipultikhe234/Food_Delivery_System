# 11 — Scalability and Capacity

| Field | Value |
|---|---|
| Version | 1.0.0 |
| Status | **Approved** 2026-10-01 |
| Requirements | NFR-SCALE-001..003, NFR-PERF-001..004, REQ-QA-003, OQ-01 (portfolio context) |

The targets below are **design targets**. None are claimed as achieved until measured (MP rule). Under OQ-01 the platform is designed for the REF p.1 scale, a modest agreed load is demonstrated, and the extrapolation is documented.

---

## 1. Capacity model (design scale)

| Input (REF p.1 / NFR-SCALE) | Value |
|---|---|
| Active users | 10M+ |
| Restaurants | 500K+ (branches about 600K) |
| Orders per day | 1M+ |
| Concurrent users | 100K+ |
| Peak TPS | 5,000–10,000 |
| Read:write | 10:1 |
| Delivery partners | 500K, location every 5–10 s |

| Derived quantity | Calculation | Result |
|---|---|---|
| Average order rate | 1,000,000 / 86,400 | **about 12 orders/s** |
| Peak order rate | 5–10× average (lunch/dinner peaks) | **60–120 orders/s** |
| Writes per order (all services, incl. history, outbox, events) | about 40–60 row writes and 15–25 events across services | about 3K–7K writes/s and 1.5K–3K events/s at peak |
| Read traffic | Peak TPS minus writes | 5K–10K req/s, dominated by discovery, menu, tracking polls |
| Active orders in flight (Little's law, about 45 min lifecycle) | 12/s × 2,700 s (average); 60–120/s × 2,700 s (peak) | **about 31K average; about 160K–320K at sustained peak** |
| Location updates | 500K partners ÷ 5 s (all online, worst case) | **up to 100K updates/s** (REF p.1). A realistic 30–40% online at peak gives 30K–40K/s. |
| Location fan-out to customers | Only partners on active deliveries, about 31K–160K, every 5–10 s | about 5K–30K pushes/s |
| WebSocket connections | Customers tracking + partners online + restaurant dashboards | **about 300K–500K at peak** |
| Search/discovery | About 50% of read traffic | 2.5K–5K req/s |

**Conclusion:** order and payment writes are modest. The scaling problems are (1) location ingestion and fan-out, (2) read-heavy discovery and menu traffic, and (3) WebSocket connection count. The architecture isolates exactly these (location-service, Redis caching + search-service, realtime-service).

## 2. Scaling strategy per component

| Component | Strategy at design scale |
|---|---|
| API gateway | Stateless, HPA on CPU/RPS. About 10–20 pods at 10K RPS (assuming about 1K RPS/pod; verified in Phase 21). |
| menu, restaurant (public reads) | Redis cache hit above 90%; HPA on CPU; ETag + CDN for public catalogue JSON (short TTL) |
| search-service | PostgreSQL read replicas (2–3) for the search database; geohash-cell caching; move to OpenSearch if p95 is above target (ADR-009 trigger) |
| order, payment | Vertical first (write volume is low). Dedicated database instances. Connection pools sized (HikariCP `maximumPoolSize` ≈ cores × 2–4) with PgBouncer in transaction mode at high pod counts. |
| location-service | Stateless ingest; Redis GEO sharded per city; Kafka `location.updates.v1` with 48+ partitions; history writes only for active deliveries, down-sampled (06 §2.11) |
| realtime-service | About 20K–50K connections per pod (JVM, tuned; verified in Phase 21); 10–25 pods at 500K connections; HPA on connection count; Redis Pub/Sub cluster |
| delivery-service | Workers partitioned by city; assignment throughput about 120 orders/s at peak is small; Redis lock per order |
| notification-service | Kafka consumer scaling up to the partition count; provider rate limits respected with token buckets per provider |
| Kafka | 3–6 brokers (MSK) at design scale; partitions per 08 §2; monitor consumer lag |
| PostgreSQL | Per-service instances; read replicas for read-heavy services (search, restaurant, menu fallback, analytics); partitioning for high-volume tables (06 §5); PITR backups |
| Redis | Cache and state separation; cluster mode for state at design scale |
| CDN | Images (media variants), web app bundles, public catalogue JSON |

### 2.1 Statelessness rules (NFR-SCALE-003)
- No HTTP session state. No sticky sessions except the WebSocket connection itself, and realtime uses Redis fan-out so any instance can deliver.
- Scheduled jobs use ShedLock (one runner per job), so pods are interchangeable.
- File uploads go directly to object storage.
- Graceful shutdown: `server.shutdown=graceful`, a 30 s grace period, readiness flips to false first, Kafka consumers stop and commit, and realtime sends a reconnect hint to clients.

### 2.2 Database scaling path (per service)
1. Indexes and query plans (`pg_stat_statements` reviewed in Phase 21).
2. Connection pooling (PgBouncer).
3. Read replicas for read-mostly queries, with read-your-writes handled by routing post-write reads to the primary for N seconds.
4. Partitioning of large append-only tables (history, audit, analytics, locations).
5. Archive or tier old data.
6. Functional sharding is already done (database per service). Horizontal sharding (e.g. orders by city via Citus) is noted as a future option and **not** designed for v1.

## 3. Verified load profile (portfolio)

OQ-01 decided: "demonstrate a modest agreed load on a small budget and document the extrapolation". The profile below was confirmed by the project owner on 2026-10-01 (OQ-22):

| Metric | Verification target | Design target | Scale factor |
|---|---|---|---|
| Mixed API traffic | 300 RPS sustained 30 min, 600 RPS spike 5 min | 5K–10K TPS | about 1:17–1:33 |
| Order placement | 10 orders/s sustained (full saga with mock payment) | 60–120/s peak | about 1:6–1:12 |
| Location ingestion | 2,000 updates/s | 100K/s | 1:50 |
| WebSocket connections | 5,000 concurrent with 1,000 active trackers | 300K–500K | about 1:60–1:100 |
| Latency SLOs (NFR-PERF) | Same targets as design: p95 < 300 ms (core APIs), < 300 ms (search/menu), < 2 s (location visible to customer, event propagation p95 < 5 s) | Same | 1:1 |

**Environment:** on-demand staging (ADR-016), with node count and instance sizes recorded in the report.

**Extrapolation method:** for each component, measure throughput per pod or instance at the SLO (e.g. RPS per gateway pod at p95 < 300 ms, connections per realtime pod, inserts/s per database instance). The report then derives the pod and instance counts needed for design scale, plus the known non-linear risks (Kafka partition hot-spotting, database write ceilings, Redis single-thread limits per shard). It must state clearly that design scale was **not** demonstrated.

## 4. Load test scenarios (k6, ADR-014)

| Scenario | Mix |
|---|---|
| `browse` | Discovery 40%, menu 30%, search 20%, restaurant profile 10% |
| `order-flow` | Login (token reuse), cart add ×3, quote, order, payment (mock webhook), restaurant accept/ready (bot), partner flow (bot) |
| `tracking` | WebSocket subscribe + polling fallback, location ingestion bots (simulated partners moving on routes) |
| `spike` | 2× load for 5 min |
| `soak` | 60% load for 2 h (memory leaks, connection pool exhaustion) |
| `failure` | Kill a broker, a Redis replica or an order-service pod during `order-flow`; assert no lost orders and consistent end states |

Each scenario has k6 thresholds bound to NFR IDs. Results go to Grafana, and a report per run is stored as a CI artefact and linked in requirement evidence (REQ-QA-003).

## 5. Known scaling risks
| Risk | Mitigation |
|---|---|
| Hot partitions (a single viral restaurant) | Menu served from cache/CDN. Order events keyed by orderId, not branchId. |
| Kafka partition count fixed early | Generous initial counts (08 §2) |
| Location history volume at full scale | Active-only + down-sampling + external time-series store behind a port |
| WebSocket memory per connection | Tune buffers; test the connections-per-pod ceiling in Phase 21 |
| PostgreSQL connection explosion with many pods | PgBouncer; pool size limits per pod |
| LLM latency and cost spikes | Budgets, caching of repeated queries, fallback search |
