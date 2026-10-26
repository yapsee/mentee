package com.ecom.order.customer;

/** The fields of customer-service's CustomerResponse that order-service needs. Unknown fields are ignored. */
public record CustomerResponse(
        String id,
        String firstname,
        String lastname,
        String email
) {
}
