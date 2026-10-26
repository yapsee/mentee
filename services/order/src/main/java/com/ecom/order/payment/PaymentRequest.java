package com.ecom.order.payment;

import com.ecom.order.customer.CustomerResponse;
import com.ecom.order.order.PaymentMethod;

import java.math.BigDecimal;

/** Same JSON as payment-service's PaymentRequest. */
public record PaymentRequest(
        BigDecimal amount,
        PaymentMethod paymentMethod,
        Integer orderId,
        String orderReference,
        CustomerResponse customer
) {
}
