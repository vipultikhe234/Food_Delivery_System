# Security Rules (summary of docs/09-security.md)

1. **No secrets in code, config-server, images or logs.** Use `${ENV_VAR}` placeholders, `.env` locally (git-ignored), External Secrets in Kubernetes. gitleaks runs on every PR.
2. **Deny by default:** gateway authentication, then `@PreAuthorize` permission, then an ownership/scope check in the application layer. A foreign customer resource returns 404.
3. **Tokens:** RS256 access JWT (15 min) validated via JWKS; opaque rotating refresh tokens with reuse detection; web refresh token in an `__Host-` HttpOnly SameSite=Strict cookie with CSRF; access token in memory only.
4. **Passwords** use Argon2id. **OTPs** are HMAC-hashed in Redis with attempt and send limits. **Lockout** after 5 failures in 15 min.
5. **Payments:** hosted checkout only; amount from the order; webhook HMAC verified + deduplicated; never trust client payment status; refunds ≤ captured; wallet ≥ 0.
6. **PII:** masked in logs; minimal in events; KYC numbers field-encrypted; partners see customer data only during active delivery.
7. **Input:** strict DTO validation, unknown fields rejected, parameterised queries only, no user-supplied URL fetching (SSRF).
8. **AI:** no DB access for the LLM; allow-listed tools with schema validation; user's JWT; confirmation for state changes; no payment or contact data sent to the provider; budgets enforced.
9. **Agents:** no production credentials, no direct pushes to `main`, no deploys.
10. **Release gate:** zero unresolved critical/high findings; documented waivers only.
