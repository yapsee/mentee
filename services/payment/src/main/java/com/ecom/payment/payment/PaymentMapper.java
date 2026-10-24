package com.ecom.payment.payment;

import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getReference(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getOrderId(),
                payment.getOrderReference(),
                payment.getCreatedDate()
        );
    }
}
