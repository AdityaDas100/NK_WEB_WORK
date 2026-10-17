# Security

> Threat model, controls and operating rules for the NK ETHICAL (IND) LTD inquiry platform.

This is a public, unauthenticated write endpoint that accepts free text from anonymous visitors and
forwards it to (a) a database and (b) a spreadsheet that a human opens on a workstation. That shape
determines the threat model: the interesting attacks are **injection into a downstream consumer**,
**resource exhaustion**, **PII leakage through logs**, and **spam**.

---

## 1. Threat model

| # | Threat                                                             | Control                                                                                                            | Where                                                        |
|---|--------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| 1 | Stored XSS — markup survives into an admin UI, CSV or e-mail       | Payload parsed as an HTML fragment with jsoup; only *text* is kept, and residual `<`/`>` are stripped.              | `InputSanitizer`                                             |
| 2 | Header / log injection through `CR`/`LF` or control characters     | Control characters removed (line breaks kept only inside messages, at most one blank line in a row).                | `InputSanitizer.multiline`                                   |
| 3 | SQL injection                                                     | Every statement goes through JPA with bound parameters; there is no string concatenation into SQL anywhere.         | repository / `InquiryRepository`                             |
| 4 | Spreadsheet formula injection (`=cmd|'/c calc'!A0`)                | Cells whose text starts with `= + - @` are prefixed with `'`; leading control-character bypasses are stripped.      | `SpreadsheetSafety`                                          |
| 5 | Homograph / overlong payloads bypassing `varchar` limits           | Unicode NFC normalisation *before* length limits; truncation respects surrogate pairs.                              | `InputSanitizer`                                             |
| 6 | Resource exhaustion — huge bodies, unparsable JSON                  | 16 KiB body cap enforced before parsing (both tiers); missing `Content-Length` is refused with `411`.               | `RequestSizeLimitFilter`, `route.ts`                         |
| 7 | Rate-limit bypass / spam flood                                     | Sustained + burst + global sliding windows per client, keyed by HMAC of the IP, on both tiers.                      | `RateLimitFilter`, `SlidingWindowRateLimiter`, `lib/rate-limit.ts` |
| 8 | Naive bots filling the form                                        | Hidden honeypot field; a filled value returns a plausible success but is never forwarded or stored.                 | `InquiryForm`, `route.ts`                                    |
| 9 | PII leaking into logs or metrics                                   | Masked values, message *length* instead of content, HMAC fingerprints instead of IPs. No raw PII in any log line.   | `PiiMasker`, `lib/logger.ts`                                 |
| 10| Cross-site request forgery / ambient authority                     | The API is stateless, issues no cookies and uses no session; CSRF is disabled because there is no ambient authority. | `SecurityConfig`                                             |
| 11| Unauthenticated access to operational endpoints                    | Deny-by-default: only `POST /api/inquiries`, `/actuator/health*` and `/error` are public; everything else is `403`.  | `SecurityConfig`                                             |
| 12| Cross-origin data theft through a permissive CORS policy           | Explicit origin allow-list, wildcards rejected, `allowCredentials=false`, one hour preflight cache, limited headers. | `SecurityConfig`, `CorsProperties`                           |
| 13| Downgrade to plaintext HTTP                                        | `requiresChannel().requiresSecure()` when `app.require-https=true`; mandatory under `prod`; HSTS preloaded.          | `SecurityConfig`, `ProductionConfigValidator`                |
| 14| Clickjacking / MIME sniffing / referrer leakage on the site        | `X-Frame-Options: DENY`, `nosniff`, restrictive CSP/referrer/permissions policies, `COOP`/`COEP`.                    | `next.config.ts`, `SecurityConfig`                           |
| 15| Information disclosure through error responses                     | RFC 9457 problem documents with a stable code and a correlation id; no stack traces, class names or SQL.             | `GlobalExceptionHandler`, `ProblemJson`                      |
| 16| Secret leakage: shipping spreadsheet credentials in the image      | No service accounts. Google Sheets is written through an Apps Script webhook with a revocable shared secret.        | `GoogleSheetsExportSink`, `docs/EXPORT.md`                   |
| 17| A client "reserving" another visitor's idempotency key             | Keys are only honoured when they match a strict pattern; anything else is replaced by a generated UUID.              | `ClientMetadataResolver`, `route.ts`                         |
| 18| Host-header / forwarded-header spoofing to fake a client IP        | `X-Forwarded-*` is ignored unless `app.trust-proxy-headers=true`; `forward-headers-strategy=framework` otherwise.    | `ClientIpResolver`, `application.yml`                        |
| 19| Container breakout / privilege escalation                          | Backend and frontend images run as unprivileged users; `no-new-privileges` is set in compose.                        | `backend/Dockerfile`, `frontend/Dockerfile`, `docker-compose.yml` |
| 20| Dependency vulnerabilities                                          | Versions pinned to verified GA releases; `mvn verify` / `npm audit` in CI; images rebuilt on a schedule.             | `pom.xml`, `package.json`                                    |

---

## 2. Data handling

### What is stored

| Field                               | Content                                                                 |
|-------------------------------------|-------------------------------------------------------------------------|
| `name`, `email`, `phone`, `company`, `message` | Sanitised submission — this *is* personal data.               |
| `consent`                           | Privacy-notice acceptance, needed to demonstrate lawful basis.           |
| `source_ip_hash`                    | `HMAC-SHA256(client IP, APP_HASH_SECRET)`, truncated to 32 hex chars.    |
| `user_agent`                        | Sanitised and truncated to 256 characters, for abuse triage.             |

The raw IP address is **never** persisted. Because the fingerprint is keyed, rotating
`APP_HASH_SECRET` makes all historical fingerprints unlinkable — at the cost of also losing
correlation, which is the intended trade-off.

### What is logged

Only masked or derived values:

```
inquiry.accepted reference=3f2504e0… name=J*** D*** email=j***@example.com
                 phone=***9999 message=142 chars ip_fp=9f2c1a… 
```

Rules that reviewers enforce:

1. No `log.info(request.getBody())`, ever. The payload is not loggable by construction.
2. New log statements must use `PiiMasker` (or add a masking helper) before touching user data.
3. The Next.js logger mirrors these rules in `src/lib/logger.ts`; log lines are JSON, and the
   `email` field must always come from `maskEmail()`.
4. Log destinations must be access-controlled: the log file is not world readable, and the shipped
   `application.yml` writes to `logs/pharma-inquiry-service.log` inside the container.

### Retention

The application does not delete inquiries. Retention is an operator policy:

```sql
-- Example: purge everything older than 24 months, including already exported rows.
DELETE FROM inquiries WHERE created_at < now() - interval '24 months';
```

Purge in batches and export first; deleting rows does **not** remove them from the workbook, so treat
the spreadsheet as part of the retention scope.

---

## 3. Transport and headers

| Control                     | Value                                                                     |
|-----------------------------|---------------------------------------------------------------------------|
| HTTPS                       | Enforced by the ingress in production; `requiresSecure()` when configured. |
| HSTS                        | `max-age=31536000; includeSubDomains; preload` (site: 63072000).           |
| CSP (API)                   | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` |
| CSP (site)                  | `default-src 'self'` with no third parties; see the hardening roadmap.     |
| `X-Content-Type-Options`    | `nosniff`                                                                 |
| `X-Frame-Options`           | `DENY`                                                                    |
| `Referrer-Policy`           | API `no-referrer`, site `strict-origin-when-cross-origin`                  |
| `Permissions-Policy`        | Camera, microphone, geolocation, payment, USB all disabled                 |
| `COOP` / `COEP`             | `same-origin` / `require-corp`                                             |
| `Cache-Control` (inquiries) | `no-store, no-cache, must-revalidate` on both tiers                        |

TLS is terminated at the reverse proxy/CDN. The application deliberately does **not** hold
certificates; `server.forward-headers-strategy=framework` makes it see the public scheme.

---

## 4. Rate limiting

Three independent windows, evaluated *before* the body is read:

| Window                     | Default | Purpose                                                        |
|----------------------------|---------|----------------------------------------------------------------|
| Sustained, per client      | 10/min  | Human pace — nobody legitimately submits ten inquiries a minute.|
| Burst, per client          | 5/s     | Stops a script that fires the whole minute quota at once.       |
| Global                     | 600/min | Safety net so a botnet spread over many IPs cannot exhaust the pool. |

The Next.js route handler enforces a second, independent limit (5/min, burst 2) so that traffic which
never reaches the backend is already discarded at the edge.

**Keys are HMAC fingerprints of the client IP.** A heap dump, a metric label or a log line therefore
never contains a visitor's address, while still allowing abuse to be correlated.

**Known limitation.** The limiter is in-memory and per instance: with *N* replicas an attacker gets
*N* × the quota. The documented upgrade path is a shared store (Redis `INCR` + `EXPIRE`, or an
envoy/nginx/WAF limit) — the call site does not change, only `SlidingWindowRateLimiter`.

---

## 5. Input handling rules

1. **Sanitise before validating.** `InquiryRequest`'s canonical constructor runs `InputSanitizer`
   first; annotations only ever see cleaned values. Do not add a field without going through the
   sanitizer.
2. **Limits mirror the schema.** Every `@Size(max = …)` equals the corresponding `varchar` width.
3. **Fails closed.** `spring.jackson.deserialization.fail-on-unknown-properties=true` — an unexpected
   property is a `400`, not something silently ignored.
4. **Consent is a constraint, not a convention.** `@AssertTrue` on a primitive `boolean`.
5. **Regexes are anchored and language-aware.** `\p{L}\p{M}` classes rather than ASCII ranges, with a
   bounded length so they cannot be used for catastrophic backtracking.
6. **No HTML is ever produced by the API.** Responses are JSON only; the site renders untrusted text
   through React's escaping.

---

## 6. Secrets

| Secret                    | Purpose                                        | Rotation                                              |
|---------------------------|------------------------------------------------|-------------------------------------------------------|
| `APP_HASH_SECRET`         | HMAC key for IP/e-mail fingerprints            | Rotate to break linkability; fingerprints change.      |
| `CLIENT_IP_HASH_SECRET`   | Edge rate-limiter key                          | Rotate independently of the backend; counters reset.   |
| `GOOGLE_SHEETS_TOKEN`     | Shared secret for the Apps Script webhook      | Rotate in the Apps Script properties and in the env.   |
| `POSTGRES_PASSWORD`       | Database credential                            | Standard credential rotation.                          |

Rules:

* Only environment variables — no secret in `application.yml`, no secret in the image, no secret in
  the repository. `.env` is git-ignored; `.env.example` holds placeholders only.
* `ProductionConfigValidator` refuses to start under `prod` with a placeholder or short
  `APP_HASH_SECRET`, an empty CORS list or `require-https=false`.
* The generated `APP_HASH_SECRET` should be at least 32 characters from `openssl rand -base64 48`.
* The Apps Script token travels in the request **body** (Apps Script cannot read headers), never in
  the URL, so it does not end up in access logs.

---

## 7. Hardening roadmap

Ordered by value per unit of effort.

1. **CSP nonces on the site.** Replace `'unsafe-inline'` in `script-src` with a per-request nonce
   issued from middleware and `strict-dynamic`. This is the last remaining weakening of the site's
   CSP.
2. **Shared rate-limit store.** Redis-backed counters (or WAF/ingress limits) to make the quota
   correct across replicas and to survive a restart.
3. **Turnstile / reCAPTCHA on the form** if the honeypot plus rate limits stop being enough. Verify
   the token server-side in the route handler *and* the API; never in the browser alone.
4. **DMARC/SPF/DKIM and a notification e-mail** so an operator learns about new inquiries without
   polling the spreadsheet.
5. **Alerting**: `pharma.export.parked > 0` and `pharma.export.backlog` growing over ~10 minutes are
   both page-worthy.
6. **Database least privilege.** The application user needs `SELECT/INSERT/UPDATE` on `inquiries` and
   the Flyway schema-history table — not `SUPERUSER`, not `CREATE ROLE`.
7. **Field-level encryption** for `message` if the threat model later includes database-file theft
   rather than only database compromise.
8. **Backups**: `pg_dump` on a schedule, plus periodic restore drills. The workbook is derived state;
   the database is not.

---

## 8. Reporting a vulnerability

Do not open a public issue for a security problem. Contact the platform team directly, include the
`X-Request-Id` of any request you observed, and allow a reasonable window before disclosure.
