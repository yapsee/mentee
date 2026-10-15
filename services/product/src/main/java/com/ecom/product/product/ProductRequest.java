package com.ecom.product.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "Name is required")
        String name,
        @NotBlank(message = "Description is required")
        String description,
        @NotNull(message = "Available quantity is required")
        @PositiveOrZero(message = "Available quantity cannot be negative")
        Integer availableQuantity,
        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        BigDecimal price,
        @NotNull(message = "Category id is required")
        Integer categoryId
) {
}
