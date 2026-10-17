package com.nkweb.pharma.service.export;

/**
 * Raised when the workbook could not be locked within the configured timeout.
 *
 * <p>Separated from the generic {@link ExportException} so that a lock timeout (which is expected
 * while an operator has the file open in Excel) can be distinguished from a real write failure in
 * logs and alerts.
 */
public class ExportLockTimeoutException extends ExportException {

    public ExportLockTimeoutException(String message) {
        super(message);
    }
}
