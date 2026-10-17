package com.nkweb.pharma.service.export;

import com.nkweb.pharma.domain.Inquiry;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot of an inquiry as it is handed to a sink.
 *
 * <p>Serialising the row here (instead of passing the JPA entity around) means the export runs
 * outside any persistence context: a slow sink can never keep a database transaction – or a
 * connection from the pool – open.
 */
public record InquiryExportRow(
        String reference,
        Instant receivedAt,
        String name,
        String email,
        String phone,
        String company,
        String message,
        boolean consent) {

    public static InquiryExportRow from(Inquiry inquiry) {
        return new InquiryExportRow(
                inquiry.getPublicId().toString(),
                inquiry.getCreatedAt(),
                inquiry.getName(),
                inquiry.getEmail(),
                inquiry.getPhone(),
                inquiry.getCompany(),
                inquiry.getMessage(),
                inquiry.isConsent());
    }

    public UUID referenceUuid() {
        return UUID.fromString(reference);
    }

    /** Shape used by the Google Sheets sink (and any future JSON based sink). */
    public Map<String, Object> toMap() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reference", reference);
        row.put("receivedAt", receivedAt == null ? null : receivedAt.toString());
        row.put("name", name);
        row.put("email", email);
        row.put("phone", phone);
        row.put("company", company);
        row.put("message", message);
        row.put("consent", consent);
        return row;
    }
}
