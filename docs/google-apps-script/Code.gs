/**
 * NK ETHICAL (IND) LTD - inquiry ingest webhook for Google Sheets.
 * =====================================================================
 *
 * Receives the batches that the Spring Boot export worker posts and appends them to a worksheet,
 * de-duplicating on the inquiry reference so that a retry after a crash cannot create a second copy
 * of the same lead.
 *
 * Setup
 * -----
 *   1. Open (or create) the target spreadsheet.
 *   2. Extensions > Apps Script, paste this file over the default `Code.gs`.
 *   3. Project Settings > Script properties, add:
 *        SHARED_TOKEN    = the same value as GOOGLE_SHEETS_TOKEN in the backend environment
 *        SPREADSHEET_ID  = the spreadsheet id (only needed when the script is NOT bound to it)
 *        SHEET_NAME      = optional, defaults to "Inquiries"
 *   4. Deploy > New deployment > Web app
 *        Execute as:        Me
 *        Who has access:    Anyone
 *   5. Copy the /exec URL into GOOGLE_SHEETS_WEBHOOK_URL on the backend.
 *
 * Why the token is in the body
 * ----------------------------
 * Apps Script web apps do not expose request headers to `doPost`, so an `Authorization: Bearer`
 * header is invisible to this code. The backend therefore also sends the shared secret as the
 * `token` field of the JSON body, which never appears in URL/access logs.
 *
 * Error behaviour
 * ---------------
 * Anything unexpected (bad token, missing configuration, lock timeout) THROWS. Apps Script answers
 * a thrown exception with HTTP 500, which is exactly what the backend needs: it treats a non-2xx
 * response as a failed batch, re-queues the rows and retries later.
 */

/** Column order; must match the Excel sink and the header written by writeHeader_(). */
var HEADERS = [
  'Reference',
  'Received (UTC)',
  'Name',
  'E-mail',
  'Phone',
  'Organisation',
  'Message',
  'Consent'
];

var DEFAULT_SHEET_NAME = 'Inquiries';
var LOCK_TIMEOUT_MS = 30000;

function doGet() {
  return json_({ ok: true, service: 'nk-ethical-inquiries', version: '1.0.0' });
}

function doPost(event) {
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(LOCK_TIMEOUT_MS)) {
    throw new Error('Could not acquire the script lock within ' + LOCK_TIMEOUT_MS + ' ms');
  }
  try {
    var payload = parseBody_(event);
    verifyToken_(payload.token);

    var rows = payload.rows;
    if (!Array.isArray(rows) || rows.length === 0) {
      return json_({ ok: true, appended: 0, skipped: 0 });
    }

    var sheet = resolveSheet_();
    var existing = readReferences_(sheet);

    var appended = 0;
    var skipped = 0;
    var toAppend = [];
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      var reference = String(row.reference === undefined || row.reference === null ? '' : row.reference);
      if (reference === '' || existing[reference]) {
        skipped++;
        continue;
      }
      existing[reference] = true;
      toAppend.push(toRow_(row));
      appended++;
    }

    if (toAppend.length > 0) {
      sheet
        .getRange(sheet.getLastRow() + 1, 1, toAppend.length, HEADERS.length)
        .setValues(toAppend);
    }

    return json_({ ok: true, appended: appended, skipped: skipped });
  } finally {
    lock.releaseLock();
  }
}

// ---------------------------------------------------------------------------
// internals
// ---------------------------------------------------------------------------

function parseBody_(event) {
  if (!event || !event.postData || !event.postData.contents) {
    throw new Error('Empty request body');
  }
  var parsed;
  try {
    parsed = JSON.parse(event.postData.contents);
  } catch (parseError) {
    throw new Error('Request body is not valid JSON');
  }
  if (!parsed || typeof parsed !== 'object') {
    throw new Error('Request body must be a JSON object');
  }
  return parsed;
}

/** Fails closed: without a configured token this endpoint refuses every request. */
function verifyToken_(presented) {
  var expected = PropertiesService.getScriptProperties().getProperty('SHARED_TOKEN');
  if (!expected) {
    throw new Error('SHARED_TOKEN script property is not configured');
  }
  if (typeof presented !== 'string' || presented !== expected) {
    throw new Error('Invalid token');
  }
}

function resolveSheet_() {
  var properties = PropertiesService.getScriptProperties();
  var sheetName = properties.getProperty('SHEET_NAME') || DEFAULT_SHEET_NAME;

  var spreadsheet;
  var spreadsheetId = properties.getProperty('SPREADSHEET_ID');
  if (spreadsheetId) {
    spreadsheet = SpreadsheetApp.openById(spreadsheetId);
  } else {
    spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  }
  if (!spreadsheet) {
    throw new Error('Neither SPREADSHEET_ID nor a container-bound spreadsheet is available');
  }

  var sheet = spreadsheet.getSheetByName(sheetName);
  if (!sheet) {
    sheet = spreadsheet.insertSheet(sheetName);
    writeHeader_(sheet);
  } else if (sheet.getLastRow() === 0) {
    writeHeader_(sheet);
  }
  return sheet;
}

function writeHeader_(sheet) {
  sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]);
  sheet.getRange(1, 1, 1, HEADERS.length).setFontWeight('bold');
  sheet.setFrozenRows(1);
}

/** Column A, so a replay is a no-op rather than a duplicated lead. */
function readReferences_(sheet) {
  var lastRow = sheet.getLastRow();
  var seen = {};
  if (lastRow < 2) {
    return seen;
  }
  var values = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
  for (var i = 0; i < values.length; i++) {
    var value = values[i][0];
    if (value !== '' && value !== null && value !== undefined) {
      seen[String(value).trim()] = true;
    }
  }
  return seen;
}

function toRow_(row) {
  return [
    defuse_(row.reference),
    toDate_(row.receivedAt),
    defuse_(row.name),
    defuse_(row.email),
    defuse_(row.phone),
    defuse_(row.company),
    defuse_(row.message),
    row.consent === true ? 'YES' : 'NO'
  ];
}

/**
 * Second line of defence against spreadsheet formula injection: the backend already prefixes
 * `= + - @` with an apostrophe, but this script can also be called directly, so it repeats the
 * check rather than trusting its caller.
 */
function defuse_(value) {
  if (value === null || value === undefined) {
    return '';
  }
  var text = String(value).replace(/^[\t\r\n\u0000\u000b\u000c]+/, '');
  if (text === '') {
    return '';
  }
  return '=+-@'.indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
}

function toDate_(value) {
  if (!value) {
    return '';
  }
  var date = new Date(value);
  return isNaN(date.getTime()) ? String(value) : date;
}

function json_(body) {
  return ContentService.createTextOutput(JSON.stringify(body)).setMimeType(
    ContentService.MimeType.JSON
  );
}
