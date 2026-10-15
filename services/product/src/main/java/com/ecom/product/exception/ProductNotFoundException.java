package com.ecom.product.exception;

import java.util.List;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(List<Integer> ids) {
        super(ids.size() == 1
                ? "No product found with id " + ids.getFirst()
                : "No products found with ids " + ids);
    }
}
