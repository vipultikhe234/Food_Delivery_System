# 06 — Database Design

| Field | Value |
|---|---|
| Version | 1.1.0 |
| Status | **Approved** 2026-10-01 (v1.1.0: Restaurant OS design) |
| Depends on | [ADR-003](17-adr/ADR-003-postgresql.md), [ADR-005](17-adr/ADR-005-outbox-pattern.md), [ADR-019](17-adr/ADR-019-tenant-isolation.md), [ADR-020](17-adr/ADR-020-inventory-ledger-costing.md), [05 Microservices](05-microservices.md) |
| Requirements | REQ-PLAT-006, REQ-PLAT-008, plus the `databaseRequirements` of every requirement |

This is the logical design. The physical DDL is written as Flyway migrations in each service during its implementation phase (`src/main/resources/db/migration/V<n>__<desc>.sql`). Column lists show the important columns. Base columns (§1.2) are implied for every table marked **[B]**.

v1.1.0 replaces §2.4 `menu_db` with `catalog_db`, extends §2.8 `order_db`, adds `pos_db`, `kitchen_db`, `inventory_db` and `procurement_db` (§2.17) and adds the tenant convention (§1.1). Their full models are in the [Restaurant OS designs](architecture/restaurant-os/README.md).

---

## 1. Conventions

### 1.1 General
| Topic | Rule |
|---|---|
| One database per service | `<service>_db`, owner role `<service>_owner` (runs migrations), runtime role `<service>_app` (DML only, no DDL). No cross-database foreign keys or queries. |
| Naming | `snake_case`, plural tables, `pk_<table>`, `fk_<table>_<ref>`, `uq_<table>_<cols>`, `ix_<table>_<cols>`, `ck_<table>_<rule>` |
| Primary key | `id UUID`, **UUIDv7** generated in the application (time-ordered, which gives good B-tree locality) |
| Timestamps | `timestamptz`, stored in UTC; displayed in IST by clients (NFR-I18N-001) |
| Money | `numeric(12,2)` plus `currency char(3)` (default `INR`); `BigDecimal` HALF_UP in Java; never `float` |
| Enums | `varchar(32)` plus a `CHECK` constraint. Avoids PostgreSQL enum migration pain; new values are an expand migration. |
| Soft delete | Only where the business needs restore or history (restaurants, products, addresses): `deleted_at timestamptz`, with partial unique indexes `WHERE deleted_at IS NULL`. Orders, payments and ledgers are never deleted. |
| JSON | `jsonb` only for genuinely schemaless data (event payloads, gateway raw responses, snapshots). Never for queryable core attributes. |
| Geography | PostGIS `geography(Point,4326)` with a GiST index; `ST_DWithin` for radius queries |
| Text search | Generated `tsvector` column with a GIN index, plus `pg_trgm` GIN for fuzzy matching (search_db) |
| Migrations | Flyway. **Expand → migrate → contract** across releases (NFR-DEPLOY-001). No destructive change in the same release that stops using a column. |
| Optimistic locking | `version bigint` and JPA `@Version` on every mutable aggregate root |
| PII | Phone and e-mail stored plain for lookup, but masked in logs. Government document numbers are encrypted at the application layer (AES-GCM, key from KMS) and stored as `bytea` plus a `last4` column. |
| Tenancy (v1.1.0) | Every tenant-owned table in catalog, order, pos, kitchen, inventory and procurement has `restaurant_id uuid NOT NULL` (the brand). Hibernate `@TenantId` filters and stamps it; PostgreSQL row-level security with `FORCE ROW LEVEL SECURITY` and policy `restaurant_id = current_setting('app.restaurant_id')::uuid` backs it up. `common-persistence` runs `SET LOCAL app.restaurant_id` per transaction. Only the `<service>_owner` role has `BYPASSRLS`, for migrations ([ADR-019](17-adr/ADR-019-tenant-isolation.md)). |

### 1.2 Base columns **[B]**
```text
id          uuid        PK
created_at  timestamptz NOT NULL DEFAULT now()
updated_at  timestamptz NOT NULL DEFAULT now()
created_by  uuid        NULL        -- user id or service principal id
updated_by  uuid        NULL
version     bigint      NOT NULL DEFAULT 0
```
These are filled by `common-persistence` (JPA auditing, with an `AuditorAware` that reads the security context).

### 1.3 Common technical tables (in every service that needs them)

```text
outbox_events                       -- ADR-005; every service that publishes events
  id uuid PK, aggregate_type varchar(64), aggregate_id uuid, event_type varchar(128),
  event_version int, topic varchar(128), partition_key varchar(128),
  payload jsonb, headers jsonb, created_at timestamptz,
  published_at timestamptz NULL, attempts int DEFAULT 0, last_error text NULL
  ix_outbox_unpublished (created_at) WHERE published_at IS NULL

processed_events                    -- idempotent consumers (REQ-PLAT-005)
  event_id uuid, consumer varchar(128), processed_at timestamptz
  PK (event_id, consumer)           -- purge rows older than 14 days

idempotency_keys                    -- order, payment, refund (REQ-PLAT-006)
  user_id uuid, idem_key varchar(128), endpoint varchar(128), request_hash char(64),
  status varchar(16) CHECK IN ('IN_PROGRESS','COMPLETED'), response_status int NULL,
  response_body jsonb NULL, created_at timestamptz, expires_at timestamptz
  PK (user_id, endpoint, idem_key)  -- same key + different hash → 422 IDEMPOTENCY_KEY_REUSED

shedlock                            -- scheduled job leader election
  name varchar(64) PK, lock_until timestamptz, locked_at timestamptz, locked_by varchar(255)
```

---

## 2. Per-service schemas

### 2.1 identity_db (identity-service)
```text
users [B]
  phone varchar(16) NULL, email citext NULL, password_hash varchar(255) NULL,
  status CHECK IN ('PENDING_VERIFICATION','ACTIVE','LOCKED','BLOCKED','DELETED'),
  phone_verified_at, email_verified_at, failed_login_count int, locked_until timestamptz,
  last_login_at
  uq_users_phone (phone) WHERE phone IS NOT NULL
  uq_users_email (email) WHERE email IS NOT NULL
  ck_users_identifier: phone IS NOT NULL OR email IS NOT NULL

roles [B]               code varchar(64) UNIQUE (CUSTOMER, RESTAURANT_OWNER, RESTAURANT_MANAGER,
                        DELIVERY_PARTNER, SUPPORT_AGENT, ADMIN, SUPER_ADMIN; REQ-AUTH-003 AC1), description, system bool
permissions [B]         code varchar(64) UNIQUE, description, category
role_permissions        role_id FK, permission_id FK, PK(role_id, permission_id)
user_roles [B]          user_id FK, role_id FK, scope_type CHECK IN ('GLOBAL','RESTAURANT','BRANCH'),
                        scope_id uuid NULL, granted_by uuid
                        uq_user_roles (user_id, role_id, scope_type, coalesce(scope_id, '00000000-...'))

refresh_tokens
  id uuid PK, user_id FK, family_id uuid, token_hash char(64) UNIQUE,
  session_id uuid, device_info varchar(255), ip inet,
  issued_at, expires_at, rotated_at NULL, revoked_at NULL,
  revoke_reason CHECK IN ('LOGOUT','LOGOUT_ALL','REUSE_DETECTED','PASSWORD_RESET','BLOCKED','ADMIN')
  ix_refresh_tokens_family (family_id), ix_refresh_tokens_user (user_id) WHERE revoked_at IS NULL

password_reset_tokens   id, user_id FK, token_hash UNIQUE, expires_at, used_at NULL
signing_keys            kid varchar(64) PK, public_jwk jsonb, status CHECK IN ('ACTIVE','RETIRING','RETIRED'),
                        created_at, retire_after        -- private keys live in the secret manager, never in the DB
```
OTPs live in Redis only, as `HMAC(otp)` with a TTL (§ 10-caching).

### 2.2 user_db (user-service)
```text
user_profiles [B]       user_id uuid UNIQUE (= identity users.id), full_name, display_name, avatar_media_id,
                        date_of_birth NULL, gender NULL, locale default 'en-IN'
addresses [B]           user_id, label CHECK IN ('HOME','WORK','OTHER'), line1, line2, landmark, city, state,
                        pincode char(6), location geography(Point,4326) NOT NULL, is_default bool, deleted_at
                        ix_addresses_user (user_id) WHERE deleted_at IS NULL
                        uq_addresses_default (user_id) WHERE is_default AND deleted_at IS NULL
user_preferences [B]    user_id UNIQUE, diet CHECK IN ('VEG','NON_VEG','EGG','VEGAN','ANY'),
                        cuisines text[], spice_level smallint CHECK 0..3, allergens text[], budget_per_meal numeric(12,2)
user_devices [B]        user_id, platform CHECK IN ('ANDROID','IOS','WEB'), push_token varchar(512),
                        app_version, last_seen_at; uq (push_token)
favorites               user_id, target_type CHECK IN ('RESTAURANT','PRODUCT'), target_id, created_at;
                        PK (user_id, target_type, target_id)
```

### 2.3 restaurant_db (restaurant-service)
```text
restaurants [B]         owner_user_id, legal_name, brand_name, description, cuisines text[],
                        logo_media_id, cover_media_id, fssai_license_no, gstin, pan_last4, pan_enc bytea,
                        status CHECK IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','SUSPENDED'),
                        rejection_reason, approved_at, approved_by, rating_avg numeric(2,1), rating_count int, deleted_at
                        uq_restaurants_fssai (fssai_license_no) WHERE deleted_at IS NULL
restaurant_branches [B] restaurant_id FK, name, address fields, location geography NOT NULL,
                        delivery_radius_km numeric(4,1) CHECK 0.5..15, avg_prep_minutes smallint,
                        min_order_value numeric(12,2), packaging_charge numeric(12,2),
                        accepting_orders bool, manual_status CHECK IN ('OPEN','CLOSED','BUSY') NULL,
                        city varchar(64), deleted_at
                        ix_branches_location GIST (location)
restaurant_hours        branch_id FK, day_of_week smallint 1..7, opens_at time, closes_at time, PK(branch_id, day_of_week, opens_at)
                        -- multiple slots per day; overnight slots allowed (closes_at < opens_at)
restaurant_hour_overrides [B] branch_id, date, closed bool, opens_at NULL, closes_at NULL, reason
restaurant_documents [B] restaurant_id, doc_type CHECK IN ('FSSAI','GST','PAN','BANK','MENU','OTHER'),
                        media_id (private), status CHECK IN ('UPLOADED','VERIFIED','REJECTED'), reviewer_id, notes
restaurant_settings [B] restaurant_id UNIQUE, auto_accept bool, cod_enabled bool, notification_prefs jsonb
restaurant_status_history  id, restaurant_id, from_status, to_status, actor_id, reason, created_at
restaurant_staff [B]    restaurant_id, user_id, role CHECK IN ('MANAGER'), branch_ids uuid[] NOT NULL, active bool,
                        revoked_at NULL; uq (restaurant_id, user_id)     -- REQ-RESTAURANT-004 (managers scoped to branches)
staff_invitations [B]   restaurant_id, branch_ids uuid[], phone_or_email, role, token_hash, expires_at, accepted_at
```

### 2.4 catalog_db (catalog-service; was menu_db)
v1.1.0: this sketch is superseded by the `catalog_db` model in [CATALOG_ARCHITECTURE §2](architecture/restaurant-os/CATALOG_ARCHITECTURE.md#2-data-model-catalog_db) (master products, combos, tax classes, price layers, menus, channels, schedules, menu items, published menu versions). The original sketch is kept below for history; it was never implemented.
```text
categories [B]          branch_id, name, sort_order, active bool, deleted_at
products [B]            branch_id, category_id FK, name, description, food_type CHECK IN ('VEG','NON_VEG','EGG'),
                        base_price numeric(12,2) CHECK >= 0, tax_category varchar(32), prep_minutes smallint,
                        tags text[], spice_level smallint, is_recommended bool, sort_order,
                        rating_avg, rating_count, deleted_at
                        ix_products_branch_category (branch_id, category_id) WHERE deleted_at IS NULL
product_variants [B]    product_id FK, name, price numeric(12,2), is_default bool, sort_order
addon_groups [B]        branch_id, name, min_select smallint, max_select smallint, CHECK (min_select <= max_select)
product_addon_groups    product_id FK, addon_group_id FK, sort_order, PK(product_id, addon_group_id)
addons [B]              addon_group_id FK, name, price numeric(12,2), food_type, active bool
product_images          product_id FK, media_id, sort_order, PK(product_id, media_id)
product_availability [B] product_id UNIQUE, available bool, track_stock bool, stock_qty int NULL CHECK (stock_qty >= 0),
                        available_from time NULL, available_to time NULL, unavailable_until timestamptz NULL
menu_versions           branch_id PK, version bigint     -- bumped on every menu change; part of the cache key
```

### 2.5 media_db (media-service)
```text
media_assets [B]        owner_id, owner_type, purpose CHECK IN ('RESTAURANT_LOGO','RESTAURANT_COVER','PRODUCT_IMAGE',
                        'REVIEW_IMAGE','AVATAR','DOCUMENT','DELIVERY_PROOF'), visibility CHECK IN ('PUBLIC','PRIVATE'),
                        bucket, object_key, content_type, size_bytes, checksum_sha256,
                        status CHECK IN ('PENDING_UPLOAD','SCANNING','READY','REJECTED'), rejection_reason,
                        variants jsonb   -- {thumb:{key,w,h}, medium:{...}}
```
Images are stored in object storage, never in PostgreSQL (MP rule).

### 2.6 cart_db (cart-service)
```text
carts [B]               user_id UNIQUE (active cart), branch_id, coupon_code NULL, address_id NULL,
                        last_activity_at, expires_at   -- 24 h (OQ-10)
cart_items [B]          cart_id FK, product_id, variant_id NULL, quantity smallint CHECK 1..50,
                        unit_price_snapshot numeric(12,2), special_instructions varchar(250)
cart_item_addons        cart_item_id FK, addon_id, price_snapshot, PK(cart_item_id, addon_id)
price_quotes            id uuid PK, user_id, cart_id, branch_id, address_id, lines jsonb, item_total, packaging_fee,
                        delivery_fee, platform_fee, tax_total, discount_total, grand_total, currency,
                        coupon_code, signature char(64), expires_at, consumed_by_order_id NULL, created_at
                        -- a quote can be consumed once (uq on consumed_by_order_id)
```

### 2.7 promotion_db (promotion-service)
```text
offers [B]              code varchar(32) NULL (NULL = automatic offer), title, description,
                        type CHECK IN ('PERCENT','FLAT','FREE_DELIVERY'), value numeric(12,2), max_discount numeric(12,2),
                        min_order_value, starts_at, ends_at, max_redemptions int NULL, redeemed_count int DEFAULT 0,
                        per_user_limit smallint DEFAULT 1, funded_by CHECK IN ('PLATFORM','RESTAURANT'),
                        status CHECK IN ('DRAFT','ACTIVE','PAUSED','EXPIRED'), current_version int
                        uq_offers_code (upper(code)) WHERE code IS NOT NULL
                        ck_offers_redemptions: max_redemptions IS NULL OR redeemed_count <= max_redemptions
offer_versions          offer_id, version, snapshot jsonb, changed_by, created_at, PK(offer_id, version)
offer_applicability     offer_id, scope_type CHECK IN ('ALL','RESTAURANT','BRANCH','CITY','FIRST_ORDER','USER_SEGMENT'),
                        scope_id NULL
offer_redemptions [B]   offer_id, user_id, order_id UNIQUE, status CHECK IN ('RESERVED','COMMITTED','RELEASED'),
                        discount_amount, reserved_until
                        ix (offer_id, user_id) WHERE status <> 'RELEASED'   -- per-user limit counted here
abuse_flags [B]         user_id, device_fingerprint, reason, offer_id, status CHECK IN ('OPEN','CLEARED','CONFIRMED')
```

### 2.8 order_db (order-service)
```text
orders [B]
  order_number varchar(20) UNIQUE        -- human readable e.g. FD-20261001-7K3Q9
  customer_id, restaurant_id, branch_id, quote_id UNIQUE,
  status (16 states, CHECK), closed bool DEFAULT false,
  payment_method CHECK IN ('ONLINE','COD','WALLET'), payment_status CHECK IN ('NONE','PENDING','PAID','FAILED','REFUND_PENDING','PARTIALLY_REFUNDED','REFUNDED'),
  item_total, packaging_fee, delivery_fee, platform_fee, tax_total, discount_total, grand_total, currency,
  paid_amount numeric(12,2) DEFAULT 0, refunded_amount numeric(12,2) DEFAULT 0,
  coupon_code NULL, prep_time_minutes NULL, estimated_ready_at NULL, ready_at NULL,
  delivery_partner_id NULL, partner_assigned_at NULL,      -- IMPACT-0001
  estimated_delivery_at NULL, delivered_at NULL,
  cancel_reason_code NULL, cancel_reason_text NULL, cancelled_by_type NULL,
  delivery_otp_hash NULL
  ck_orders_refund: refunded_amount <= paid_amount
  ix_orders_customer_created (customer_id, created_at DESC)
  ix_orders_branch_status (branch_id, status) WHERE closed = false
  ix_orders_status_updated (status, updated_at) WHERE closed = false     -- admin, timeout jobs
order_items [B]         order_id FK, product_id, variant_id, name_snapshot, variant_name_snapshot, food_type,
                        unit_price, quantity, line_total, special_instructions
order_item_addons       order_item_id FK, addon_id, name_snapshot, price
order_addresses         order_id PK FK, line1, line2, landmark, city, pincode, location geography, contact_name, contact_phone
order_status_history    id uuid, order_id, from_status, to_status, actor_type, actor_id, reason, correlation_id, created_at
                        ix (order_id, created_at)
saga_state [B]          order_id UNIQUE, step CHECK IN ('PAYMENT','RESTAURANT_ACCEPT','READY','ASSIGNMENT','REFUND','DONE'),
                        deadline_at NULL, attempts int, last_error
                        ix_saga_deadline (deadline_at) WHERE step <> 'DONE'
```
Order rows are retained indefinitely (financial records, minimum 8 years under Indian accounting rules, subject to legal review). Old orders move to a partition or archive after 2 years (§3).

v1.1.0 changes ([ORDER_ARCHITECTURE §6](architecture/restaurant-os/ORDER_ARCHITECTURE.md#6-data-model-changes-order_db)): `orders` gains `order_source`, `order_type`, `channel`, `table_session_id`, `table_label`, `qr_guest_id`, `device_id`, `menu_version`, `business_date`, `locked`, `settled_at`, `served_at`, `handed_over_at`, `completed_at`; `payment_method` adds `BILL`; `customer_id` and `quote_id` become nullable; status adds `SERVED`, `HANDED_OVER`, `COMPLETED`. `order_items` gains rounds, fire status, voided quantity, station and tax snapshots and combo child lines. New tables `order_rounds`, `order_contacts` and the projection `table_sessions_view`. Trigger `fn_guard_locked_order()` rejects changes to locked orders (REQ-ORDER-009).

### 2.9 payment_db (payment-service)
```text
payments [B]            order_id, customer_id, method CHECK IN ('UPI','CARD','NETBANKING','WALLET','COD'),
                        provider CHECK IN ('RAZORPAY','MOCK','WALLET','COD'), provider_order_id UNIQUE NULL,
                        amount, currency, status CHECK IN ('CREATED','PENDING','CAPTURED','FAILED','CANCELLED','COD_PENDING','COLLECTED'),
                        captured_amount DEFAULT 0, refunded_amount DEFAULT 0, failure_code, failure_reason
                        ck_payments_refund: refunded_amount <= captured_amount
                        uq_payments_order_active (order_id) WHERE status IN ('CREATED','PENDING','CAPTURED','COD_PENDING','COLLECTED')
payment_transactions [B] payment_id FK, type CHECK IN ('AUTH','CAPTURE','REFUND','VOID'), provider_txn_id UNIQUE,
                        amount, status, raw_response jsonb (sanitised: no card data)
payment_webhook_events  id uuid, provider, provider_event_id UNIQUE, event_type, signature_valid bool,
                        payload jsonb, received_at, processed_at NULL, processing_error NULL
refunds [B]             payment_id FK, order_id, amount CHECK > 0, destination CHECK IN ('ORIGINAL','WALLET'),
                        reason_code, reason_text, requested_by, provider_refund_id UNIQUE NULL,
                        status CHECK IN ('PENDING','PROCESSING','COMPLETED','FAILED'), idempotency_key UNIQUE
wallets [B]             user_id UNIQUE, balance numeric(12,2) DEFAULT 0, currency, status
                        ck_wallets_balance: balance >= 0
wallet_transactions     id uuid, wallet_id FK, type CHECK IN ('CREDIT','DEBIT'), amount CHECK > 0,
                        balance_after numeric(12,2), reason CHECK IN ('REFUND','CASHBACK','ORDER_PAYMENT','ADJUSTMENT'),
                        reference_type, reference_id, created_at, created_by
                        uq (reference_type, reference_id, type)       -- no duplicate credit for the same refund
reconciliation_reports [B] run_date, provider, matched int, mismatched int, details jsonb, status
cod_collections [B]     payment_id UNIQUE, partner_id, amount, collected_at
```
No PAN, CVV or card numbers are ever stored (NFR-SEC-003); hosted checkout returns tokens and IDs only.

### 2.10 delivery_db (delivery-service)
```text
delivery_partners [B]   user_id UNIQUE, full_name, phone, vehicle_type CHECK IN ('BICYCLE','SCOOTER','MOTORCYCLE','CAR'),
                        vehicle_number, city, verification_status CHECK IN ('PENDING','VERIFIED','REJECTED','SUSPENDED'),
                        availability CHECK IN ('OFFLINE','ONLINE','BREAK','ON_DELIVERY','SUSPENDED'),
                        capacity smallint DEFAULT 1, rating_avg, rating_count,
                        acceptance_rate_30d numeric(4,3), offers_30d int
partner_documents [B]   partner_id, doc_type CHECK IN ('DRIVING_LICENSE','AADHAAR','PAN','VEHICLE_RC','PHOTO','BANK'),
                        media_id, number_last4, number_enc bytea, status, reviewer_id
partner_status_history  id, partner_id, from_status, to_status, reason, created_at  -- partitioned monthly
delivery_assignments [B] order_id UNIQUE, branch_id, partner_id NULL,
                        status CHECK IN ('SCHEDULED','SEARCHING','ASSIGNED','AT_RESTAURANT','PICKED_UP','OUT_FOR_DELIVERY',
                        'ARRIVED','DELIVERED','FAILED','CANCELLED','UNASSIGNED_FAILED'),
                        not_before, assigned_at, picked_up_at, delivered_at, failure_reason,
                        pickup_location geography, drop_location geography, distance_km, proof_media_id
assignment_offers [B]   assignment_id FK, order_id, partner_id, round smallint,
                        status CHECK IN ('OFFERED','ACCEPTED','REJECTED','EXPIRED','WITHDRAWN'), expires_at, responded_at
                        uq (order_id, partner_id)                -- never offer the same order twice
assignment_decisions    id, assignment_id, round, radius_km, candidates jsonb, filtered jsonb, created_at
delivery_status_history id, assignment_id, from_status, to_status, actor_id, location geography NULL, created_at
partner_earnings [B]    partner_id, order_id UNIQUE, base_fee, distance_fee, incentive, tip, total, cod_collected, earned_on date
                        ix (partner_id, earned_on)
```

### 2.11 location_db (location-service)
```text
delivery_locations                      -- PARTITION BY RANGE (recorded_at), daily partitions (pg_partman)
  partner_id uuid, recorded_at timestamptz, received_at timestamptz, location geography(Point,4326),
  accuracy_m real, speed_mps real, heading smallint, order_id uuid NULL
  PK (partner_id, recorded_at)
  -- retention 90 days (OQ-20): drop partitions; optional archive to object storage (Parquet) first
eta_predictions                         -- for ETA accuracy evaluation
  id, order_id, kind CHECK IN ('PICKUP','DROP','TOTAL'), predicted_seconds, predicted_at, actual_seconds NULL
```
**Volume note:** at design scale (100K updates/s) raw history would be about 8.6 billion rows/day. That is not viable in a single PostgreSQL. Design response (also in [11](11-scalability.md)):
1. Persist history **only for partners on an active delivery**, not for idle online partners.
2. Down-sample to one point every 15 s for storage (the live stream stays at 5–10 s).
3. Batch insert with `COPY`.
4. At design scale, move history to a time-series or columnar store (TimescaleDB or object storage + Athena) behind the `LocationHistoryStore` port.

At the verified portfolio load this table is fine in PostgreSQL.

### 2.12 notification_db (notification-service)
```text
notification_templates [B] code, channel CHECK IN ('PUSH','SMS','EMAIL','IN_APP'), locale, version, subject, body,
                        active bool; uq (code, channel, locale, version)
notifications [B]       user_id, category CHECK IN ('TRANSACTIONAL','ORDER','PROMOTIONAL','SYSTEM'), title, body,
                        data jsonb, read_at NULL, source_event_id      -- in-app inbox; retention 90 days
                        ix (user_id, created_at DESC)
notification_deliveries [B] notification_id NULL, user_id, channel, template_code, dedupe_key UNIQUE,
                        provider, provider_message_id, status CHECK IN ('PENDING','SENT','DELIVERED','FAILED','SKIPPED'),
                        attempts, last_error, next_attempt_at
notification_preferences [B] user_id UNIQUE, push bool, sms bool, email bool, promotional bool, quiet_hours jsonb
```

### 2.13 review_db (review-service)
```text
reviews [B]             order_id, customer_id, target_type CHECK IN ('RESTAURANT','PRODUCT','DELIVERY'), target_id,
                        rating smallint CHECK 1..5, comment varchar(1000), status CHECK IN ('PUBLISHED','HIDDEN','PENDING_MODERATION'),
                        moderation_reason; uq (order_id, target_type, target_id)
review_images           review_id FK, media_id, PK(review_id, media_id)
review_replies [B]      review_id UNIQUE, author_user_id, body varchar(1000)
rating_aggregates       target_type, target_id, rating_sum bigint, rating_count int, avg numeric(2,1),
                        dist int[5], updated_at, PK(target_type, target_id)
```
The brief's separate restaurant_reviews, product_reviews and ratings tables are modelled as one `reviews` table discriminated by `target_type`. That gives one moderation flow and one uniqueness rule.

### 2.14 search_db (search-service)
```text
restaurant_search_docs  branch_id PK, restaurant_id, name, cuisines text[], tags text[], location geography,
                        delivery_radius_km, is_open bool, accepting_orders bool, rating_avg, rating_count,
                        avg_prep_minutes, price_for_two, offer_badges text[], veg_only bool, hours jsonb,
                        search_vector tsvector GENERATED, updated_at, source_version bigint
                        GIST(location), GIN(search_vector), GIN(name gin_trgm_ops), GIN(cuisines)
dish_search_docs        product_id PK, branch_id, restaurant_id, name, description, category, food_type, price,
                        available bool, rating_avg, location geography, search_vector tsvector GENERATED
                        GIST(location), GIN(search_vector), GIN(name gin_trgm_ops)
```
Each document stores `source_version`. An event older than the stored version is ignored, which makes out-of-order delivery safe.

### 2.15 recommendation_db, ai_db, admin_db, analytics_db, audit_db
```text
-- recommendation_db
user_order_features     user_id PK, top_cuisines jsonb, top_products jsonb, avg_order_value, order_hours int[24], last_order_at
item_popularity         area_geohash5, product_id, branch_id, time_bucket CHECK IN ('BREAKFAST','LUNCH','SNACKS','DINNER','LATE'),
                        score numeric, PK(area_geohash5, product_id, time_bucket)

-- ai_db (retention 30 days for sessions/messages per OQ-20)
assistant_sessions [B]  user_id, channel, status, expires_at
assistant_messages      id, session_id, role CHECK IN ('USER','ASSISTANT','TOOL'), content text (PII-minimised), created_at
tool_invocations        id, session_id, tool_name, arguments jsonb, requires_confirmation bool,
                        confirmed_at NULL, status, result_summary jsonb, latency_ms, created_at
usage_ledger            id, session_id, user_id, provider, model, prompt_tokens, completion_tokens, cost_usd numeric(10,6), latency_ms, created_at

-- admin_db
complaints [B]          ticket_number UNIQUE, customer_id, order_id NULL, category, priority, status CHECK IN
                        ('OPEN','IN_PROGRESS','WAITING_CUSTOMER','RESOLVED','CLOSED'), assignee_id, sla_due_at,
                        resolution_type CHECK IN ('NONE','REFUND','COUPON','APOLOGY','ESCALATED') NULL, resolution_note
complaint_messages [B]  complaint_id FK, author_id, author_type, body, attachments uuid[]

-- analytics_db (partitioned by day)
fact_orders             order_id PK, order_date, city, restaurant_id, branch_id, customer_id, status, grand_total,
                        discount_total, created_at, delivered_at, delivery_minutes
fact_payments, fact_deliveries   (similar)
daily_restaurant_kpis   date, restaurant_id, orders, gmv, cancellations, avg_rating, PK(date, restaurant_id)
daily_platform_kpis     date, city, orders, gmv, aov, active_users, PK(date, city)

-- audit_db (append-only; retention 7 years per OQ-20)
audit_logs                              -- PARTITION BY RANGE (occurred_at) monthly
  id uuid, occurred_at, actor_id, actor_type, actor_ip inet, action varchar(64), entity_type, entity_id,
  before jsonb NULL, after jsonb NULL (sensitive fields masked), reason, correlation_id,
  prev_hash char(64), hash char(64)     -- hash = SHA-256(prev_hash || canonical(row)); integrity check job
  PK (id, occurred_at)
  -- runtime role: INSERT + SELECT only
```

### 2.16 requirement_db (Phase 18; designed then)
Imports `requirements.json`: requirements, requirement_versions, status_history, acceptance_criteria, traceability_links (requirement ↔ code/test/PR), test_evidence, releases, release_requirements.

### 2.17 Restaurant OS databases (v1.1.0)

| Database | Service | Model |
|---|---|---|
| `pos_db` | pos-service | [POS_ARCHITECTURE §2](architecture/restaurant-os/POS_ARCHITECTURE.md#2-data-model-pos_db): areas, tables, sessions, held orders, QR codes and sessions, bills, invoices and counters, credit notes, settings |
| `kitchen_db` | kitchen-service | [KITCHEN_ARCHITECTURE §3](architecture/restaurant-os/KITCHEN_ARCHITECTURE.md#3-data-model-kitchen_db): stations, displays, printers, KOTs, KOT items and events, KOT counters, roll-up |
| `inventory_db` | inventory-service | [INVENTORY_ARCHITECTURE §4](architecture/restaurant-os/INVENTORY_ARCHITECTURE.md#4-data-model-inventory_db): stock items, units, locations, balances, batches, movement ledger, counts, transfers, production, recipes ([ADR-020](17-adr/ADR-020-inventory-ledger-costing.md)) |
| `procurement_db` | procurement-service | [INVENTORY_ARCHITECTURE §13.1](architecture/restaurant-os/INVENTORY_ARCHITECTURE.md#131-data-model-procurement_db): suppliers, purchase orders, goods receipts, invoices, returns |

restaurant_db gains locations (warehouses, central kitchens) and the device registry; identity_db gains device credentials and Argon2id staff PIN hashes; payment_db gains in-store payment records with `reference_type` `ORDER`/`BILL` (REQ-PAYMENT-006). Details in [restaurant-os README §1](architecture/restaurant-os/README.md#1-service-boundaries-ros-oq-01-adr-018).

---

## 3. Data lifecycle and retention

| Data | Retention | Mechanism |
|---|---|---|
| Orders, payments, refunds, wallet ledger | ≥ 8 years (financial; legal review pending) | Never deleted. Partitioned or archived after 2 years. |
| Audit logs | 7 years (OQ-20) | Monthly partitions, detached to cold storage after 1 year |
| Location history | 90 days (OQ-20) | Drop daily partitions |
| AI sessions and messages | 30 days (OQ-20) | Nightly purge job |
| Notifications (inbox) | 90 days (OQ-20) | Nightly purge |
| `processed_events` | 14 days | Nightly purge |
| `outbox_events` (published) | 7 days | Nightly purge |
| `idempotency_keys` | 24 h | `expires_at` purge |
| Account deletion (privacy, REQ-SEC-003) | — | Personal data anonymised in user, identity and review records. Order records are kept with the customer reference pseudonymised. |

## 4. Migration and seeding rules
- Each service's migrations run at startup under the owner role in development. In staging and production they run as a **separate Kubernetes Job** before rollout (13-deployment.md).
- Reference data (roles, permissions, notification templates) is seeded by versioned migrations (`R__` repeatable migrations only for idempotent reference data).
- Demo and test data are never in migrations. They live in `infrastructure/docker/seed/` scripts, used locally and in staging only.

## 5. Capacity notes (design scale, NFR-SCALE-001)
| Table | Growth at design scale | Approach |
|---|---|---|
| orders | 1M/day → about 365M/year | Index on `(customer_id, created_at)`; partition by month once above about 100M rows; archive after 2 years |
| order_status_history | about 10M/day | Monthly partitions |
| outbox_events | Equal to the event rate, purged | Partial index on unpublished rows |
| delivery_locations | See §2.11 | Active-delivery only, down-sampled, external store at scale |
| audit_logs | About 2–5M/day | Monthly partitions |
| notifications | About 5M/day | Partition by month; 90-day retention |
