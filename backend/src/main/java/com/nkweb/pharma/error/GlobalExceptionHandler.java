package com.nkweb.pharma.error;

import com.nkweb.pharma.security.RequestTracingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Centralised error handling.
 *
 * <p>Two rules drive this class:
 * <ol>
 *   <li><b>Client mistakes are actionable.</b> Validation problems come back as RFC 9457
 *       {@code application/problem+json} with a per-field {@code errors[]} array that the Next.js
 *       form maps back onto the inputs.</li>
 *   <li><b>Server mistakes are opaque.</b> Unexpected exceptions are logged with their stack trace
 *       and a correlation id, but the response only carries that correlation id – no class names, no
 *       SQL, no stack trace, nothing that would help an attacker fingerprint the deployment.</li>
 * </ol>
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} is load bearing: with {@code spring.mvc.problemdetails}
 * enabled Spring Boot also registers its own {@code ProblemDetailsExceptionHandler} for the same
 * exceptions ({@code MethodArgumentNotValidException}, {@code HttpMessageNotReadableException},
 * {@code HttpMediaTypeNotSupportedException}, ...). That handler runs at order {@code 0}, so without
 * this annotation it would win the tie and the contract below (notably the {@code code} discriminator
 * the frontend keys off) would silently disappear. Ordering ours first keeps the built-in handler as
 * the fallback for anything we do not map explicitly.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://errors.nkethical.example/";
    private static final int MAX_REPORTED_FIELD_ERRORS = 20;

    /** Field level violations reported by {@code @Valid} on the request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBodyValidation(MethodArgumentNotValidException exception,
                                                              HttpServletRequest request) {
        List<FieldError> errors = new ArrayList<>();
        exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(),
                        error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldError::field))
                .limit(MAX_REPORTED_FIELD_ERRORS)
                .forEach(errors::add);

        exception.getBindingResult().getGlobalErrors().stream()
                .limit(MAX_REPORTED_FIELD_ERRORS)
                .forEach(error -> errors.add(new FieldError("_", error.getDefaultMessage())));

        log.info("request.rejected reason=validation errors={}", errors.size());
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed",
                "Validation failed", "One or more fields are invalid. See 'errors' for details.",
                request);
        problem.setProperty("errors", errors);
        return respond(problem, HttpStatus.BAD_REQUEST);
    }

    /** Violations raised for method parameters (query/path/header constraints). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException exception,
                                                               HttpServletRequest request) {
        List<FieldError> errors = exception.getAllErrors().stream()
                .limit(MAX_REPORTED_FIELD_ERRORS)
                .map(error -> new FieldError("request", error.getDefaultMessage()))
                .toList();

        log.info("request.rejected reason=parameter-validation errors={}", errors.size());
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed",
                "Validation failed", "One or more request parameters are invalid.", request);
        problem.setProperty("errors", errors);
        return respond(problem, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception,
                                                                  HttpServletRequest request) {
        List<FieldError> errors = exception.getConstraintViolations().stream()
                .limit(MAX_REPORTED_FIELD_ERRORS)
                .map(violation -> new FieldError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed",
                "Validation failed", "One or more fields are invalid.", request);
        problem.setProperty("errors", errors);
        return respond(problem, HttpStatus.BAD_REQUEST);
    }

    /** Unreadable JSON – wrong types, unknown properties, trailing garbage. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                              HttpServletRequest request) {
        // The parser message can echo parts of the payload: never forward it to the client.
        log.info("request.rejected reason=malformed-json detail={}",
                exception.getMostSpecificCause().getClass().getSimpleName());
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "malformed-request",
                "Malformed request", "The request body could not be parsed as a valid inquiry.",
                request);
        return respond(problem, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception,
                                                                    HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type",
                "Unsupported media type", "Submit the inquiry as application/json.", request);
        return respond(problem, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception,
                                                                  HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed",
                "Method not allowed", "This endpoint accepts POST requests only.", request);
        return respond(problem, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoResourceFoundException exception,
                                                        HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "not-found",
                "Not found", "The requested resource does not exist.", request);
        return respond(problem, HttpStatus.NOT_FOUND);
    }

    /** Unique constraint violations that are not explainable as a benign duplicate. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException exception,
                                                             HttpServletRequest request) {
        log.warn("request.rejected reason=data-integrity request_id={}",
                RequestTracingFilter.currentRequestId(), exception);
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "conflict",
                "Conflict", "The submission conflicts with an existing record.", request);
        return respond(problem, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidDataAccessResourceUsageException.class)
    public ResponseEntity<ProblemDetail> handleInvalidDataAccess(InvalidDataAccessResourceUsageException exception,
                                                                 HttpServletRequest request) {
        log.error("request.failed reason=invalid-data-access request_id={}",
                RequestTracingFilter.currentRequestId(), exception);
        return respond(problem(HttpStatus.SERVICE_UNAVAILABLE, "storage-unavailable",
                "Service unavailable",
                "The inquiry could not be stored. Please try again in a moment.", request),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Catch-all. Anything that reaches this point is by definition a bug or an outage: log it
     * completely, tell the client nothing but the correlation id.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        String requestId = RequestTracingFilter.currentRequestId();
        log.error("request.failed reason=unhandled method={} path={} request_id={}",
                request.getMethod(), request.getRequestURI(), requestId, exception);

        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Internal server error",
                "Something went wrong while processing your inquiry. "
                        + "Quote request id '" + requestId + "' if you contact support.", request);
        return respond(problem, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String title, String detail,
                                        HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_BASE + code));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("requestId", RequestTracingFilter.currentRequestId());
        return problem;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(problem);
    }

    /** Serialised as {@code {"field": "...", "message": "..."}}. */
    public record FieldError(String field, String message) {
    }
}
