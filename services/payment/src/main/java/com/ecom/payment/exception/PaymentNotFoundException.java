package com.ecom.payment.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Integer id) {
        super("No payment found with id " + id);
    }
}
