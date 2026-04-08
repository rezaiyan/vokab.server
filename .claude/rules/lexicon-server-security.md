---
name: Security & Auth
description: JWT, Spring Security, rate limiting, webhook validation, and sensitive data handling
type: convention
paths:
  - "src/main/kotlin/com/alirezaiyan/vokab/server/security/**"
  - "src/main/kotlin/com/alirezaiyan/vokab/server/config/**"
  - "src/main/kotlin/com/alirezaiyan/vokab/server/presentation/controller/**Auth**"
  - "src/main/kotlin/com/alirezaiyan/vokab/server/presentation/controller/**Webhook**"
---

# Security & Auth

## Sensitive Data — Never Log

**Never** log: passwords, JWTs (access or refresh), full Apple/Google ID tokens, refresh tokens, FCM/APNs device tokens, email addresses, subscription receipts.

- Use `SensitiveDataMasker` / request logging filter for automatic masking.
- In custom log lines, log `user.id` — never `user.email`.
- If you must log an identifier for debugging, log a short prefix + hash, not the full value.

## JWT

- **Never parse JWTs manually** in controllers or services. The `JwtAuthenticationFilter` sets `SecurityContext` — use `@AuthenticationPrincipal User`.
- RS256 is the production signing algorithm (`RS256JwtTokenProvider`); HS256 is a fallback. Don't hardcode either — go through the provider.
- Refresh token rotation: the `replaced_by` column chain (V3/V6 migrations) enforces one-time use. Never reuse a refresh token.

## Public Endpoints

Any route that should skip authentication MUST be added explicitly to `SecurityConfig.kt`'s `permitAll()` list. New endpoints are **locked by default** — don't forget to list health checks, webhooks, and auth endpoints.

## Rate Limiting (Bucket4j)

Apply rate limiting via `RateLimitConfig` to:

- All `/api/auth/**` endpoints
- AI extraction endpoints (OpenRouter-backed)
- Password reset / email-sending flows

Rate limits live in `application.yml` under `rate-limit.*`. New sensitive endpoints must be added to the filter chain, not just relied on at the controller level.

## Webhook Signature Validation

Webhooks MUST validate signatures **before** processing payload:

| Webhook     | Validator                                   |
| ----------- | ------------------------------------------- |
| Apple       | `ApplePublicKeyService` → JWT verification |
| RevenueCat  | HMAC signature from `X-RevenueCat-Signature` header |

Unvalidated webhooks are rejected with 401 and logged. Never short-circuit signature checks "for testing" in production profile.

## Password Hashing

- **Argon2id** via BouncyCastle (`BCryptPasswordEncoder` is not used — Argon2 is the standard).
- Never store plaintext. Never compare with `==`.

## CORS

`WebConfig` / `SecurityConfig` define allowed origins. Don't add `*` in production. New origins (e.g., web client domains) go in `application.yml` under `cors.allowed-origins`.

## Secrets

- Never commit secrets. `application.yml` uses `${ENV_VAR}` placeholders for all credentials.
- Firebase service account JSON, JWT RSA keys, OpenRouter API key, Apple signing key, RevenueCat webhook secret — all env vars.
- Check `.gitignore` before adding a new config file with credentials.

## Error Leakage

`server.error.include-message: never`, `include-stacktrace: never`. Never build error responses that echo internal messages. Log the full exception server-side; return a generic message to the client.
