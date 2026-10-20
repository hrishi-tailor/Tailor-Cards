package com.tailorcards.api.pipeline;

import com.tailorcards.api.pipeline.dto.IngestResult;
import com.tailorcards.api.pipeline.dto.PriceBatch;
import com.tailorcards.api.pipeline.dto.Watchlist;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Endpoints for the AWS price pipeline, outside the admin login. Every request must carry a valid signature
 * ({@link PipelineSignature}); the body is read as raw bytes so the signature is checked before anything is parsed.
 * When no shared secret is configured the endpoints answer 404, as if they didn't exist.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/pricing")
@RequiredArgsConstructor
public class PipelineController {

    static final String TIMESTAMP_HEADER = "X-TC-Timestamp";
    static final String SIGNATURE_HEADER = "X-TC-Signature";
    /** About 5,000 prices; anything larger is refused before it's verified or parsed. */
    static final int MAX_BODY_BYTES = 1024 * 1024;

    private final PipelineSignature signature;
    private final PipelinePricingService pricing;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @GetMapping("/watchlist")
    public Watchlist watchlist(HttpServletRequest request) {
        authorize(request, readBody(request));
        return pricing.watchlist();
    }

    @PostMapping("/snapshots")
    public IngestResult snapshots(HttpServletRequest request) {
        byte[] body = readBody(request);
        authorize(request, body);
        PriceBatch batch = parse(body);
        IngestResult result = pricing.record(batch);
        log.info("Pipeline run {}: saved {} prices, skipped {} already saved", result.runId(), result.saved(), result.duplicates());
        return result;
    }

    private void authorize(HttpServletRequest request, byte[] body) {
        if (!signature.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!signature.verify(request.getMethod(), request.getRequestURI(), body,
                request.getHeader(TIMESTAMP_HEADER), request.getHeader(SIGNATURE_HEADER))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired signature");
        }
    }

    private static byte[] readBody(HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
        }
        try (InputStream in = request.getInputStream()) {
            byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
            }
            return body;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unreadable request body");
        }
    }

    private PriceBatch parse(byte[] body) {
        PriceBatch batch;
        try {
            batch = objectMapper.readValue(body, PriceBatch.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed price batch");
        }
        if (batch == null) {
            throw new IllegalArgumentException("Malformed price batch");
        }
        Set<ConstraintViolation<PriceBatch>> violations = validator.validate(batch);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .limit(5)
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; ")));
        }
        return batch;
    }
}
