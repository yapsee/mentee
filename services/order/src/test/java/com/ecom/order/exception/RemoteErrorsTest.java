package com.ecom.order.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteErrorsTest {

    private final RemoteErrors remoteErrors = new RemoteErrors(JsonMapper.builder().build());

    @Test
    void clientError_keepsStatusTitleDetailAndExtraFields() {
        byte[] body = """
                {"type":"about:blank","title":"Insufficient stock","status":409,
                 "detail":"Not enough stock for 1 product(s)","instance":"/api/v1/products/purchase",
                 "shortages":["Samsung Odyssey G9 (id 6): requested 99, available 3"]}
                """.getBytes(StandardCharsets.UTF_8);

        BusinessException ex = remoteErrors.toBusinessException("product-service", 409, body);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getTitle()).isEqualTo("Insufficient stock");
        assertThat(ex.getMessage()).isEqualTo("Not enough stock for 1 product(s)");
        // The downstream "instance" path must not leak; the extra field must survive.
        assertThat(ex.getProperties()).containsOnlyKeys("shortages");
        assertThat(ex.getProperties().get("shortages"))
                .isEqualTo(List.of("Samsung Odyssey G9 (id 6): requested 99, available 3"));
    }

    @Test
    void serverError_becomes503WithoutLeakingDetails() {
        byte[] body = "{\"detail\":\"NullPointerException at ProductService.java:42\"}".getBytes();

        BusinessException ex = remoteErrors.toBusinessException("product-service", 500, body);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getMessage()).doesNotContain("NullPointer").contains("product-service");
    }

    @Test
    void nonJsonBody_stillGivesAUsefulError() {
        BusinessException ex = remoteErrors.toBusinessException("customer-service", 404, "<html>".getBytes());

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("customer-service answered 404");
    }
}
