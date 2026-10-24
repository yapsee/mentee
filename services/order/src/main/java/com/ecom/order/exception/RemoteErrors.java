package com.ecom.order.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns an error response from another service into a BusinessException.
 * Our services all answer errors as RFC 9457 ProblemDetail, so we can keep their title, detail and
 * extra fields, and the client of order-service gets a useful message ("Insufficient stock", which product).
 */
@Component
@RequiredArgsConstructor
public class RemoteErrors {

    private static final Set<String> STANDARD_FIELDS = Set.of("type", "title", "status", "detail", "instance");

    private final JsonMapper jsonMapper;

    public BusinessException toBusinessException(String service, int status, byte[] body) {
        if (status >= 500 || status < 400) {
            // Their bug or outage is not the client's fault, and their internals must not leak.
            return unavailable(service);
        }
        Map<String, Object> problem = parse(body);
        Map<String, Object> extra = new LinkedHashMap<>(problem);
        extra.keySet().removeAll(STANDARD_FIELDS);
        return new BusinessException(
                HttpStatus.valueOf(status),
                String.valueOf(problem.getOrDefault("title", "Request rejected by " + service)),
                String.valueOf(problem.getOrDefault("detail", service + " answered " + status)),
                extra);
    }

    public BusinessException unavailable(String service) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                service + " is not reachable, please retry later");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(byte[] body) {
        try {
            return body == null || body.length == 0 ? Map.of() : jsonMapper.readValue(body, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
