package com.ecom.product.product;

import com.ecom.product.category.Category;
import com.ecom.product.category.CategoryRepository;
import com.ecom.product.exception.InsufficientStockException;
import com.ecom.product.exception.InvalidPurchaseException;
import com.ecom.product.exception.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private CategoryRepository categoryRepository;

    @Spy
    private ProductMapper mapper;

    @InjectMocks
    private ProductService service;

    private final Category keyboards = Category.builder().id(1).name("Keyboards").build();

    private Product product(int id, int stock) {
        return Product.builder().id(id).name("P" + id).description("d")
                .availableQuantity(stock).price(new BigDecimal("10.00")).category(keyboards).build();
    }

    @Test
    void purchase_decrementsStockAndReturnsPrices() {
        Product p1 = product(1, 5);
        Product p2 = product(2, 3);
        when(repository.findAllByIdForUpdate(any())).thenReturn(List.of(p1, p2));

        List<ProductPurchaseResponse> result = service.purchaseProducts(List.of(
                new ProductPurchaseRequest(1, 2), new ProductPurchaseRequest(2, 3)));

        assertThat(p1.getAvailableQuantity()).isEqualTo(3);
        assertThat(p2.getAvailableQuantity()).isZero();
        assertThat(result).extracting(ProductPurchaseResponse::quantity).containsExactly(2, 3);
        assertThat(result.getFirst().price()).isEqualByComparingTo("10.00");
    }

    @Test
    void purchase_withNotEnoughStock_changesNothing() {
        Product p1 = product(1, 5);
        Product p2 = product(2, 1);
        when(repository.findAllByIdForUpdate(any())).thenReturn(List.of(p1, p2));

        assertThatThrownBy(() -> service.purchaseProducts(List.of(
                new ProductPurchaseRequest(1, 2), new ProductPurchaseRequest(2, 4))))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(ex -> assertThat(((InsufficientStockException) ex).getShortages())
                        .containsExactly("P2 (id 2): requested 4, available 1"));

        // The first line was valid, but nothing may change when any line fails.
        assertThat(p1.getAvailableQuantity()).isEqualTo(5);
        assertThat(p2.getAvailableQuantity()).isEqualTo(1);
    }

    @Test
    void purchase_withUnknownProduct_listsMissingIds() {
        when(repository.findAllByIdForUpdate(any())).thenReturn(List.of(product(1, 5)));

        assertThatThrownBy(() -> service.purchaseProducts(List.of(
                new ProductPurchaseRequest(1, 1), new ProductPurchaseRequest(99, 1))))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("No product found with id 99");
    }

    @Test
    void purchase_withDuplicateProduct_isRejected() {
        assertThatThrownBy(() -> service.purchaseProducts(List.of(
                new ProductPurchaseRequest(1, 1), new ProductPurchaseRequest(1, 2))))
                .isInstanceOf(InvalidPurchaseException.class);
    }

    @Test
    void createProduct_withUnknownCategory_fails() {
        when(categoryRepository.findById(42)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.createProduct(
                new ProductRequest("X", "Y", 1, BigDecimal.ONE, 42)))
                .hasMessage("No category found with id 42");
    }
}
