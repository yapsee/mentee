package com.ecom.order.product;

import java.math.BigDecimal;

/** Same JSON as product-service's ProductPurchaseResponse. */
public record PurchaseResponse(
        Integer productId,
        String name,
        String description,
        BigDecimal price,
        Integer quantity
) {
}
