package com.nkweb.pharma.security;

import com.nkweb.pharma.config.AppSecurityProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Keeps personally identifiable information out of logs, metrics and stored metadata.
 *
 * <p>Two mechanisms are used:
 * <ul>
 *   <li>{@code mask*}: irreversible, human readable partial masking used for support
 *       ("was my inquiry received?").</li>
 *   <li>{@link #fingerprint(String)}: keyed HMAC-SHA256. Referencing the same e-mail or IP always
 *       produces the same short token, which makes it possible to correlate abuse and to count
 *       unique senders <em>without</em> storing or logging the original value.</li>
 * </ul>
 */
@Component
public class PiiMasker {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int FINGERPRINT_LENGTH = 32;

    private final byte[] key;

    public PiiMasker(AppSecurityProperties properties) {
        this.key = properties.hashSecret().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Keyed, deterministic pseudonym of a value. Never reversible without the configured secret.
     *
     * @return 32 hex characters, or {@code null} when the input is {@code null}.
     */
    public String fingerprint(String value) {
        if (value == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(value.strip().toLowerCase(java.util.Locale.ROOT)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, FINGERPRINT_LENGTH / 2);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available in this JVM", e);
        }
    }

    /** {@code jane.doe@example.com -> j***@example.com} */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String domain = email.substring(at);
        return email.charAt(0) + "***" + domain;
    }

    /** {@code +1 555 010 9999 -> ***9999} */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digitsOnly = phone.replaceAll("\\D", "");
        return digitsOnly.length() <= 4 ? "***" : "***" + digitsOnly.substring(digitsOnly.length() - 4);
    }

    /** {@code Jane Doe -> J*** D***} keeps names out of the log stream. */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        StringBuilder masked = new StringBuilder();
        for (String part : name.strip().split("\\s+")) {
            if (masked.length() > 0) {
                masked.append(' ');
            }
            masked.append(part.charAt(0)).append("***");
        }
        return masked.toString();
    }

    /** Describes a message without revealing it. */
    public static String describeLength(String value) {
        return value == null ? "null" : value.length() + " chars";
    }

    /** Short digest used to detect truncated/garbled payloads in logs, e.g. for correlation ids. */
    public static String shortDigest(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)), 0, 6);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }
}
