package com.ecom.customer.exception;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("A customer with email " + email + " already exists");
    }
}
