# NK ETHICAL (IND) LTD — web platform

A production-grade landing page and inquiry funnel for a pharmaceutical company: a Next.js marketing
site with a product catalogue, a hardened Spring Boot intake API, PostgreSQL as the system of record,
and an automated worker that mirrors every inquiry into an Excel workbook or a Google Sheet.

> **Demo content.** Product names, prices, certificates
> and legal text in this repository are placeholders for NK ETHICAL (IND) LTD. Nothing here is medical advice.

---

## Highlights

* **Durable by construction.** The inquiry is committed to PostgreSQL *before* the visitor sees
  `201 Created`. The spreadsheet is derived state, exported by a retried transactional outbox.
* **Secure intake.** Sanitising before validation (jsoup + NFC + control-character stripping), bound
  parameters everywhere, formula-injection defusing, strict CORS, CSP/HSTS, deny-by-default
  authorization, and three independent rate limits.
* **Idempotent.** An `Idempotency-Key` makes a double click, a retry or a proxy replay return the
  original reference instead of creating a duplicate lead.
* **No PII in logs.** Masked values, message *lengths* rather than content, and keyed HMAC
  fingerprints instead of IP addresses — on both tiers.
* **Zero layout shift.** The catalogue is grid/flex based and the form reserves the height of every
  validation message, so nothing moves on mobile, tablet or desktop as errors appear.
* **Concurrency-safe export.** `FOR UPDATE SKIP LOCKED` across instances, an in-process lock plus a
  cross-process file lock for the workbook, atomic file replacement, and dedupe by reference so a
  replay is a no-op.
* **Runs anywhere.** `docker compose up --build` starts PostgreSQL 16, the API and the site. Tests
  need neither Docker nor PostgreSQL.

---

## Quick start

### Everything in Docker

```bash
cp .env.example .env
# edit POSTGRES_PASSWORD, APP_HASH_SECRET and CLIENT_IP_HASH_SECRET
docker compose up --build
```

| Service    | URL                                                              |
|------------|------------------------------------------------------------------|
| Site       | <http://localhost:3000>                                          |
| Intake API | <http://localhost:8080/api/inquiries> (`POST`)                   |
| Health     | <http://localhost:8080/actuator/health/readiness>                |
| PostgreSQL | `localhost:5432`                                                 |
| Workbook   | Docker volume `nk-ethical-pharma_exports` → `/app/data/exports/inquiries.xlsx` |

### Local, without Docker

Prerequisites: **JDK 25+**, **Node 20.9+**, **PostgreSQL 16**.

```bash
# --- database ---
createdb pharma

# --- backend (Flyway creates the schema on first start) ---
cd backend
DB_URL=jdbc:postgresql://localhost:5432/pharma \
DB_USER=pharma DB_PASSWORD=pharma \
APP_HASH_SECRET=$(openssl rand -base64 48) \
mvn spring-boot:run

# --- frontend ---
cd ../frontend
cp .env.example .env.local        # API_BASE_URL defaults to http://localhost:8080
npm install
npm run dev
```

`EXPORT_ENABLED=false` runs an intake-only instance (useful behind a separate exporter).

### Try a submission

```bash
curl -i -X POST http://localhost:3000/api/inquiries \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 8f14e45f-ea3b-4a2f-9c3d-2c1a4b6e7d90' \
  -d '{
        "name": "Jane Doe",
        "email": "jane.doe@example.com",
        "phone": "+1 555 010 9999",
        "company": "Acme Pharma Ltd",
        "message": "We would like pricing for Cardolix 20 and the EU stability data.",
        "consent": true
      }'
```

Repeat the same command with the same `Idempotency-Key`: the second response is `200 DUPLICATE` with
the original reference and **no** second row is created.

---

## Testing

```bash
# backend: unit + slice + H2 integration tests (no Docker, no PostgreSQL needed)
cd backend && mvn verify

# frontend
cd frontend
npm run typecheck
npm run lint
npm run build
```

What the backend suite covers:

| Area                | Examples                                                                                          |
|---------------------|---------------------------------------------------------------------------------------------------|
| Sanitising          | Markup removal, control characters, Unicode normalisation, truncation, phone/e-mail normalisation. |
| Validation          | Field constraints evaluated on *sanitised* values; consent mandatory.                             |
| Excel sink          | 8 threads × 25 concurrent appends → exactly 200 rows; replay is a no-op; formula defusing; damaged-workbook quarantine; rebuild. |
| Export worker       | Success, sink failure, unexpected exception, failure while recording a failure, sweeper recovery, disabled pipeline. |
| Controller          | `201`/`200`/`400`/`415`/`500`, `Location` and `Cache-Control`, problem+json shape, no internal leakage. |
| Rate limiter        | Sliding windows, eviction under load, retry-after.                                                 |
| Integration (H2)    | Full context + filter chain: hostile payload persisted sanitised, idempotent replay, unknown-property rejection, no raw IP stored. |

---

## API

The full contract — including every problem+json shape — is in
[`docs/openapi.yaml`](docs/openapi.yaml).

| Method | Path                        | Purpose                                        |
|--------|-----------------------------|------------------------------------------------|
| `POST` | `/api/inquiries`            | Submit an inquiry. `201` new, `200` replay.     |
| `GET`  | `/api/inquiries`            | `405 method-not-allowed`.                       |
| `ANY`  | `/api/inquiries` (browser)  | The Next.js route handler proxies the above.    |
| `GET`  | `/actuator/health/liveness` | Process is alive.                               |
| `GET`  | `/actuator/health/readiness`| Database reachable.                             |
| `GET`  | `/actuator/prometheus`      | Metrics (private network only).                 |

Error codes: `validation-failed`, `malformed-request`, `method-not-allowed`,
`unsupported-media-type`, `not-found`, `conflict`, `forbidden`, `length-required`,
`payload-too-large`, `rate-limit-exceeded`, `storage-unavailable`, `internal-error`.

---

## Configuration

Every setting has a working default in `backend/src/main/resources/application.yml` and can be
overridden by an environment variable. The ones that matter in practice:

| Variable                    | Default                    | Why you would change it                                    |
|-----------------------------|----------------------------|-------------------------------------------------------------|
| `APP_HASH_SECRET`           | placeholder                | **Must** be replaced; keys PII pseudonymisation.             |
| `APP_CORS_ALLOWED_ORIGINS`  | `http://localhost:3000`    | Add your real site origin; wildcards are rejected.           |
| `APP_TRUST_PROXY_HEADERS`   | `false`                    | `true` only behind a proxy that overwrites `X-Forwarded-*`.  |
| `APP_REQUIRE_HTTPS`         | `false`                    | `true` in any deployed environment (mandatory with `prod`).  |
| `RATE_LIMIT_PER_MINUTE`     | `10`                       | Sustained per-client limit.                                  |
| `RATE_LIMIT_BURST`          | `5`                        | Per-second burst limit.                                      |
| `RATE_LIMIT_GLOBAL_PER_MINUTE` | `600`                   | Across all clients.                                          |
| `EXPORT_SINKS`              | `excel`                    | `excel`, `google-sheets` or both.                             |
| `GOOGLE_SHEETS_WEBHOOK_URL` | *(empty)*                  | Enables the Sheets sink.                                      |
| `DB_POOL_MAX_SIZE`          | `20`                       | Keep modest; Postgres likes not being oversubscribed.         |

Frontend (`frontend/.env.local`): `API_BASE_URL`, `NEXT_PUBLIC_SITE_URL`,
`CLIENT_IP_HASH_SECRET`, `RATE_LIMIT_PER_MINUTE`, `RATE_LIMIT_BURST`,
`INQUIRY_FORWARD_TIMEOUT_MS`.

Activating the `prod` profile makes the service refuse to start with a placeholder secret, an empty
CORS list or `require-https=false`.

---

## Documentation

| Document                       | Contents                                                                    |
|--------------------------------|-----------------------------------------------------------------------------|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | System design, request lifecycle, concurrency, failure semantics, trade-offs. |
| [`docs/SECURITY.md`](docs/SECURITY.md)         | Threat model, controls, data handling, secrets, hardening roadmap.            |
| [`docs/EXPORT.md`](docs/EXPORT.md)             | Outbox pipeline, Excel/Sheets sinks, configuration, operations runbook.       |
| [`docs/openapi.yaml`](docs/openapi.yaml)       | OpenAPI 3.1 description of the intake API.                                    |
| [`docs/google-apps-script/Code.gs`](docs/google-apps-script/Code.gs) | The Google Sheets ingest webhook.                 |

---

## Project layout

```
backend/    Spring Boot 4.1 service (Java 25, Maven) — intake, outbox, Excel/Sheets sinks, tests
frontend/   Next.js 16 site (React 19, Tailwind 4, Zod) — pages, catalogue, form, proxy route
docs/       architecture, security, export and API reference
docker-compose.yml   PostgreSQL 16 + API + site
.env.example         every variable the compose stack reads
```

### Tech stack

| Layer      | Choice                                                                    |
|------------|---------------------------------------------------------------------------|
| Frontend   | Next.js 16 (App Router), React 19, TypeScript 5.9, Tailwind CSS 4, Zod 4, react-hook-form 7 |
| Backend    | Spring Boot 4.1, Spring MVC, Spring Security 7, Spring Data JPA, Flyway, Bean Validation |
| Database   | PostgreSQL 16                                                             |
| Export     | Apache POI 5.5 (`.xlsx`), Google Apps Script webhook                      |
| Runtime    | JDK 25 bytecode (built and run on JDK 27), Node 24                        |
| Images     | `eclipse-temurin:25-jre-noble`, `node:24-alpine`, both non-root           |

---

## Operations

* **Health:** split liveness/readiness probes so a database blip does not get a healthy process killed.
* **Metrics** (`/actuator/prometheus`): `pharma.inquiries.accepted|duplicate|rate_limited|allowed`,
  `pharma.inquiries.intake` timer, `pharma.export.backlog`, `pharma.export.parked`,
  `pharma.export.rows{sink,result}`, `pharma.export.batches{result}`.
* **Alert on:** `pharma.export.parked > 0` (a lead is not in the spreadsheet) and a
  `pharma.export.backlog` that does not drain within ~10 minutes.
* **Logging:** structured, PII-free, correlation-id tagged (`X-Request-Id` is echoed end to end).
* **Backups:** `pg_dump` on a schedule. The workbook is regenerable; the database is not.

---

## Troubleshooting

| Symptom                                              | Likely cause and fix                                                                                  |
|------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `503 storage-unavailable`                            | Database unreachable. Check `DB_URL` and the container health.                                         |
| `429` with a long `Retry-After`                      | The client (or your test script) hit the per-minute limit. Raise `RATE_LIMIT_PER_MINUTE` locally.       |
| Every submission returns `502 upstream-unavailable`  | The site cannot reach the API. Check `API_BASE_URL` (inside Compose it is `http://backend:8080`).       |
| The service refuses to start under `prod`            | `ProductionConfigValidator`: set a real `APP_HASH_SECRET`, a CORS origin and `APP_REQUIRE_HTTPS=true`.  |
| `export.batch.failed` + `ExportLockTimeoutException` | The workbook is open in Excel. Close it; the sweeper retries.                                          |
| `export.excel.quarantined` in the logs               | The workbook was damaged; it was set aside and rebuilt. Regenerate fully with `--app.export.rebuild-on-start=true`. |
| Rows stuck in `FAILED`                               | Inspect `export_error`, fix the sink, then re-queue (see the runbook in `docs/EXPORT.md`).              |

---

## License

Proprietary. All rights reserved. Third-party components keep their own licences (see `pom.xml` and
`frontend/package.json`).
