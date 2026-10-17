# Architecture

> NK ETHICAL (IND) LTD — landing page, inquiry funnel and automated spreadsheet export.

This document describes how the system is put together, which guarantee each moving part is
responsible for, and why the boundaries are where they are. `docs/SECURITY.md` covers the threat
model and `docs/EXPORT.md` the export pipeline in operational detail.

---

## 1. Shape of the system

```
                 browser
                    │  HTTPS
                    ▼
        ┌───────────────────────────┐
        │  Next.js 16 (App Router)  │   static marketing page, product catalogue,
        │  React 19 + Tailwind 4    │   InquiryForm (react-hook-form + Zod)
        └─────────────┬─────────────┘
                      │  POST /api/inquiries           ← same origin: no CORS in the browser path
                      ▼
        ┌───────────────────────────┐
        │  Next.js route handler    │   body-size guard → per-IP rate limit → honeypot →
        │  (Node runtime)           │   Zod re-validation → server-to-server forward
        └─────────────┬─────────────┘
                      │  POST /api/inquiries   (Idempotency-Key, X-Request-Id)
                      ▼
        ┌───────────────────────────┐
        │  Spring Boot 4.1 / Java 25│   sanitise → validate → commit  (durable, then 201)
        │  Spring MVC + Security 7  │   ┌─────────────────────────────────────────────┐
        └─────────────┬─────────────┘   │  transactional outbox on `inquiries`        │
                      │                 └──────────────┬──────────────────────────────┘
                      ▼                                │ async worker + sweeper
        ┌───────────────────────────┐                  ▼
        │  PostgreSQL 16            │        ┌──────────────────────────┐
        │  `inquiries` (system of   │        │  Export sinks            │
        │  record)                  │───────▶│  · inquiries.xlsx (POI)  │
        └───────────────────────────┘        │  · Google Sheets webhook │
                                             └──────────────────────────┘
```

Three independent stages, and the arrow that matters is the one between them: **the inquiry is
committed to PostgreSQL before the client is told anything.** The spreadsheet is derived state and
can be regenerated from the database at any time.

---

## 2. Repository layout

```
NK_WEB_WORK/
├── backend/                       Spring Boot intake service (Maven, Java 25)
│   ├── src/main/java/com/nkweb/pharma/
│   │   ├── api/                   InquiryController + request/response DTOs
│   │   ├── config/                properties records, security, async, prod fail-fast guard
│   │   ├── domain/                Inquiry entity, ExportStatus
│   │   ├── error/                 RFC 9457 advice + filter-level problem writer
│   │   ├── repository/            Spring Data JPA repository (claim query, sweeper)
│   │   ├── security/              sanitising, PII masking, rate limiting, request filters
│   │   └── service/
│   │       ├── InquiryService     intake use case (transaction boundary, idempotency)
│   │       └── export/            outbox drain, file/Sheets sinks, locks, rebuild tool
│   ├── src/main/resources/
│   │   ├── application.yml        all defaults, overridable through environment variables
│   │   └── db/migration/          Flyway migrations (V1 is the whole schema)
│   └── src/test/java/             unit + slice + H2 integration tests
├── frontend/                      Next.js 16 App Router site
│   ├── src/app/                   pages, metadata routes, /api/inquiries route handler
│   ├── src/components/site/       header, hero, catalogue, inquiry form, footer
│   └── src/lib/                   env, logger, rate limiter, product data, Zod schema
├── docs/                          this document, SECURITY.md, EXPORT.md, openapi.yaml
├── docker-compose.yml             PostgreSQL + backend + frontend, one command
└── .env.example                   every variable the compose stack reads
```

---

## 3. Backend

### 3.1 Request lifecycle of a submission

| # | Component                                   | Responsibility                                                                                       |
|---|---------------------------------------------|------------------------------------------------------------------------------------------------------|
| 1 | `RequestTracingFilter`                      | Accepts or generates `X-Request-Id`, puts it in the MDC, echoes it in the response.                   |
| 2 | `RateLimitFilter`                           | Sustained (per minute), burst (per second) and global limits keyed by HMAC of the client IP.          |
| 3 | `RequestSizeLimitFilter`                    | Rejects bodies above `app.request-limits.max-body-bytes` and chunked requests without a length.      |
| 4 | `SecurityConfig`                            | CORS preflight, deny-by-default authorization, HSTS and friends.                                      |
| 5 | `InquiryController`                         | Extracts idempotency key, IP fingerprint and user agent; delegates to the service.                   |
| 6 | `InquiryRequest` (record)                   | **Sanitises in its canonical constructor**, then JSR 380 validation runs on the cleaned value.        |
| 7 | `InquiryService`                            | Idempotency pre-check, transactional insert, outbox row, publishes `InquiryCreatedEvent`.            |
| 8 | `GlobalExceptionHandler`                    | Converts every failure into an RFC 9457 problem document; never leaks internals.                     |

### 3.2 Sanitise, *then* validate

This ordering is the single most important design decision in the intake path.

```
raw JSON ──▶ InputSanitizer ──▶ JSR 380 constraints ──▶ Inquiry entity ──▶ PostgreSQL
             (NFC, markup,        (size, pattern,        (varchar widths
              control chars,       e-mail, consent)       identical to limits)
              truncation)
```

* Validating raw input would reject payloads that are harmless after cleaning, and — worse — accept
  payloads that only *look* harmless before cleaning.
* Truncation to the column width happens in the same pass, so a value that passes validation can
  never raise a database error or be silently cut by PostgreSQL.
* Markup is removed with jsoup, so nothing stored in the database can be rendered as HTML by any
  consumer — an unescaped CSV or spreadsheet viewer included.

### 3.3 The transaction boundary

`InquiryService` uses `TransactionTemplate` rather than `@Transactional` on purpose. The idempotency
recovery path has to run **after** a failed transaction has been rolled back, which is impossible
from inside the same transactional method:

```
pre-check  ──found────────────▶ return the original inquiry (200 DUPLICATE)
    │
    └──not found──▶ TX { INSERT row; publish InquiryCreatedEvent }──commit──▶ 201 CREATED
                                  │
                                  └──unique violation──▶ rollback, re-read by key ──▶ 200 DUPLICATE
```

The event is published *inside* the transaction and consumed by
`@TransactionalEventListener(AFTER_COMMIT)`, so a rollback can never trigger an export.

### 3.4 Data model

`inquiries` is both the business record and the outbox:

| Column group                                   | Purpose                                                                   |
|------------------------------------------------|---------------------------------------------------------------------------|
| `id`, `public_id`                              | Surrogate key (never exposed) and opaque public reference (UUID).          |
| `client_request_id` (UNIQUE)                   | Idempotency key.                                                           |
| `name`, `email`, `phone`, `company`, `message` | Sanitised payload; widths mirror the validation limits.                     |
| `consent`                                      | Privacy-notice acceptance; mandatory.                                      |
| `export_status`, `export_attempts`, `export_error`, `exported_at` | Outbox state machine.                                     |
| `source_ip_hash`, `user_agent`                 | Pseudonymous abuse triage data — an HMAC, never an address.                 |
| `created_at`, `updated_at`, `version`          | Auditing plus optimistic locking for concurrent export claims.              |

Indexes are shaped by the queries that exist: `(export_status, id)` drives the claim, `created_at`
supports reporting, `email` supports support lookups.

### 3.5 Concurrency

| Where                    | Mechanism                                                                                       |
|--------------------------|-------------------------------------------------------------------------------------------------|
| Across instances         | `SELECT … FOR UPDATE SKIP LOCKED` — two workers receive disjoint batches.                        |
| Within one JVM           | A fair `ReentrantLock` around a drain; a second caller returns immediately.                       |
| Excel file, in process   | Per-path `ReentrantLock` (`FileWriteLock`).                                                       |
| Excel file, cross-process| `FileLock` on a sibling `<file>.lock`, acquired with `tryLock` in a bounded loop.                  |
| Workbook replacement     | Write to a temp file in the same directory, then `ATOMIC_MOVE` over the target.                    |
| Lost updates             | `@Version` optimistic locking on the entity.                                                      |

A `FileLock` alone cannot replace the in-JVM lock (locking a region the same JVM already holds throws
`OverlappingFileLockException` instead of blocking), and an in-JVM lock alone cannot protect against a
second instance or a cron exporter. Both are required.

### 3.6 Failure semantics

| Failure                                        | Reaction                                                                       |
|------------------------------------------------|---------------------------------------------------------------------------------|
| Database unavailable                           | `503 storage-unavailable`; nothing stored, nothing exported.                     |
| Unique violation from a race                   | The loser returns the winner's reference — a duplicate, not an error.             |
| Sink unavailable (disk full, Sheets 5xx)       | Batch back to `PENDING`, `export_attempts` incremented; retried by the sweeper.   |
| Attempts exhausted (`app.export.max-attempts`) | Row parked as `FAILED` and counted by the `pharma.export.parked` gauge.          |
| Process killed between claim and update        | Sweeper re-queues `PROCESSING` rows untouched for 5 minutes.                      |
| Workbook corrupted                             | Quarantined to `<file>.<epoch>.corrupt` and rebuilt from the database.            |
| Workbook locked by an operator in Excel        | `ExportLockTimeoutException` → treated as a transient failure and retried.        |

There is no code path in which a committed inquiry is dropped.

### 3.7 Configuration

Everything is externalised in `application.yml` with environment-variable overrides. The
`prod` profile additionally activates `ProductionConfigValidator`, which refuses to start with a
placeholder `APP_HASH_SECRET`, an empty CORS allow-list or `require-https=false`.

| Variable                          | Default            | Notes                                                    |
|-----------------------------------|--------------------|----------------------------------------------------------|
| `DB_URL`, `DB_USER`, `DB_PASSWORD`| local PostgreSQL   | Pool sizing via `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE`.  |
| `APP_HASH_SECRET`                 | placeholder        | HMAC key for PII pseudonymisation. **Must** be replaced.  |
| `APP_CORS_ALLOWED_ORIGINS`        | `http://localhost:3000` | Comma separated, wildcards rejected.                 |
| `APP_TRUST_PROXY_HEADERS`         | `false`            | Enable only behind a proxy that overwrites `X-Forwarded-*`. |
| `APP_REQUIRE_HTTPS`               | `false`            | `true` redirects to HTTPS; mandatory under `prod`.        |
| `RATE_LIMIT_*`                    | 10 / 5 / 600       | Sustained, burst and global limits per minute.             |
| `EXPORT_ENABLED`, `EXPORT_SINKS`  | `true`, `excel`    | `excel`, `google-sheets` or both.                          |

### 3.8 Observability

* **Metrics** (Prometheus, `/actuator/prometheus` on the private network):
  `pharma.inquiries.accepted`, `.duplicate`, `.rate_limited`, `.allowed`, the `pharma.inquiries.intake`
  timer, and for the pipeline `pharma.export.backlog`, `pharma.export.parked`,
  `pharma.export.rows{sink,result}` and `pharma.export.batches{result}`.
* **Logs** are structured and PII-free: masked e-mail/phone/name, a message *length* rather than its
  content, and HMAC fingerprints instead of IPs. Production uses Boot's built-in structured logging
  (`logging.structured.format.*=ecs`) so the lines are directly ingestible.
* **Health** probes are split into liveness and readiness so a database blip does not get a healthy
  process killed.

---

## 4. Frontend

* **App Router, server components by default.** The page, catalogue and legal pages are static; only
  `InquiryForm` is a client component.
* **`/api/inquiries` route handler** owns the browser-facing contract: body-size guard, per-IP rate
  limit (keyed by an HMAC fingerprint), honeypot suppression, Zod re-validation and a
  server-to-server forward with an idempotency key and a correlation id. The Spring service therefore
  never needs to be reachable from the public internet.
* **One schema, three enforcement points.** The Zod schema in `src/lib/validation/inquiry.ts` runs in
  the browser (UX), in the route handler (defence in depth) and is mirrored by the Jakarta annotations
  on the DTO (authority). Limits are identical to the database column widths.
* **Layout stability.** The catalogue is CSS Grid/Flexbox with fixed aspect ratios, and the inquiry
  form reserves the height of every validation message, so the page cannot shift as the user types,
  as errors appear, or as the viewport changes.
* **Idempotency key rotation.** The form generates a key when it is mounted and rotates it only after
  a successful submission, so a double click or a flaky network retries the *same* key instead of
  producing a duplicate lead.
* **Security headers** (CSP, HSTS, `X-Frame-Options: DENY`, referrer and permissions policies) are set
  centrally in `next.config.ts`.

---

## 5. Running it

### Docker Compose (everything)

```bash
cp .env.example .env      # then edit POSTGRES_PASSWORD, APP_HASH_SECRET, CLIENT_IP_HASH_SECRET
docker compose up --build
```

* site → <http://localhost:3000>
* API → <http://localhost:8080> (`POST /api/inquiries`, `/actuator/health/readiness`)
* database → `localhost:5432`

### Local, without Docker

```bash
# 1. PostgreSQL 16
createdb pharma

# 2. backend  (JDK 25+; Flyway creates the schema on first start)
cd backend
./mvnw spring-boot:run            # or: mvn spring-boot:run
#   DB_URL=jdbc:postgresql://localhost:5432/pharma \
#   DB_USER=pharma DB_PASSWORD=pharma \
#   APP_HASH_SECRET=$(openssl rand -base64 48)

# 3. frontend
cd ../frontend
cp .env.example .env.local
npm install
npm run dev
```

### Tests

```bash
cd backend && mvn verify            # unit + slice + H2 integration tests
cd frontend && npm run typecheck && npm run lint && npm run build
```

The backend suite needs neither Docker nor PostgreSQL: `application-test.yml` runs H2 in PostgreSQL
compatibility mode, applies the same Flyway migrations and switches the exporter off (the export
pipeline is covered by tests that use a real temporary filesystem).

---

## 6. Deliberate trade-offs

| Decision                                        | Why                                                                                                        |
|-------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| Transactional outbox instead of 2-phase commit  | The database orchestrates the retry; no broker to run, no distributed transaction to lose.                   |
| In-memory rate limiting                         | Zero extra infrastructure for a single-instance intake API. The upgrade path (Redis, WAF) is documented.     |
| Apps Script webhook instead of the Sheets API   | No service-account key to store or leak; the sheet owner keeps the permission model.                         |
| Excel workbook rewritten per batch              | At a few hundred rows a batch this is milliseconds; beyond that, switch to daily files or the Sheets sink.    |
| No Lombok/MapStruct                             | Annotation processors are the most fragile part of a JDK-27 build; plain Java removes that class of risk.     |
| `--release 25` on JDK 27                        | The current LTS baseline produces class files any JDK ≥ 25 can run, independent of the build toolchain.       |
