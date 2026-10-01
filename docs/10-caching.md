# 10 — Caching and Redis Usage

| Field | Value |
|---|---|
| Version | 1.0.0 |
| Status | **Approved** 2026-10-01 |
| Depends on | [ADR-004](17-adr/ADR-004-redis.md) |
| Requirements | REQ-MENU-004, REQ-RESTAURANT-003, REQ-SEARCH-001/002, REQ-PROMO-002, REQ-AUTH-002/005, REQ-SEC-001, REQ-LOCATION-001, REQ-DELIVERY-002/003, REQ-RT-001, NFR-PERF-001/002 |

**Principle:** cache only where it removes a measured or obvious hot path. Order state, payment state and wallet balance are **never** cached. They are always read from their source of truth (MP §19).

---

## 1. Deployments

| Logical instance | Policy | Persistence | Contents |
|---|---|---|---|
| `redis-cache` | `maxmemory-policy allkeys-lru` | None | Rebuildable caches (§2) |
| `redis-state` | `noeviction` | AOF `everysec` (production) | OTP, rate limits, login counters, revoked sessions, GEO, availability, locks, assignment schedule, Pub/Sub (§3) |

- **Local:** one Redis container, DB 0 for cache and DB 1 for state.
- **Production:** two ElastiCache replication groups (primary + replica, multi-AZ). Cluster mode is enabled for `redis-state` at design scale, so GEO and Pub/Sub keys use hash tags where multi-key operations are needed.
- **Key naming:** `<service>:<entity>:<id>[:<qualifier>]`. All keys have a TTL except GEO sets and long-lived state. Values are JSON (Jackson); sizes are monitored and kept below 512 KB per value (a full menu is typically 20–150 KB, compressed with LZ4 above 32 KB).

## 2. Cache catalogue (`redis-cache`)

| Key | Owner | Pattern | TTL | Invalidation | Notes |
|---|---|---|---|---|---|
| `menu:branch:{branchId}:v{menuVersion}` | menu | Cache-aside | 10 min | Version bump on every menu change; evict **after commit** (`TransactionalEventListener(AFTER_COMMIT)`) | The version in the key prevents stale reads during races. Clients also get an ETag. |
| `menu:product:{productId}` | menu | Cache-aside | 10 min | Evict after commit | Product detail |
| `restaurant:branch:{branchId}:status` | restaurant | Write-through | 5 min (safety) | Set on hours/toggle change and at schedule boundaries (job computes open/close transitions) | Read by cart, search and checkout |
| `restaurant:branch:{branchId}:profile` | restaurant | Cache-aside | 30 min | Evict on `BranchUpdated` | Public profile |
| `search:popular:{geohash5}:{queryHash}` | search | Cache-aside | 5–10 min | TTL only | Top query results per area (REF p.4) |
| `search:discovery:{geohash6}:{sort}:{filtersHash}` | search | Cache-aside | 60 s | TTL only | Nearby list; short TTL because open status changes |
| `promo:offers:branch:{branchId}` | promotion | Cache-aside | 10 min | Evict on `OfferUpdated` | Active offers list for cart pricing and badges |
| `user:prefs:{userId}` | user | Cache-aside | 30 min | Evict on update | Read by recommendation and AI |
| `rec:home:{userId}` | recommendation | Cache-aside | 15 min | Evict on `OrderDelivered` for the user | |
| `gw:jwks` | gateway, all services | In-memory (Caffeine) | 10 min | Refresh on unknown `kid` | Not Redis |

### 2.1 Stampede and consistency controls
- **Single-flight:** on a miss for hot keys (menu), a short lock `menu:lock:{branchId}` (`SET NX PX 3000`) lets one loader populate the cache while others wait 50 ms and retry (max 3 attempts), then fall through to the database.
- **TTL jitter:** ±10% on all TTLs to avoid synchronised expiry.
- **Evict after commit:** never evict before the transaction commits. Otherwise a concurrent reader could repopulate the old value.
- **Negative caching:** "not found" for product or branch IDs is cached for 30 s, to blunt enumeration and scrapers.
- **Two-level caching** (Caffeine in-process, 5–10 s TTL, plus Redis) is only for the extreme hot set (top branches' menus) and only if load testing shows Redis network cost dominates. It is off by default.

## 3. State catalogue (`redis-state`)

| Key | Owner | Type | TTL | Purpose |
|---|---|---|---|---|
| `otp:{purpose}:{phone}` | identity | Hash `{hmac, attempts}` | 5 min | OTP (hashed) |
| `otp:send:{phone}` / `otp:send:ip:{ip}` | identity | Counter | 10 min / 24 h | Send limits |
| `login:fail:{identifier}` | identity | Counter | 15 min | Lockout (REQ-AUTH-005) |
| `revoked_sessions:{sid}` | identity (write), gateway and realtime (read) | String | 15 min (= access TTL) | Immediate revocation |
| `rl:{bucket}:{key}` | gateway | Token bucket (Lua, Spring Cloud Gateway `RedisRateLimiter`) | Bucket window | Rate limiting |
| `partners:geo:{city}` | location | GEO set | None (members removed when offline or stale) | Latest partner positions; `GEOSEARCH` |
| `partner:loc:{partnerId}` | location | Hash `{lat,lng,ts,acc,speed,heading}` | 10 min | Latest point plus freshness |
| `partner:avail:{partnerId}` | delivery | Hash `{status, active, capacity, pendingOfferId, city}` | None (cleared on offline) | Assignment hot path |
| `assign:schedule` | delivery | Sorted set (score = notBefore) | — | Scheduled assignments |
| `assign:lock:{orderId}` | delivery | String NX PX | 10 s (renewed) | One worker per order |
| `assign:offered:{orderId}` | delivery | Set | 2 h | Never re-offer a partner |
| `assign:offer-deadlines` | delivery | Sorted set | — | 30 s offer expiry processing |
| `rt:active-delivery:{partnerId}` | realtime | String → orderId, customerId | 6 h | Location fan-out filter |
| `rt:user:{userId}` / `rt:branch:{branchId}` | realtime | Pub/Sub channels | — | Cross-instance WebSocket delivery |
| `idem:lock:{scope}:{key}` | order, payment | String NX | 30 s | Optional fast-path guard in front of the `idempotency_keys` table |
| `ai:budget:{userId}:{yyyymmdd}` | ai | Counter | 48 h | Daily token budget |

**GEO partitioning:** one GEO set per city keeps `GEOSEARCH` fast and lets cluster slots spread by city. The partner's city comes from their profile; a partner crossing a city boundary is moved by the ingestion service.

**Stale partner cleanup:** a job every 30 s removes GEO members whose `partner:loc` timestamp is older than 5 min and sets availability `OFFLINE` with reason `STALE_LOCATION` (REQ-DELIVERY-002).

## 4. Failure behaviour

| Redis outage affects | Behaviour | Rationale |
|---|---|---|
| Caches | Fall through to the database, protected by bulkheads; temporary latency increase; alert | Correctness unaffected |
| Rate limiting | **Fail open** for authenticated low-risk routes with local in-memory fallback limits; **fail closed** for OTP send and login | Availability for most routes; abuse-sensitive routes stay protected |
| OTP | OTP login unavailable (503 with a clear message); password login still works | Never weaken OTP checks |
| Revoked sessions | Gateway treats a lookup failure as not revoked, while the token TTL (15 min) still bounds exposure. Admin routes fail closed. | Balanced risk |
| GEO / availability | Assignment pauses; orders queue in `assign:schedule`, which is rebuilt from the `delivery_assignments` table on recovery; alert critical | Database is the durable record |
| Pub/Sub | Real-time pushes stop; clients fall back to polling (REQ-RT-001 AC4) | Graceful degradation |

## 5. Metrics
`cache_gets_total{cache,result=hit|miss}`, hit ratio per cache (target above 90% for the menu at steady state), `redis_command_duration_seconds`, memory used and evictions (alert on any eviction in `redis-state`), keyspace size per prefix (sampled).
