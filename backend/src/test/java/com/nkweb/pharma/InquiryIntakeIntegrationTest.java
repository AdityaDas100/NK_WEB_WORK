package com.nkweb.pharma;

import com.jayway.jsonpath.JsonPath;
import com.nkweb.pharma.domain.ExportStatus;
import com.nkweb.pharma.domain.Inquiry;
import com.nkweb.pharma.repository.InquiryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end intake test: real Spring context, real security filter chain, real rate limiter, real
 * (Flyway-migrated) schema.
 *
 * <p>Runs against H2 in PostgreSQL compatibility mode so that the suite needs no Docker; the export
 * pipeline is switched off for this profile because it is covered by the dedicated sink and worker
 * tests. What is verified <em>here</em> is the part that only exists when everything is wired
 * together: the filter chain is applied, sanitising and validation happen in the right order, the
 * row is durable and idempotency works across two independent HTTP requests.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class InquiryIntakeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InquiryRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("a hostile payload is sanitised, validated and persisted as PENDING")
    void persistsASanitisedInquiry() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "integration-key-accept-0001")
                        .header("User-Agent", "integration-test")
                        .content("""
                                {
                                  "name": "  <b>Jane</b>   Doe  ",
                                  "email": "JANE.DOE@Example.COM",
                                  "phone": "+1 (555) 010-9999",
                                  "company": "<i>Acme</i> Pharma Ltd",
                                  "message": "Hello <script>alert(1)</script> we need pricing for 50000 units.",
                                  "consent": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));

        List<Inquiry> stored = repository.findAll();
        assertThat(stored).hasSize(1);

        Inquiry inquiry = stored.getFirst();
        assertThat(inquiry.getName()).isEqualTo("Jane Doe");
        assertThat(inquiry.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(inquiry.getPhone()).isEqualTo("+1 (555) 010-9999");
        assertThat(inquiry.getCompany()).isEqualTo("Acme Pharma Ltd");
        assertThat(inquiry.getMessage())
                .doesNotContain("<")
                .doesNotContain(">")
                .doesNotContain("script")
                .contains("we need pricing for 50000 units.");

        // The outbox row is durable and untouched by the (disabled) exporter.
        assertThat(inquiry.getExportStatus()).isEqualTo(ExportStatus.PENDING);
        assertThat(inquiry.getExportAttempts()).isZero();
        assertThat(inquiry.getCreatedAt()).isNotNull();
        assertThat(inquiry.getVersion()).isZero();
    }

    @Test
    @DisplayName("a retried submission returns the original reference and creates no second row")
    void replayingAnIdempotencyKeyIsSafe() throws Exception {
        String idempotencyKey = "integration-key-idempotent-0001";

        String firstResponse = mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(validPayload()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String reference = JsonPath.read(firstResponse, "$.reference");

        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(validPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.reference").value(reference));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a second submission without an idempotency key is a new inquiry")
    void generatesAnIdempotencyKeyWhenTheClientSendsNone() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated());

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findAll())
                .extracting(Inquiry::getClientRequestId)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("an invalid payload is rejected with problem+json and stores nothing")
    void rejectsAnInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Jane Doe",
                                  "email": "not-an-email",
                                  "phone": "+1 555 010 9999",
                                  "message": "too short",
                                  "consent": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation-failed"))
                .andExpect(jsonPath("$.errors").isArray());

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("an unexpected property is rejected (the API fails closed)")
    void rejectsUnknownProperties() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Jane Doe",
                                  "email": "jane.doe@example.com",
                                  "phone": "+1 555 010 9999",
                                  "message": "We would like pricing for Cardolix 20, please.",
                                  "consent": true,
                                  "admin": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed-request"));

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("the raw client address never reaches the database")
    void storesOnlyAPseudonymousClientFingerprint() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.7")
                        .header("User-Agent", "integration-test/1.0")
                        .content(validPayload()))
                .andExpect(status().isCreated());

        Inquiry inquiry = repository.findAll().getFirst();
        assertThat(inquiry.getSourceIpHash())
                .isNotNull()
                .matches("[0-9a-f]{32}")
                .doesNotContain("127.0.0.1")
                .doesNotContain("203.0.113.7");
        assertThat(inquiry.getUserAgent()).isEqualTo("integration-test/1.0");
    }

    private static String validPayload() {
        return """
                {
                  "name": "Jane Doe",
                  "email": "jane.doe@example.com",
                  "phone": "+1 555 010 9999",
                  "company": "Acme Pharma Ltd",
                  "message": "We would like pricing for Cardolix 20 and the EU stability data.",
                  "consent": true
                }
                """;
    }
}
