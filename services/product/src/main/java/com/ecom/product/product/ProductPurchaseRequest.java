package com.ecom.product.product;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductPurchaseRequest(
        @NotNull(message = "Product id is required")
        Integer productId,
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        Integer quantity
) {
}
