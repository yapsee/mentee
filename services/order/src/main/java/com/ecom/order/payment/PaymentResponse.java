package com.ecom.order.payment;

/** The fields of payment-service's PaymentResponse that order-service needs. */
public record PaymentResponse(
        Integer id,
        String reference,
        String status
) {
}
