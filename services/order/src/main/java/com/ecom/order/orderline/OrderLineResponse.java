package com.ecom.order.orderline;

public record OrderLineResponse(
        Integer id,
        Integer productId,
        Integer quantity
) {
}
