package com.ecom.order.order;

import com.ecom.order.product.PurchaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** No price and no total here: the server computes them from product-service. */
public record OrderRequest(
        @NotBlank(message = "Customer id is required")
        String customerId,
        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,
        @NotEmpty(message = "At least one product is required")
        List<@Valid PurchaseRequest> products
) {
}
