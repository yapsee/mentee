package com.ecom.payment.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,
        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,
        @NotNull(message = "Order id is required")
        Integer orderId,
        @NotBlank(message = "Order reference is required")
        String orderReference,
        @NotNull(message = "Customer is required")
        @Valid
        CustomerDto customer
) {
}
