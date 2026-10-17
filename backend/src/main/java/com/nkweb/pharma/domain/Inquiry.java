package com.nkweb.pharma.domain;

import com.nkweb.pharma.api.dto.InquiryRequest;
import com.nkweb.pharma.service.ClientMetadata;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A product/contact inquiry submitted through the public landing page.
 *
 * <p>The row is the system of record: it is committed before the client receives a response, and
 * the spreadsheet export is derived from it (see {@link ExportStatus}).
 */
@Entity
@Table(name = "inquiries")
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Opaque reference exposed to the client instead of the internal primary key. */
    @Column(name = "public_id", nullable = false, updatable = false, unique = true)
    private UUID publicId;

    /** Client-generated key that makes the intake endpoint idempotent. */
    @Column(name = "client_request_id", nullable = false, updatable = false, unique = true, length = 64)
    private String clientRequestId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "phone", nullable = false, length = 24)
    private String phone;

    @Column(name = "company", length = 120)
    private String company;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    @Column(name = "consent", nullable = false)
    private boolean consent;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_status", nullable = false, length = 16)
    private ExportStatus exportStatus = ExportStatus.PENDING;

    @Column(name = "export_attempts", nullable = false)
    private int exportAttempts;

    @Column(name = "export_error", length = 400)
    private String exportError;

    @Column(name = "exported_at")
    private Instant exportedAt;

    /** HMAC of the client IP; pseudonymous so the table holds no raw network identifier. */
    @Column(name = "source_ip_hash", length = 64)
    private String sourceIpHash;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: protects concurrent export claims from lost updates. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Inquiry() {
        // for JPA
    }

    /** Builds a new pending inquiry from an already validated and sanitised submission. */
    public static Inquiry create(InquiryRequest request, ClientMetadata metadata) {
        Inquiry inquiry = new Inquiry();
        inquiry.publicId = UUID.randomUUID();
        inquiry.clientRequestId = metadata.clientRequestId();
        inquiry.name = request.name();
        inquiry.email = request.email();
        inquiry.phone = request.phone();
        inquiry.company = request.company();
        inquiry.message = request.message();
        inquiry.consent = request.consent();
        inquiry.sourceIpHash = metadata.sourceIpHash();
        inquiry.userAgent = metadata.userAgent();
        inquiry.exportStatus = ExportStatus.PENDING;
        inquiry.exportAttempts = 0;
        return inquiry;
    }

    /** Marks the row as claimed by an export worker. */
    public void markProcessing() {
        this.exportStatus = ExportStatus.PROCESSING;
        this.exportAttempts = this.exportAttempts + 1;
    }

    /** Marks the row as successfully written to every configured sink. */
    public void markExported(Instant when) {
        this.exportStatus = ExportStatus.EXPORTED;
        this.exportError = null;
        this.exportedAt = when;
    }

    /**
     * Handles an export failure: re-queues the row until {@code maxAttempts} is reached, after
     * which it is parked as {@link ExportStatus#FAILED} so an operator can investigate.
     */
    public void markFailed(String error, int maxAttempts) {
        this.exportError = error == null ? null
                : error.substring(0, Math.min(error.length(), 400));
        this.exportStatus = this.exportAttempts >= maxAttempts ? ExportStatus.FAILED : ExportStatus.PENDING;
    }

    public boolean isReadyForRetry(int maxAttempts) {
        return exportStatus == ExportStatus.PENDING && exportAttempts < maxAttempts;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ---------------------------------------------------------------------
    // Accessors (no setters: state changes go through the intention revealing methods above)
    // ---------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getCompany() {
        return company;
    }

    public String getMessage() {
        return message;
    }

    public boolean isConsent() {
        return consent;
    }

    public ExportStatus getExportStatus() {
        return exportStatus;
    }

    public int getExportAttempts() {
        return exportAttempts;
    }

    public String getExportError() {
        return exportError;
    }

    public Instant getExportedAt() {
        return exportedAt;
    }

    public String getSourceIpHash() {
        return sourceIpHash;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Inquiry inquiry)) {
            return false;
        }
        return publicId != null && publicId.equals(inquiry.publicId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(publicId);
    }

    /** Never leaks PII: safe to use in logs and error messages. */
    @Override
    public String toString() {
        return "Inquiry[reference=" + publicId + ", status=" + exportStatus + "]";
    }
}
