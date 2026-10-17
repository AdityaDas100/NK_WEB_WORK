# Spreadsheet export

> How an inquiry gets from PostgreSQL into a spreadsheet — reliably, exactly once in effect, and
> without ever blocking the visitor's submission.

---

## 1. Why this shape

The naive implementation is "validate, then append a row, then return `201`". It has two fatal
properties: the response depends on a file/network operation that can be slow or fail, and a failure
after the database commit silently loses the lead.

This system inverts that:

1. the inquiry is **committed to PostgreSQL first** and only then acknowledged;
2. the export is a **derived artefact**, driven by a transactional outbox on the same row;
3. the spreadsheet can be **regenerated from the database** at any time.

Consequence: `POST /api/inquiries` never touches a file, a Google API or a lock. Its latency is one
INSERT.

---

## 2. State machine

```
              INSERT (same transaction as the inquiry)
                        │
                        ▼
   ┌───────────────► PENDING ──── claim ────► PROCESSING ──── sink ok ────► EXPORTED
   │                    ▲                         │
   │                    │                         │ attempts < max-attempts
   │                    └────── markFailed ───────┤
   │                                              │ attempts >= max-attempts
   │  sweeper: PROCESSING older than 5 min        ▼
   └───────────────────────────────────────── FAILED   (needs an operator)
```

| Column            | Meaning                                                                |
|-------------------|------------------------------------------------------------------------|
| `export_status`   | `PENDING` → `PROCESSING` → `EXPORTED`, or `FAILED` after the last retry. |
| `export_attempts` | Incremented on every claim; the ceiling is `app.export.max-attempts` (default 6). |
| `export_error`    | Sanitised, truncated failure reason (max 400 chars) for the operator.   |
| `exported_at`     | When the row reached every configured sink.                             |

`FAILED` is **not** a data-loss state: the inquiry is intact and can be re-queued.

---

## 3. Two triggers, one drain

| Trigger                                    | Purpose                                                                          |
|--------------------------------------------|----------------------------------------------------------------------------------|
| `@TransactionalEventListener(AFTER_COMMIT)` on `InquiryCreatedEvent` | Fast path: export right after the commit, on the async executor, so HTTP latency is unaffected. |
| `@Scheduled` sweeper (default every 15 s)   | Backstop: re-queues abandoned `PROCESSING` rows and drains anything pending — crashed process, sink outage, locked file. |

Both call the same `drainPending()`. A fair `ReentrantLock` means a second caller in the same JVM
returns immediately: the running drain will pick up its rows anyway. Dropping an event therefore
cannot lose data, which is what makes the fast path best-effort and the pipeline at-least-once.

**Claiming** is `SELECT … FOR UPDATE SKIP LOCKED`:

```sql
SELECT * FROM inquiries
 WHERE export_status = 'PENDING'
   AND export_attempts < :maxAttempts
 ORDER BY id
 LIMIT :batchSize
 FOR UPDATE SKIP LOCKED
```

Two instances asking at the same time receive *disjoint* sets. The transaction that claims rows
commits **before** any I/O happens, so a slow or hung sink cannot pin a HikariCP connection — the
most common way an "async export" takes a production database down with it.

---

## 4. The Excel sink

`ExcelFileExportSink` appends to a workbook (default `./data/exports/inquiries.xlsx`, sheet
`Inquiries`).

### Concurrency and crash safety

| Hazard                                            | Handling                                                                        |
|---------------------------------------------------|---------------------------------------------------------------------------------|
| Two threads of one JVM read-modify-write           | Per-path `ReentrantLock`.                                                        |
| A second process (another instance, a cron job)    | `FileLock` on a sibling `inquiries.xlsx.lock`.                                   |
| Crash mid-write                                    | Temp file in the same directory, then `ATOMIC_MOVE` over the target — readers see the old or the new file, never a partial one. |
| The workbook is open in Excel on Windows           | Lock acquisition times out after 20 s → `ExportLockTimeoutException` → transient failure, retried by the sweeper. |
| A replay after a crash between write and DB update | The reference column is read first and duplicates are skipped.                   |
| Corrupted / truncated workbook                     | Quarantined to `inquiries.xlsx.<epoch>.corrupt`, a fresh workbook is started, the row is written. |
| Network share without atomic moves                 | Falls back to a plain replace; the write lock still serialises writers.          |

Deduplication is **per reference**, and it is what makes the whole pipeline idempotent in effect:
sinks may be called more than once for the same row, and the second call is a no-op.

### Formula injection

Any cell whose text begins with `=`, `+`, `-` or `@` is prefixed with `'`, and leading control
characters (`TAB`, `CR`, `LF`, `NUL`) are removed first because some parsers skip them when looking
for the formula marker. A payload such as `=cmd|'/c calc'!A0` therefore lands as literal text
instead of executing on the reviewer's workstation.

### Columns

| # | Column          | Notes                                             |
|---|-----------------|---------------------------------------------------|
| 1 | Reference       | Plain-text UUID; the dedupe key.                   |
| 2 | Received (UTC)  | Real date cell, formatted `yyyy-mm-dd hh:mm:ss`.   |
| 3 | Name            |                                                    |
| 4 | E-mail          |                                                    |
| 5 | Phone           |                                                    |
| 6 | Organisation    |                                                    |
| 7 | Message         | Wrapped text, top-aligned.                         |
| 8 | Consent         | `YES` / `NO`.                                      |

The header row is frozen and an auto-filter spans the data.

---

## 5. The Google Sheets sink

`GoogleSheetsExportSink` posts batches to an **Apps Script web app** owned by the spreadsheet owner.

Why not the Sheets API with a service account:

* no long-lived JSON key to store, rot, or leak;
* it works with personal Google accounts and with Workspace tenants that forbid service accounts;
* the spreadsheet keeps its own permission model;
* the script can de-duplicate on the reference, which is exactly the contract a sink must honour.

### Setup

1. Create/open the target spreadsheet → **Extensions ▸ Apps Script**.
2. Replace `Code.gs` with [`google-apps-script/Code.gs`](google-apps-script/Code.gs).
3. **Project Settings ▸ Script properties**:

   | Property         | Required | Value                                                    |
   |------------------|----------|----------------------------------------------------------|
   | `SHARED_TOKEN`   | yes      | A long random string; identical to `GOOGLE_SHEETS_TOKEN`. |
   | `SPREADSHEET_ID` | no       | Only for a standalone script not bound to the sheet.      |
   | `SHEET_NAME`     | no       | Defaults to `Inquiries`.                                  |

4. **Deploy ▸ New deployment ▸ Web app** — *Execute as:* **Me**, *Who has access:* **Anyone**.
5. Put the `/exec` URL and the token into the backend:

   ```env
   EXPORT_SINKS=excel,google-sheets
   GOOGLE_SHEETS_WEBHOOK_URL=https://script.google.com/macros/s/AKfy…/exec
   GOOGLE_SHEETS_TOKEN=<the SHARED_TOKEN value>
   ```

### How it behaves

* The script takes `LockService.getScriptLock()` so two concurrent batches cannot interleave.
* It reads column A and skips references that are already present → replay-safe.
* The token is validated from the JSON **body** (`token`), because Apps Script cannot read request
  headers. It is deliberately not put in the URL, where it would end up in access logs.
* Any failure **throws**, which makes Apps Script answer `500`, which the sink turns into a failed
  batch → the rows are re-queued and retried. (A `200` with an `{ok:false}` body would silently mark
  rows as exported, so the script never uses that shape.)
* The script repeats the formula-injection defusing — it must also be safe when called directly.

`ExportSinkRouter` fans a batch out to every configured sink. If one fails, the batch is reported as
failed: rows are re-queued, and the sink that already succeeded skips them on the retry because it is
idempotent per reference. That is "at least once delivery, effectively once applied", without a
distributed transaction.

---

## 6. Configuration reference

| Property                                   | Env var                     | Default            | Notes                                             |
|--------------------------------------------|-----------------------------|--------------------|---------------------------------------------------|
| `app.export.enabled`                       | `EXPORT_ENABLED`            | `true`             | `false` stores inquiries but never exports.        |
| `app.export.sinks`                         | `EXPORT_SINKS`              | `excel`            | `excel`, `google-sheets` or `excel,google-sheets`. |
| `app.export.directory`                     | `EXPORT_DIRECTORY`          | `./data/exports`   | Created on demand.                                 |
| `app.export.file-name`                     | `EXPORT_FILE_NAME`          | `inquiries.xlsx`   |                                                    |
| `app.export.sheet-name`                    | –                           | `Inquiries`        | An existing first sheet is adopted, not duplicated.|
| `app.export.batch-size`                    | –                           | `250`              | Rows per claim (1–5000).                           |
| `app.export.max-attempts`                  | –                           | `6`                | After this the row is parked as `FAILED`.          |
| `app.export.sweep-interval`                | –                           | `15s`              | Recovery sweep cadence.                            |
| `app.export.initial-delay`                 | –                           | `20s`              | Delay before the first sweep.                      |
| `app.export.rebuild-on-start`              | –                           | `false`            | One-shot rebuild from the database, then exits.    |
| `app.export.google-sheets.webhook-url`     | `GOOGLE_SHEETS_WEBHOOK_URL` | *(empty)*          | Blank disables the sink entirely.                  |
| `app.export.google-sheets.bearer-token`    | `GOOGLE_SHEETS_TOKEN`       | *(empty)*          | Sent as `Authorization` **and** as `token` in the body. |
| `app.export.google-sheets.timeout`         | –                           | `10s`              | Connect and read timeout per request.              |

Unknown sink names fail fast at startup: `app.export.sinks` is validated against the registry.

---

## 7. Operations runbook

### Metrics to watch

| Metric                                                       | Meaning                                                          |
|--------------------------------------------------------------|------------------------------------------------------------------|
| `pharma.export.backlog`                                      | Rows still `PENDING`. Should hover near zero.                     |
| `pharma.export.parked`                                       | Rows `FAILED`. **Any non-zero value means a lead is not in the sheet.** |
| `pharma.export.rows{sink,result="exported"}`                 | Throughput.                                                      |
| `pharma.export.rows{sink="excel",result="skipped_duplicate"}`| Replays absorbed by the dedupe — should be small but non-zero occasionally. |
| `pharma.export.batches{result="failed"}`                     | Sink failures.                                                   |
| `pharma.export.rows{result="recovered"}`                     | Rows recovered from a crashed worker.                            |

### Log events

`export.drain.completed`, `export.batch.failed`, `export.recovered_abandoned`,
`export.excel.appended`, `export.excel.quarantined`, `export.marked.exported.mismatch`,
`export.rebuild.started`, `export.rebuild.completed`. All PII-free.

### Re-queue parked rows after fixing the cause

```sql
-- Inspect first: what went wrong, and when?
SELECT public_id, export_status, export_attempts, export_error, created_at
  FROM inquiries
 WHERE export_status = 'FAILED'
 ORDER BY created_at DESC;

-- Then give them another chance.
UPDATE inquiries
   SET export_status = 'PENDING', export_attempts = 0, export_error = NULL
 WHERE export_status = 'FAILED';
```

### Rebuild the workbook from the database

The database is the system of record, so a lost, quarantined or hand-edited workbook can be
regenerated:

```bash
java -jar pharma-inquiry-service.jar --app.export.rebuild-on-start=true
```

Or, in Compose:

```bash
docker compose run --rm backend \
  java -jar /app/app.jar --app.export.rebuild-on-start=true --app.export.enabled=false
```

The rebuild takes the same write lock as the worker, so it can never interleave with an append. It
rewrites the file from scratch and de-duplicates references.

### Rows stuck in `PROCESSING`

The sweeper re-queues them after five minutes of inactivity, so no manual action is normally needed.
If they persist, the sweeper is not running — check the `export.recovered_abandoned` log event and
whether the scheduler is enabled.

### The workbook is locked by an operator

`export.batch.failed` with `ExportLockTimeoutException` is expected while somebody has the file open
in Excel on Windows. It resolves itself; the retries are visible in `export_attempts`.

---

## 8. Scaling notes

| Situation                                        | Change                                                                                              |
|--------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| Several application instances                    | Nothing. `SKIP LOCKED` + the cross-process file lock already handle it — put the workbook on shared storage or use the Sheets sink. |
| The workbook grows into tens of thousands of rows| Switch to one file per day (`app.export.file-name`) or move to the Google Sheets sink. The pipeline is unchanged. |
| Export latency becomes visible to operators      | Reduce `sweep-interval`; raise `batch-size` if the sinks handle larger batches.                       |
| Higher durability required                       | Replace the outbox drain with a real broker (RabbitMQ/Kafka) behind the same `ExportSink` contract. The transactional-outbox columns stay as the producer-side guarantee. |
| A `--app.export.rebuild-on-start=true` run       | Run it with `--app.export.enabled=false` so the worker does not race the rebuild.                     |
