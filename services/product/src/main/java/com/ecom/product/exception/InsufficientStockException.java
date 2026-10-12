package com.ecom.product.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class InsufficientStockException extends RuntimeException {

    private final List<String> shortages;

    public InsufficientStockException(List<String> shortages) {
        super("Not enough stock for " + shortages.size() + " product(s)");
        this.shortages = shortages;
    }
}
