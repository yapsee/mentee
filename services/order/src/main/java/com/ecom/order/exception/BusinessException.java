package com.ecom.order.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * A failure the client should see as-is: a missing customer, a product out of stock, a service down.
 * Carries the HTTP status and any extra ProblemDetail fields (e.g. "shortages" from product-service).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final Map<String, Object> properties;

    public BusinessException(HttpStatus status, String title, String detail) {
        this(status, title, detail, Map.of());
    }

    public BusinessException(HttpStatus status, String title, String detail, Map<String, Object> properties) {
        super(detail);
        this.status = status;
        this.title = title;
        this.properties = properties;
    }
}
