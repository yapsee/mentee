package com.ecom.order.product;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Same JSON as product-service's ProductPurchaseRequest. Each service owns its copy of the contract. */
public record PurchaseRequest(
        @NotNull(message = "Product id is required")
        Integer productId,
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        Integer quantity
) {
}
