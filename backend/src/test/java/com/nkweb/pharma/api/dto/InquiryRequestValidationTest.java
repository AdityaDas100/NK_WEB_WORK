package com.nkweb.pharma.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation tests for the intake payload.
 *
 * <p>Two properties are verified here and nothing else can replace them:
 * <ol>
 *   <li>the record's canonical constructor sanitises the input <em>before</em> validation runs;</li>
 *   <li>the constraints reject the payload shapes an attacker or a careless user would send.</li>
 * </ol>
 */
class InquiryRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("sanitises every field on construction")
    void sanitisesOnConstruction() {
        InquiryRequest request = new InquiryRequest(
                "  <b>Jane</b>   Doe ",
                "  JANE.Doe@Example.COM ",
                " +1 (555) 010-9999 ",
                "<i>Acme</i> Pharma Ltd",
                "Hello,\n\n\n\nwe need pricing for 50 000 units.\u0000",
                true);

        assertThat(request.name()).isEqualTo("Jane Doe");
        assertThat(request.email()).isEqualTo("jane.doe@example.com");
        assertThat(request.phone()).isEqualTo("+1 (555) 010-9999");
        assertThat(request.company()).isEqualTo("Acme Pharma Ltd");
        assertThat(request.message()).doesNotContain("\u0000").contains("we need pricing");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void acceptsAValidPayload() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void acceptsAPayloadWithoutTheOptionalOrganisation() {
        InquiryRequest request = new InquiryRequest(
                "Jane Doe", "jane@example.com", "+15550109999", null,
                "Please send your catalogue for the EU market.", true);

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>", " ", "A1", "@nope"})
    void rejectsNamesThatAreNotNames(String candidate) {
        InquiryRequest request = new InquiryRequest(
                candidate, "jane@example.com", "+15550109999", "",
                "Please send your catalogue for the EU market.", true);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "jane@example", "jane@@example.com", "@example.com"})
    void rejectsMalformedEmailAddresses(String candidate) {
        InquiryRequest request = new InquiryRequest(
                "Jane Doe", candidate, "+15550109999", "",
                "Please send your catalogue for the EU market.", true);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("email");
    }

    @Test
    void rejectsShortPhoneNumbers() {
        InquiryRequest request = new InquiryRequest(
                "Jane Doe", "jane@example.com", "12345", "",
                "Please send your catalogue for the EU market.", true);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("phone");
    }

    @Test
    void rejectsTooShortMessages() {
        InquiryRequest request = new InquiryRequest(
                "Jane Doe", "jane@example.com", "+15550109999", "", "hi", true);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("message");
    }

    @Test
    @DisplayName("consent is mandatory")
    void rejectsMissingConsent() {
        InquiryRequest request = new InquiryRequest(
                "Jane Doe", "jane@example.com", "+15550109999", "",
                "Please send your catalogue for the EU market.", false);

        Set<ConstraintViolation<InquiryRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("consent");
    }

    @Test
    @DisplayName("sanitising a payload cannot push a field over its limit")
    void sanitisedValuesFitTheColumnWidths() {
        InquiryRequest request = new InquiryRequest(
                "Jane Doe", "jane@example.com", "+15550109999",
                "A".repeat(500),
                "M".repeat(9000),
                true);

        assertThat(request.company()).hasSize(InquiryRequest.MAX_COMPANY_LENGTH);
        assertThat(request.message()).hasSize(InquiryRequest.MAX_MESSAGE_LENGTH);
        // The sanitiser truncates, so the payload would still pass validation but is reported here
        // as a deliberate design decision (see the class comment).
        assertThat(request.name()).hasSizeLessThanOrEqualTo(InquiryRequest.MAX_NAME_LENGTH);
    }

    private static InquiryRequest validRequest() {
        return new InquiryRequest(
                "Jane Doe",
                "jane.doe@example.com",
                "+1 555 010 9999",
                "Acme Pharma Ltd",
                "We would like pricing for Cardolix 20 and the stability data for the EU dossier.",
                true);
    }
}
