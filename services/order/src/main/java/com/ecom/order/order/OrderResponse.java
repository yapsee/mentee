package com.ecom.order.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Integer id,
        String reference,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        String customerId,
        LocalDateTime createdDate
) {
}
