package com.polygis.common;

import java.io.IOException;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * One shape for every error, from controllers and from servlet filters alike:
 * {@code {type, title, status, detail, code, requestId}}. {@code code} is the stable key the SPA switches on;
 * {@code requestId} matches the X-Request-Id header and the server logs.
 */
public final class Problems {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        decorate(pd, code);
        return pd;
    }

    public static void decorate(ProblemDetail pd, String code) {
        if (pd.getProperties() == null || !pd.getProperties().containsKey("code")) pd.setProperty("code", code);
        String requestId = MDC.get("requestId");
        if (requestId != null) pd.setProperty("requestId", requestId);
    }

    /** For filters, which run outside Spring MVC. Values are JSON-encoded, never concatenated. */
    public static void write(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        var body = new java.util.LinkedHashMap<String, Object>(Map.of(
                "type", "about:blank", "title", status.getReasonPhrase(), "status", status.value(),
                "detail", detail, "code", code));
        String requestId = MDC.get("requestId");
        if (requestId != null) body.put("requestId", requestId);
        JSON.writeValue(response.getWriter(), body);
    }
}
