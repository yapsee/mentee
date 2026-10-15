package com.ecom.product.product;

import java.math.BigDecimal;

/** One purchased line. order-service uses the price from here, never from the client. */
public record ProductPurchaseResponse(
        Integer productId,
        String name,
        String description,
        BigDecimal price,
        Integer quantity
) {
}
