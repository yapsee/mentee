package com.ecom.product.product;

import com.ecom.product.category.Category;
import com.ecom.product.category.CategoryRepository;
import com.ecom.product.exception.CategoryNotFoundException;
import com.ecom.product.exception.InsufficientStockException;
import com.ecom.product.exception.InvalidPurchaseException;
import com.ecom.product.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));
        Product saved = repository.save(mapper.toProduct(request, category));
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Integer id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ProductNotFoundException(List.of(id)));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * All-or-nothing: either every line is available and every stock is decremented,
     * or an exception is thrown and the transaction rolls back with no stock changed.
     */
    @Transactional
    public List<ProductPurchaseResponse> purchaseProducts(List<ProductPurchaseRequest> requests) {
        List<Integer> ids = requests.stream().map(ProductPurchaseRequest::productId).toList();
        Set<Integer> uniqueIds = new HashSet<>(ids);
        if (uniqueIds.size() != ids.size()) {
            throw new InvalidPurchaseException("Each product may appear only once per purchase");
        }

        // One query for all products (not one per line), rows locked until commit.
        Map<Integer, Product> products = repository.findAllByIdForUpdate(uniqueIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Integer> missing = ids.stream().filter(id -> !products.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ProductNotFoundException(missing);
        }

        // Check every line first, so the error lists all problems at once.
        List<String> shortages = new ArrayList<>();
        for (ProductPurchaseRequest request : requests) {
            Product product = products.get(request.productId());
            if (product.getAvailableQuantity() < request.quantity()) {
                shortages.add("%s (id %d): requested %d, available %d".formatted(
                        product.getName(), product.getId(), request.quantity(), product.getAvailableQuantity()));
            }
        }
        if (!shortages.isEmpty()) {
            throw new InsufficientStockException(shortages);
        }

        // No save() needed: the entities are managed, Hibernate flushes the changes on commit.
        return requests.stream().map(request -> {
            Product product = products.get(request.productId());
            product.setAvailableQuantity(product.getAvailableQuantity() - request.quantity());
            return mapper.toPurchaseResponse(product, request.quantity());
        }).toList();
    }
}
