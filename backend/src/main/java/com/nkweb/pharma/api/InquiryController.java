package com.nkweb.pharma.api;

import com.nkweb.pharma.api.dto.InquiryRequest;
import com.nkweb.pharma.api.dto.InquiryResponse;
import com.nkweb.pharma.service.ClientMetadata;
import com.nkweb.pharma.service.ClientMetadataResolver;
import com.nkweb.pharma.service.InquiryService;
import com.nkweb.pharma.service.InquirySubmission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Public intake endpoint.
 *
 * <p>The controller is intentionally thin: transport concerns (validation annotations on the DTO,
 * metadata extraction) live here, everything else is in the service layer so that it can be reused
 * and tested without HTTP.
 *
 * <p>Responses:
 * <ul>
 *   <li>{@code 201 Created} – new inquiry stored and queued for export. {@code Location} points at
 *       the reference.</li>
 *   <li>{@code 200 OK} – an idempotent retry of an already accepted submission.</li>
 *   <li>{@code 400} – validation failure, malformed JSON or unsupported content type.</li>
 *   <li>{@code 413} – body larger than {@code app.request-limits.max-body-bytes}.</li>
 *   <li>{@code 429} – rate limit exceeded ({@code Retry-After} header).</li>
 *   <li>{@code 500} – unexpected failure; the body contains a correlation id, never a stack trace.</li>
 * </ul>
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class InquiryController {

    private final InquiryService inquiryService;
    private final ClientMetadataResolver metadataResolver;

    public InquiryController(InquiryService inquiryService, ClientMetadataResolver metadataResolver) {
        this.inquiryService = inquiryService;
        this.metadataResolver = metadataResolver;
    }

    @PostMapping(path = "/inquiries", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InquiryResponse> submit(@Valid @RequestBody InquiryRequest request,
                                                  HttpServletRequest httpRequest) {

        ClientMetadata metadata = metadataResolver.resolve(httpRequest);
        InquirySubmission submission = inquiryService.submit(request, metadata);

        String status = submission.duplicate()
                ? InquiryResponse.STATUS_DUPLICATE
                : InquiryResponse.STATUS_RECEIVED;
        InquiryResponse body = new InquiryResponse(
                submission.reference().toString(), status, submission.receivedAt());

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("/api/inquiries/" + submission.reference()));
        headers.setCacheControl("no-store");
        return ResponseEntity
                .status(submission.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .headers(headers)
                .body(body);
    }
}
