package com.ecom.payment.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Integer id,
        String reference,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        Integer orderId,
        String orderReference,
        LocalDateTime createdDate
) {
}
