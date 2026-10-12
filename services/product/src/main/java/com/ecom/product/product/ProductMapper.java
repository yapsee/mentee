package com.ecom.product.product;

import com.ecom.product.category.Category;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public Product toProduct(ProductRequest request, Category category) {
        return Product.builder()
                .name(request.name())
                .description(request.description())
                .availableQuantity(request.availableQuantity())
                .price(request.price())
                .category(category)
                .build();
    }

    public ProductResponse toResponse(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getAvailableQuantity(),
                product.getPrice(),
                category.getId(),
                category.getName(),
                category.getDescription()
        );
    }

    public ProductPurchaseResponse toPurchaseResponse(Product product, int quantity) {
        return new ProductPurchaseResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                quantity
        );
    }
}
