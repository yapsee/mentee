package com.ecom.product.exception;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(Integer id) {
        super("No category found with id " + id);
    }
}
