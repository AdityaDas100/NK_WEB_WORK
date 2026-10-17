package com.nkweb.pharma.service;

import com.nkweb.pharma.security.ClientIpResolver;
import com.nkweb.pharma.security.InputSanitizer;
import com.nkweb.pharma.security.PiiMasker;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Extracts the transport metadata of a submission in a way that is safe to persist and to log.
 *
 * <p>The client supplied {@code Idempotency-Key} is only honoured when it matches a conservative
 * pattern; otherwise a fresh key is generated. Accepting arbitrary values would let a client
 * "reserve" keys that legitimately belong to someone else's submission.
 */
@Component
public class ClientMetadataResolver {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,63}$");
    private static final int MAX_USER_AGENT_LENGTH = 256;

    private final ClientIpResolver ipResolver;
    private final PiiMasker piiMasker;

    public ClientMetadataResolver(ClientIpResolver ipResolver, PiiMasker piiMasker) {
        this.ipResolver = ipResolver;
        this.piiMasker = piiMasker;
    }

    public ClientMetadata resolve(HttpServletRequest request) {
        String clientRequestId = normaliseRequestId(request.getHeader(IDEMPOTENCY_HEADER));
        String ipHash = piiMasker.fingerprint(ipResolver.resolve(request));
        String userAgent = InputSanitizer.text(
                request.getHeader(HttpHeaders.USER_AGENT), MAX_USER_AGENT_LENGTH);
        return new ClientMetadata(clientRequestId, ipHash, userAgent);
    }

    static String normaliseRequestId(String candidate) {
        if (candidate != null) {
            String trimmed = candidate.strip();
            if (SAFE_REQUEST_ID.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }
}
