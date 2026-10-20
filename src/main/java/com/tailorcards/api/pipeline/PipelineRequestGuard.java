package com.tailorcards.api.pipeline;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Admits a request from the AWS pipeline: reads its raw body (bounded) and checks its signature before anything is
 * parsed. Answers 404 when no shared secret is configured, so the internal endpoints look absent, and 401 for an
 * unsigned, tampered or stale request.
 */
@Component
@RequiredArgsConstructor
public class PipelineRequestGuard {

    public static final String TIMESTAMP_HEADER = "X-TC-Timestamp";
    public static final String SIGNATURE_HEADER = "X-TC-Signature";
    /** About 5,000 prices; anything larger is refused before it's verified or parsed. */
    static final int MAX_BODY_BYTES = 1024 * 1024;

    private final PipelineSignature signature;

    /** Returns the verified body (empty for a GET). */
    public byte[] admit(HttpServletRequest request) {
        if (!signature.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        byte[] body = readBody(request);
        if (!signature.verify(request.getMethod(), signedPath(request), body,
                request.getHeader(TIMESTAMP_HEADER), request.getHeader(SIGNATURE_HEADER))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired signature");
        }
        return body;
    }

    /** The path as signed: with its query string, so paging parameters can't be changed in transit. */
    static String signedPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isEmpty() ? request.getRequestURI() : request.getRequestURI() + "?" + query;
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
}
