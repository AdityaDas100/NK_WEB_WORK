package com.nkweb.pharma.api.dto;

import com.nkweb.pharma.security.InputSanitizer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Incoming inquiry payload.
 *
 * <p>Validation happens on the <em>sanitised</em> value: the canonical constructor removes markup,
 * control characters and Unicode ambiguities first, and Jakarta Bean Validation (JSR 380) is applied
 * to the cleaned result. This ordering matters – validating raw input would allow a payload that is
 * harmless after cleaning to be rejected, and worse, a payload that looks harmless before cleaning
 * to be accepted.
 *
 * <p>The declared limits mirror the database column widths, so a value that passes validation can
 * never be truncated by PostgreSQL or raise a database error.
 */
public record InquiryRequest(

        @NotBlank(message = "Please enter your name.")
        @Size(min = 2, max = 80, message = "Name must be between 2 and 80 characters.")
        @Pattern(regexp = "^[\\p{L}\\p{M}][\\p{L}\\p{M} .'\\-]*$",
                message = "Name may only contain letters, spaces, apostrophes, hyphens and dots.")
        String name,

        @NotBlank(message = "Please enter your e-mail address.")
        @Size(max = 254, message = "E-mail address is too long.")
        @Email(message = "Please enter a valid e-mail address.")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$",
                message = "Please enter a valid e-mail address.")
        String email,

        @NotBlank(message = "Please enter a phone number.")
        @Size(min = 7, max = 24, message = "Phone number must be between 7 and 24 characters.")
        @Pattern(regexp = "^\\+?[0-9][0-9 ()\\-./]*$",
                message = "Phone number may only contain digits, spaces and + - ( ) . /")
        String phone,

        @Size(max = 120, message = "Organisation must be at most 120 characters.")
        @Pattern(regexp = "^[\\p{L}\\p{M}0-9 .,&'()/\\-]*$",
                message = "Organisation contains unsupported characters.")
        String company,

        @NotBlank(message = "Please describe your inquiry.")
        @Size(min = 10, max = 2000, message = "Message must be between 10 and 2000 characters.")
        String message,

        @AssertTrue(message = "Please accept the privacy notice so we can process your inquiry.")
        boolean consent) {

    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MAX_PHONE_LENGTH = 24;
    public static final int MAX_COMPANY_LENGTH = 120;
    public static final int MAX_MESSAGE_LENGTH = 2000;

    public InquiryRequest {
        name = InputSanitizer.text(name, MAX_NAME_LENGTH);
        email = InputSanitizer.email(email);
        phone = InputSanitizer.phone(rawPhone(phone));
        company = InputSanitizer.text(company, MAX_COMPANY_LENGTH);
        message = InputSanitizer.multiline(message, MAX_MESSAGE_LENGTH);
    }

    /**
     * The phone sanitiser collapses unsupported characters into spaces, so the input is trimmed
     * once more here to keep values such as {@code "555 0100  "} from reaching the database.
     */
    private static String rawPhone(String phone) {
        return phone == null ? null : phone.strip();
    }
}
