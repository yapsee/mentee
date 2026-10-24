package com.ecom.payment.payment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Who pays. Sent by order-service so the confirmation email can be addressed without another call. */
public record CustomerDto(
        @NotBlank(message = "Customer id is required")
        String id,
        @NotBlank(message = "Customer firstname is required")
        String firstname,
        @NotBlank(message = "Customer lastname is required")
        String lastname,
        @NotBlank(message = "Customer email is required")
        @Email(message = "Customer email is not valid")
        String email
) {
}
