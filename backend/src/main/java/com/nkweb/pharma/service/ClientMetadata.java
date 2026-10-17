package com.nkweb.pharma.service;

/**
 * Non-PII metadata attached to an inquiry.
 *
 * @param clientRequestId idempotency key; re-submitting the same submission returns the original
 *                        inquiry instead of creating a second row.
 * @param sourceIpHash    HMAC-SHA256 fingerprint of the client IP (never the address itself).
 * @param userAgent       truncated, sanitised user agent string used for abuse triage.
 */
public record ClientMetadata(String clientRequestId, String sourceIpHash, String userAgent) {
}
