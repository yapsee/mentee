package com.ecom.product.exception;

public class InvalidPurchaseException extends RuntimeException {

    public InvalidPurchaseException(String message) {
        super(message);
    }
}
