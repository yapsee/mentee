package com.ecom.product.handler;

import com.ecom.product.exception.CategoryNotFoundException;
import com.ecom.product.exception.InsufficientStockException;
import com.ecom.product.exception.InvalidPurchaseException;
import com.ecom.product.exception.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Same error format as customer-service: RFC 9457 ProblemDetail, plus an {@code errors} map. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ProductNotFoundException.class, CategoryNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Insufficient stock", ex.getMessage());
        problem.setProperty("shortages", ex.getShortages());
        return problem;
    }

    @ExceptionHandler(InvalidPurchaseException.class)
    public ProblemDetail handleInvalidPurchase(InvalidPurchaseException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid purchase", ex.getMessage());
    }

    // @Valid on a single request object (e.g. POST /products).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return validationProblem(errors);
    }

    // Constraints on a list body (e.g. POST /products/purchase with List<@Valid ...>).
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error -> {
            String field = error instanceof org.springframework.validation.FieldError fieldError
                    ? "[" + result.getContainerIndex() + "]." + fieldError.getField()
                    : "body";
            errors.putIfAbsent(field, error.getDefaultMessage());
        }));
        return validationProblem(errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "The request body is not valid JSON");
    }

    private ProblemDetail validationProblem(Map<String, String> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid");
        problem.setProperty("errors", errors);
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
