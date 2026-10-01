# 09 — Security

| Field | Value |
|---|---|
| Version | 1.0.0 |
| Status | **Approved** 2026-10-01 |
| Depends on | [ADR-008](17-adr/ADR-008-jwt-tokens.md), [07 API](07-api-design.md) |
| Requirements | REQ-AUTH-001..005, REQ-SEC-001..003, REQ-AUDIT-001, REQ-PAYMENT-002, REQ-AI-002/003, REQ-DEVAGENT-002, REQ-QA-004, NFR-SEC-001..003 |
| Reference standard | OWASP ASVS 4.x Level 2 (NFR-SEC-001, proposed), OWASP Top 10 2021, OWASP API Security Top 10 2023 |

> **Pending change (Proposed 2026-10-01):** the Restaurant OS design adds roles CASHIER, CAPTAIN, KITCHEN_STAFF and INVENTORY_MANAGER, new permissions, device credentials, staff PINs, QR guest tokens and tenant isolation ([restaurant-os README §4–5](architecture/restaurant-os/README.md), [ADR-019](17-adr/ADR-019-tenant-isolation.md)). This document is updated when that design is approved.

---

## 1. Threat model (summary)

Assets, from highest value: payment and refund flows and the wallet ledger; customer PII (phone, addresses, location history); partner KYC documents; account takeover (especially admin); restaurant revenue data; platform availability.

| STRIDE | Main threats | Key controls |
|---|---|---|
| **S**poofing | Credential stuffing, OTP brute force, stolen refresh tokens, forged webhooks | Rate limits + lockout (REQ-AUTH-005), OTP attempt limits, refresh token rotation with reuse detection, HMAC webhook verification, MFA for admin (§2.4) |
| **T**ampering | Client-side price or amount manipulation, quote tampering, mass assignment, forged events | Server-side pricing, HMAC-signed quotes, payment amount taken from the order, strict DTOs (unknown fields rejected), Kafka ACLs per service principal |
| **R**epudiation | Disputed refunds and cancellations, admin abuse | Hash-chained append-only audit log with actor, reason and correlation ID |
| **I**nformation disclosure | IDOR on orders and addresses, PII in logs, stack traces, partner seeing customer data after delivery | Ownership checks, 404-on-foreign-resource, log masking, Problem Details without internals, time-boxed data visibility (REQ-SEC-003 AC2) |
| **D**enial of service | API floods, OTP SMS pumping (toll fraud), WebSocket connection floods, expensive search queries, LLM cost exhaustion | Gateway rate limits, OTP per-phone and per-IP limits plus country restriction, connection limits per user, query limits, AI token budgets, WAF at the edge |
| **E**levation of privilege | Role self-assignment, manager to owner, scope bypass, prompt injection triggering tools | Only SUPER_ADMIN grants ADMIN/SUPER_ADMIN, scope checks server-side, AI tools run with the user's own JWT plus an allow-list plus confirmation |

A full threat model per flow (order and payment, location, AI) is maintained with each phase's design review and re-checked in Phase 22 (REQ-QA-004).

---

## 2. Authentication (REQ-AUTH-001..005)

### 2.1 Credentials
| Item | Design |
|---|---|
| Customer sign-up | Phone + OTP (primary). E-mail + password optional. |
| Restaurant / admin / support | E-mail + password + OTP second factor (admin MFA, §2.4) |
| Partner | Phone + OTP |
| Password hashing | **Argon2id** (memory 19 MiB, iterations 2, parallelism 1; OWASP minimum), via Spring Security `Argon2PasswordEncoder`. Re-hash on login if parameters change. |
| Password policy | Minimum 10 characters, checked against a breached-password list (k-anonymity range API or a bundled top-100K list), no composition rules (NIST 800-63B) |
| OTP | 6 digits, CSPRNG, TTL 5 min, stored as `HMAC-SHA256(secret, phone|otp)` in Redis. Max 5 verify attempts per OTP. Send limited to 3 per 10 min and 10 per day per phone, plus per-IP limits. Constant-time compare. |
| Lockout | 5 failures in 15 min locks login for 15 min (REQ-AUTH-005 AC1). Unlock is possible via OTP to prevent lockout-as-DoS. Every lockout is audited. |
| Enumeration | Login and forgot-password return identical responses and timing for unknown accounts |

### 2.2 Tokens (ADR-008)
- **Access JWT (RS256, 15 min):**
  - Claims: `iss`, `aud=fooddelivery-api`, `sub`, `sid`, `roles`, `perms`, `scopes` (`[{type:"RESTAURANT", id}, {type:"BRANCH", id}]`), `iat`, `exp`, `jti`. Signing key ID in the JWT header (`kid`).
  - Size budget: under 4 KB. Owners of very many branches get `scopes` collapsed to the restaurant level, and branch checks resolve through restaurant-service.
- **Refresh token (opaque, 30 days, rotate on every use):** reuse of an already-rotated token revokes the family and all sessions of that `sid`, and is audited as `REFRESH_TOKEN_REUSED`.
- **Web:** refresh cookie `__Host-rt` (`HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`). Access token in memory only (not `localStorage`). CSRF uses the double-submit token on `/auth/refresh` and `/auth/logout`.
- **Mobile:** refresh token in Keychain/Keystore via secure storage. Optional biometric unlock gates local use.
- **Immediate revocation:** on block, role revocation, password reset and logout-all, `revoked_sessions:{sid}` is written to Redis (TTL 15 min). The gateway and realtime-service check it, and realtime-service also disconnects live sockets.
- **Service tokens:** client-credentials grant at identity-service, `aud=internal`, scope `svc:<name>`, TTL 5 min, cached by the caller.

### 2.3 Key management and rotation
| Key | Store | Rotation |
|---|---|---|
| JWT signing keypair | Secret manager (private); JWKS (public) | Every 90 days. A new key is published in JWKS 24 h before it signs; the old key stays in JWKS for 24 h after it stops signing. |
| Quote HMAC key | Secret manager | 90 days; two active keys during overlap (key ID in the quote record) |
| OTP HMAC secret | Secret manager | 90 days (OTPs are short-lived, so a simple swap works) |
| Field encryption key (KYC numbers) | KMS (envelope encryption) | Annual. Data keys are re-wrapped and data is not re-encrypted unless the key is compromised. |
| Razorpay key secret and webhook secret | Secret manager | On staff change or compromise, and at least yearly |
| Database and Redis passwords | Secret manager + External Secrets | 90 days; dual-user rotation |

The rotation runbook is in `docs/runbooks/key-rotation.md` (Phase 23).

### 2.4 Admin protections
- MFA (OTP) is mandatory for ADMIN, SUPER_ADMIN and SUPPORT_AGENT.
- Admin sessions have shorter refresh TTLs (8 h) and an idle timeout of 30 min on web.
- Optional IP allow-list for the admin dashboard in production (configuration).
- Sensitive admin actions (refund above threshold, role grant, user block) require a reason and are audited. Refunds above the threshold need SUPER_ADMIN approval (REQ-ADMIN-002 AC2).

---

## 3. Authorisation (REQ-AUTH-003)

### 3.1 Model
**Deny by default.** A request is allowed only if all three layers pass:

1. **Gateway:** the route is public, or a JWT is valid and its session is not revoked.
2. **Permission:** `@PreAuthorize("hasAuthority('<PERMISSION>')")` on every controller method. A CI test fails if a controller method lacks a security annotation (ArchUnit rule).
3. **Ownership/scope:** checked in the application service by `AccessPolicy` components from `common-security`, e.g. `orderAccess.canView(principal, order)`. These are never left to the controller alone.

Role-to-permission mapping is **data** in identity_db (REQ-AUTH-003 AC2). Permissions are embedded in the JWT at issue time.

### 3.2 Permission catalogue
| Category | Permissions |
|---|---|
| Orders | `ORDER_CREATE`, `ORDER_VIEW`, `ORDER_VIEW_ALL`, `ORDER_CANCEL`, `ORDER_CANCEL_ANY`, `ORDER_MANAGE` (restaurant accept/reject/prepare/ready), `ORDER_REFUND` |
| Cart & reviews | `CART_MANAGE`, `REVIEW_CREATE`, `REVIEW_REPLY`, `REVIEW_MODERATE` |
| Restaurants | `RESTAURANT_CREATE`, `RESTAURANT_UPDATE`, `RESTAURANT_APPROVE`, `RESTAURANT_SUSPEND`, `BRANCH_MANAGE`, `STAFF_MANAGE` |
| Menu | `MENU_UPDATE`, `MENU_AVAILABILITY_UPDATE` |
| Promotions | `OFFER_MANAGE` (restaurant-funded), `COUPON_MANAGE` (platform) |
| Payments | `PAYMENT_CREATE`, `PAYMENT_VIEW_ALL`, `WALLET_VIEW`, `REFUND_APPROVE_HIGH` |
| Delivery | `DELIVERY_OPERATE`, `PARTNER_VERIFY`, `ASSIGNMENT_OVERRIDE` |
| Users & access | `USER_VIEW_ALL`, `USER_BLOCK`, `ROLE_MANAGE`, `PERMISSION_MANAGE` |
| Support | `COMPLAINT_CREATE`, `COMPLAINT_MANAGE` |
| Insights & ops | `ANALYTICS_VIEW_PLATFORM`, `ANALYTICS_VIEW_RESTAURANT`, `AUDIT_VIEW`, `SYSTEM_HEALTH_VIEW`, `AI_MONITOR_VIEW`, `REQUIREMENT_VIEW`, `REQUIREMENT_APPROVE`, `RELEASE_VIEW`, `DLT_REPLAY` |
| Platform | `AI_ASSISTANT_USE`, `MEDIA_UPLOAD`, `NOTIFICATION_TEMPLATE_MANAGE`, `SEARCH_REINDEX` |

### 3.3 Role → permission matrix
Scope: **own** = the caller's own resources; **scoped** = restaurants or branches in the caller's JWT scopes; **assigned** = deliveries currently assigned to the partner.

| Permission | CUSTOMER | R_OWNER | R_MANAGER | PARTNER | SUPPORT | ADMIN | SUPER_ADMIN |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ORDER_CREATE, CART_MANAGE, PAYMENT_CREATE | ✓ | | | | | | |
| ORDER_VIEW | own | scoped | scoped | assigned | | | |
| ORDER_VIEW_ALL | | | | | ✓ | ✓ | ✓ |
| ORDER_CANCEL (customer cancel rules) | own | | | | | | |
| ORDER_CANCEL_ANY (with reason) | | | | | ✓ | ✓ | ✓ |
| ORDER_MANAGE (accept/reject/prepare/ready) | | scoped | scoped | | | | |
| ORDER_REFUND | | | | | ≤ support limit | ≤ admin limit | ✓ |
| REFUND_APPROVE_HIGH | | | | | | | ✓ |
| REVIEW_CREATE | own delivered orders | | | | | | |
| REVIEW_REPLY | | scoped | scoped | | | | |
| REVIEW_MODERATE | | | | | ✓ | ✓ | ✓ |
| RESTAURANT_CREATE | | ✓ (self-onboarding) | | | | ✓ | ✓ |
| RESTAURANT_UPDATE, BRANCH_MANAGE | | scoped | | | | ✓ | ✓ |
| STAFF_MANAGE | | scoped | | | | | ✓ |
| RESTAURANT_APPROVE, RESTAURANT_SUSPEND | | | | | | ✓ | ✓ |
| MENU_UPDATE | | scoped | scoped | | | | ✓ |
| MENU_AVAILABILITY_UPDATE | | scoped | scoped | | | | ✓ |
| OFFER_MANAGE | | scoped | | | | ✓ | ✓ |
| COUPON_MANAGE | | | | | | ✓ | ✓ |
| PAYMENT_VIEW_ALL | | | | | ✓ (read) | ✓ | ✓ |
| WALLET_VIEW | own | | | | ✓ | ✓ | ✓ |
| DELIVERY_OPERATE | | | | ✓ (own) | | | |
| PARTNER_VERIFY | | | | | | ✓ | ✓ |
| ASSIGNMENT_OVERRIDE | | | | | ✓ | ✓ | ✓ |
| USER_VIEW_ALL | | | | | ✓ (masked) | ✓ | ✓ |
| USER_BLOCK | | | | | | ✓ | ✓ |
| ROLE_MANAGE, PERMISSION_MANAGE | | | | | | | ✓ |
| COMPLAINT_CREATE | ✓ | ✓ | ✓ | ✓ | | | |
| COMPLAINT_MANAGE | | | | | ✓ | ✓ | ✓ |
| ANALYTICS_VIEW_RESTAURANT | | scoped | scoped | | | ✓ | ✓ |
| ANALYTICS_VIEW_PLATFORM | | | | | | ✓ | ✓ |
| AUDIT_VIEW, SYSTEM_HEALTH_VIEW, AI_MONITOR_VIEW, DLT_REPLAY | | | | | | ✓ | ✓ |
| REQUIREMENT_VIEW, RELEASE_VIEW | | | | | | ✓ | ✓ |
| REQUIREMENT_APPROVE | | | | | | | ✓ (engineering approver) |
| AI_ASSISTANT_USE | ✓ | | | | | | |
| MEDIA_UPLOAD | ✓ (avatars, review images) | ✓ | ✓ | ✓ (documents, proof) | | ✓ | ✓ |
| NOTIFICATION_TEMPLATE_MANAGE, SEARCH_REINDEX | | | | | | ✓ | ✓ |

Notes:
- Restaurant reject is part of `ORDER_MANAGE`. Customers cancel only in CREATED, PAYMENT_PENDING, CONFIRMED or RESTAURANT_ACCEPTED (REQ-ORDER-004).
- Refund limits (OQ-23, decided 2026-10-01) are configuration values: support up to ₹500 per order, admin up to ₹5,000; higher amounts need SUPER_ADMIN approval.
- `RESTAURANT_MANAGER` cannot invite staff, change ownership-level settings or delete the restaurant (REQ-RESTAURANT-004 AC3).
- Only SUPER_ADMIN can grant ADMIN or SUPER_ADMIN (REQ-AUTH-003 AC5).
- Customer menu browsing and discovery are public (OQ-08).

### 3.4 Ownership rules (IDOR defence, REQ-AUTH-003 AC4)
| Resource | Rule |
|---|---|
| Order | Customer: `order.customerId == sub`. Restaurant: `order.branchId ∈ scopes`. Partner: `order.deliveryPartnerId == partnerId(sub)` **and** the order is active (customer details are hidden after DELIVERED + 1 h; REQ-SEC-003 AC2). |
| Address, profile, wallet, notifications, cart | `ownerId == sub` |
| Restaurant / branch / menu | Restaurant or branch ID in scopes. Scopes are **re-checked against restaurant-service for write operations** so a revocation applies on the next request (REQ-RESTAURANT-004 AC2). |
| Delivery assignment / offer | `partnerId == partnerId(sub)` |
| Media | Private assets need ownership or a role-based purpose check before a signed URL is issued |
| WebSocket subscriptions | Same rules, applied on SUBSCRIBE |

A foreign resource returns `404 NOT_FOUND`, not 403, for customer-owned objects, so existence is not revealed. IDOR tests are mandatory for every resource endpoint (REQ-QA-004).

---

## 4. Data protection (REQ-SEC-003)

| Control | Design |
|---|---|
| In transit | TLS 1.2+ (prefer 1.3) at CDN, load balancer and ingress. HSTS (1 year, includeSubDomains). Internal traffic is inside the VPC and cluster, with NetworkPolicies; mTLS via a service mesh is a later option. Database, Redis and Kafka clients use TLS in staging and production. |
| At rest | RDS, ElastiCache, MSK, S3 and EBS encrypted with KMS. Backups encrypted. |
| Field-level | KYC numbers (PAN, Aadhaar, licence) use AES-256-GCM envelope encryption with the plain last 4 digits kept for display. Aadhaar: store only what is legally needed (masked, last 4 digits). |
| Minimisation | Events carry IDs, not contact data. Partners see the customer's first name, the drop location and a masked phone, only while the delivery is active. Analytics uses pseudonymous IDs. |
| Logs | `common-observability` masking: phone (`******3210`), e-mail (`r***@x.com`), tokens, `Authorization`, OTPs, card-like patterns (Luhn match) and addresses. Request and response bodies are never logged at INFO. |
| Retention | Per [06 §3](06-database-design.md); purge jobs are audited |
| Privacy rights (DPDP Act 2023, REQ-SEC-003 AC4) | Data export (JSON) and account deletion: personal data is anonymised and financial records are retained with a pseudonymised customer reference. Requests are processed asynchronously with an audit trail. |

---

## 5. Secrets management (NFR-SEC-002)

- **Never in Git, images or config-server.** Spring properties reference environment variables, e.g. `spring.datasource.password: ${DB_PASSWORD}`.
- **Local:** a `.env` file (git-ignored), generated from `.env.example`, which holds placeholder names only.
- **Kubernetes:** External Secrets Operator syncs AWS Secrets Manager into Kubernetes Secrets, mounted as environment variables. One IAM role per service (IRSA) with access only to its own secrets.
- **CI:** GitHub OIDC to an AWS role, with no long-lived cloud keys. Repository secrets only for things that cannot use OIDC.
- **Detection:** gitleaks pre-commit hook and CI job on every PR (REQ-DEVOPS-002), plus GitHub secret scanning and push protection. Container images are scanned with Trivy, which fails the build on secrets or critical CVEs.
- **AI agents** never receive production secrets (REQ-DEVAGENT-002).

---

## 6. OWASP Top 10 (2021) mapping

| Risk | Controls | Verification (REQ-QA-004) |
|---|---|---|
| A01 Broken Access Control | 3-layer authorisation, ownership policies, deny by default, ArchUnit rule for annotations | IDOR suite per resource; role matrix tests generated from §3.3 |
| A02 Cryptographic Failures | TLS everywhere, Argon2id, RS256, AES-GCM field encryption, KMS | Config tests; TLS scan (testssl) on staging |
| A03 Injection | JPA/parameterised queries only; no string-built SQL (ArchUnit forbids `createNativeQuery` with concatenation); output encoding in React; strict validation | SAST (Semgrep/CodeQL), DAST (OWASP ZAP), injection payload tests |
| A04 Insecure Design | Threat model per flow, server-side pricing, signed quotes, idempotency, saga compensations | Design reviews, abuse-case tests (coupon race, double refund) |
| A05 Security Misconfiguration | Hardened images (non-root, read-only rootfs), actuator on an internal port only, Swagger disabled in production, security headers, CORS allow-list per environment | ZAP baseline, kube-bench/kube-score, Trivy config |
| A06 Vulnerable Components | Dependabot or Renovate, OWASP Dependency-Check / Trivy SCA, pinned versions, SBOM (CycloneDX) per image | CI gates: no critical or high without a waiver |
| A07 Identification & Authentication Failures | OTP limits, lockout, rotation and reuse detection, MFA for admins, session revocation | Auth test suite, brute-force tests |
| A08 Software & Data Integrity Failures | Signed webhooks, outbox (no lost events), signed images (cosign) and provenance, protected branches | Webhook forgery tests; CI verifies signatures before deploy |
| A09 Security Logging & Monitoring Failures | Audit log, structured logs with correlation, security alerts (lockout spikes, refresh reuse, webhook signature failures) | Alert rule tests; audit coverage test per sensitive action |
| A10 SSRF | No user-supplied URL fetching. Media uses pre-signed uploads only. Outbound egress allow-list for provider hosts. | ZAP plus targeted tests |

**API Security Top 10 (2023) additions:** BOLA, i.e. object-level authorisation (= A01 ownership); BOPLA, i.e. property-level authorisation (response DTOs per audience, no entity serialisation); unrestricted resource consumption (rate limits, pagination caps, AI budgets); unsafe consumption of APIs (provider responses validated and timeouts enforced).

### 6.1 HTTP security headers (gateway)
`Strict-Transport-Security`, `Content-Security-Policy` (web apps: `default-src 'self'`; script hashes; `connect-src` API, WebSocket and payment provider domains; `frame-src` payment provider), `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy` (geolocation only where needed), `X-Frame-Options: DENY` (except the payment iframe host). CORS uses an exact-origin allow-list per environment, with credentials only for the auth refresh path.

---

## 7. Payment security (REQ-PAYMENT-001..005, NFR-SEC-003)

1. **Hosted checkout** (Razorpay Checkout or the mock): card and UPI data never touch platform systems, which keeps PCI DSS scope to SAQ-A level.
2. The amount is computed server-side from the order. The gateway order is created server-side, so the client cannot alter the amount.
3. **Webhook verification:** HMAC-SHA256 with the webhook secret over the raw body, constant-time compare. Invalid signatures get 401, are logged and alerted. Events are deduplicated by provider event ID, and processing is idempotent.
4. **Never trust frontend status:** the client "success" callback only triggers a UI poll. Confirmation comes from the verified webhook, or from reconciliation, which queries the gateway API server-side.
5. Refund invariants are enforced in the database (`refunded_amount <= captured_amount`) and in code. Refunds require idempotency keys.
6. Wallet: `CHECK balance >= 0`, debit and ledger in one transaction, unique reference per credit.
7. Optionally, webhooks are accepted only from the provider's published IP ranges, configured at the ingress.

---

## 8. AI security (REQ-AI-002, REQ-AI-003, MP rule: the LLM must not access the database)

| Threat | Control |
|---|---|
| Direct database access by the LLM | Impossible by design: ai-service has no database credentials for domain databases. Tools are HTTP calls through the gateway. |
| Prompt injection (user input or content such as restaurant descriptions and reviews) | The system prompt is isolated; untrusted content is delimited and labelled as data; the tool allow-list is fixed; arguments are validated against JSON Schema; tools run with the **user's** JWT, so the LLM cannot exceed the user's rights; state-changing tools (`addToCart`) need explicit user confirmation; no payment or order-placement tools in v1 |
| Data exfiltration through tool results | Tool results are filtered to the fields needed (no PII beyond the user's own); responses are scanned for secrets and PII patterns before returning |
| Cost exhaustion | Per-user rate limits, daily token budget per user, global monthly budget with circuit breaker and fallback to keyword search |
| Harmful output | Output schema validation; allergy and dietary statements carry a disclaimer and are only based on menu data; a red-team test suite runs in CI (REQ-AI-003) |
| Logging | Conversations are stored PII-minimised with 30-day retention; prompts and completions are excluded from general logs |

DevAgent and TestAgent: no production credentials; branch protection plus CODEOWNERS plus required reviews make merge or deploy impossible without a human (REQ-DEVAGENT-002); agent actions are logged.

---

## 9. Infrastructure and supply chain

- **Containers:** distroless or minimal JRE base, non-root UID, read-only root filesystem, dropped capabilities, `seccompProfile: RuntimeDefault`, resource limits.
- **Kubernetes:** Pod Security Standards "restricted"; NetworkPolicies default-deny with explicit allows (service → its database, Redis, Kafka, and the services it calls); secrets as environment variables from External Secrets; RBAC least privilege; audit logging on the API server (managed).
- **Edge:** CloudFront + AWS WAF (managed rule sets, rate-based rules, geo restriction for OTP endpoints if abused); Shield Standard.
- **Kafka:** SASL/SCRAM or IAM authentication, TLS, ACLs per service principal (produce to its own topics, consume from subscribed topics).
- **PostgreSQL:** per-service users; the runtime user has no DDL; the audit runtime user is insert-only; public access is disabled.
- **Supply chain:** pinned dependencies, SBOM per image, image signing (cosign) and admission verification, Dependabot or Renovate, GitHub Actions pinned to commit SHAs.

---

## 10. Security testing plan (REQ-QA-004, Phase 22, continuous from Phase 3)

| Activity | Tool | When |
|---|---|---|
| Secret scanning | gitleaks, GitHub push protection | Every commit/PR |
| SAST | CodeQL (Java, TypeScript), Semgrep rules | Every PR |
| SCA | Dependabot, Trivy / OWASP Dependency-Check | Every PR + nightly |
| Container and IaC scan | Trivy (image, config), Checkov (Terraform), kube-score | Every PR touching Docker/infra |
| Authorisation tests | Generated role-matrix tests + IDOR suite (REST Assured) | Every PR (service scope) |
| DAST | OWASP ZAP baseline (PR on dev environment), full scan (staging) | Nightly / before release |
| Abuse cases | Coupon race, double refund, duplicate webhook, quote tampering, OTP brute force | Integration suite |
| AI red team | Prompt-injection corpus, tool misuse attempts | CI for ai-service |
| Penetration test | Manual review against ASVS L2 checklist | Phase 22, before v1.0.0 |

**Release gate:** no unresolved critical or high findings (NFR-SEC-001). Any accepted risk needs a documented waiver with an expiry and an owner.
