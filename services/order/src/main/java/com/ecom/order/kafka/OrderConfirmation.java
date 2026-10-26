package com.ecom.order.kafka;

import com.ecom.order.customer.CustomerResponse;
import com.ecom.order.order.PaymentMethod;
import com.ecom.order.product.PurchaseResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * The event published on order-topic: everything notification-service needs to write the
 * "order confirmed" email, so it never has to call order, customer or product services.
 * A CONTRACT: add fields, never rename or remove them.
 */
public record OrderConfirmation(
        String orderReference,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        CustomerResponse customer,
        List<PurchaseResponse> products
) {
}
