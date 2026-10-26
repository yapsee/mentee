package com.ecom.payment.notification;

import com.ecom.payment.payment.PaymentMethod;

import java.math.BigDecimal;

/**
 * The event published on payment-topic. This JSON is a CONTRACT with notification-service (Day 8):
 * renaming or removing a field breaks the consumer. Add fields, never change existing ones.
 */
public record PaymentConfirmation(
        String orderReference,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String customerFirstname,
        String customerLastname,
        String customerEmail
) {
}
