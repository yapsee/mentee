package com.ecom.customer.customer;

public record CustomerResponse(
        String id,
        String firstname,
        String lastname,
        String email,
        AddressDto address
) {
}
