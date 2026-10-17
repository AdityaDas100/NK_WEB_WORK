package com.nkweb.pharma.api;

import com.nkweb.pharma.api.dto.InquiryRequest;
import com.nkweb.pharma.error.GlobalExceptionHandler;
import com.nkweb.pharma.service.ClientMetadata;
import com.nkweb.pharma.service.ClientMetadataResolver;
import com.nkweb.pharma.service.InquiryService;
import com.nkweb.pharma.service.InquirySubmission;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transport-level contract of {@code POST /api/inquiries}.
 *
 * <p>Built with {@code standaloneSetup} on purpose: this test is about the HTTP contract (status
 * codes, headers, problem+json shapes) and must not depend on the security or rate-limiting filter
 * chains, which have their own tests. The service and the metadata resolver are mocked, so a
 * failure here always points at the controller or the exception handler.
 */
class InquiryControllerTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-01-02T03:04:05Z");

    private InquiryService inquiryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        inquiryService = mock(InquiryService.class);
        ClientMetadataResolver metadataResolver = mock(ClientMetadataResolver.class);
        when(metadataResolver.resolve(any(HttpServletRequest.class)))
                .thenReturn(new ClientMetadata("junit-idempotency-key", "fingerprint", "JUnit/1.0"));

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new InquiryController(inquiryService, metadataResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    // ------------------------------------------------------------------
    // success paths
    // ------------------------------------------------------------------

    @Test
    @DisplayName("201 Created with the reference, a Location header and no-store caching")
    void acceptsANewInquiry() throws Exception {
        UUID reference = UUID.randomUUID();
        when(inquiryService.submit(any(InquiryRequest.class), any(ClientMetadata.class)))
                .thenReturn(new InquirySubmission(reference, RECEIVED_AT, false));

        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/inquiries/" + reference))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reference").value(reference.toString()))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.receivedAt").exists());
    }

    @Test
    @DisplayName("200 OK when an idempotency key is replayed")
    void reportsAReplayAsDuplicate() throws Exception {
        UUID reference = UUID.randomUUID();
        when(inquiryService.submit(any(InquiryRequest.class), any(ClientMetadata.class)))
                .thenReturn(new InquirySubmission(reference, RECEIVED_AT, true));

        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference.toString()))
                .andExpect(jsonPath("$.status").value("DUPLICATE"));
    }

    // ------------------------------------------------------------------
    // client errors
    // ------------------------------------------------------------------

    @Test
    @DisplayName("400 problem+json with per-field errors; the service is never reached")
    void rejectsAnInvalidPayload() throws Exception {
        String invalid = """
                {
                  "name": "",
                  "email": "not-an-email",
                  "phone": "1",
                  "message": "short",
                  "consent": false
                }
                """;

        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation-failed"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("name", "email", "phone", "message")))
                .andExpect(jsonPath("$.errors[*].message").exists());

        verifyNoInteractions(inquiryService);
    }

    @Test
    @DisplayName("400 malformed-request for a body that is not valid JSON")
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("malformed-request"));

        verifyNoInteractions(inquiryService);
    }

    @Test
    void rejectsAnUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(validPayload()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported-media-type"));
    }

    // ------------------------------------------------------------------
    // server errors
    // ------------------------------------------------------------------

    @Test
    @DisplayName("500 explains nothing: no class names, no SQL, no stack trace")
    void doesNotLeakInternalsOnUnexpectedFailure() throws Exception {
        when(inquiryService.submit(any(InquiryRequest.class), any(ClientMetadata.class)))
                .thenThrow(new IllegalStateException("jdbc:postgresql://pharma-db:5432/pharma refused"));

        mockMvc.perform(post("/api/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal-error"))
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("pharma-db"))))
                .andExpect(jsonPath("$.requestId").exists());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

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
