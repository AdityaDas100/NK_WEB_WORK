package com.nkweb.pharma.service.export;

/**
 * Raised when rows could not be written to an export sink.
 *
 * <p>The exception is intentionally a plain runtime exception: the worker catches it, re-queues the
 * affected rows in the database and lets the sweeper retry, which means a temporary sink outage
 * (locked workbook on Windows, Google quota, network hiccup) never loses an inquiry.
 */
public class ExportException extends RuntimeException {

    public ExportException(String message) {
        super(message);
    }

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
