package com.ecom.customer.exception;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(String id) {
        super("No customer found with id " + id);
    }
}
