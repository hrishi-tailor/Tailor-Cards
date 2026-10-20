package com.tailorcards.api.pipeline;

import com.tailorcards.api.pipeline.dto.IngestResult;
import com.tailorcards.api.pipeline.dto.PriceBatch;
import com.tailorcards.api.pipeline.dto.Watchlist;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Endpoints for the AWS price pipeline, outside the admin login. {@link PipelineRequestGuard} checks every request's
 * signature before its body is parsed; without a shared secret the endpoints answer 404, as if they didn't exist.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/pricing")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineRequestGuard guard;
    private final PipelinePricingService pricing;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @GetMapping("/watchlist")
    public Watchlist watchlist(HttpServletRequest request) {
        guard.admit(request);
        return pricing.watchlist();
    }

    @PostMapping("/snapshots")
    public IngestResult snapshots(HttpServletRequest request) {
        PriceBatch batch = parse(guard.admit(request));
        IngestResult result = pricing.record(batch);
        log.info("Pipeline run {}: saved {} prices, skipped {} already saved", result.runId(), result.saved(), result.duplicates());
        return result;
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
