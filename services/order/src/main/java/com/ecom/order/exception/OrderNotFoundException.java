package com.ecom.order.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Integer id) {
        super("No order found with id " + id);
    }
}
