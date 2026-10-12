package com.ecom.customer.customer;

import jakarta.validation.constraints.NotBlank;

public record AddressDto(
        @NotBlank(message = "Street is required")
        String street,
        @NotBlank(message = "House number is required")
        String houseNumber,
        @NotBlank(message = "Zip code is required")
        String zipCode
) {
}
