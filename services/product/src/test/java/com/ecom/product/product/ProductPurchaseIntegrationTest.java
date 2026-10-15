package com.ecom.product.product;

import com.ecom.product.TestcontainersConfiguration;
import com.ecom.product.category.CategoryRepository;
import com.ecom.product.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against a real PostgreSQL (Testcontainers) with the real Flyway migrations. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProductPurchaseIntegrationTest {

    @Autowired
    private ProductService service;

    @Autowired
    private ProductRepository repository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Integer newProduct(int stock) {
        Integer categoryId = categoryRepository.findAll().getFirst().getId();
        return service.createProduct(new ProductRequest(
                "Test product", "For tests", stock, new BigDecimal("19.99"), categoryId)).id();
    }

    private int stockOf(Integer id) {
        return repository.findById(id).orElseThrow().getAvailableQuantity();
    }

    @Test
    void flywaySeed_isLoaded() {
        assertThat(categoryRepository.count()).isGreaterThanOrEqualTo(4);
        assertThat(service.findAll()).extracting(ProductResponse::name).contains("Keychron K2", "Sony WH-1000XM5");
    }

    @Test
    void failedPurchase_rollsBackEveryLine() {
        Integer a = newProduct(10);
        Integer b = newProduct(1);

        assertThatThrownBy(() -> service.purchaseProducts(List.of(
                new ProductPurchaseRequest(a, 4), new ProductPurchaseRequest(b, 2))))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(stockOf(a)).isEqualTo(10);
        assertThat(stockOf(b)).isEqualTo(1);
    }

    @Test
    void concurrentBuyersOfTheLastUnits_neverOversell() throws Exception {
        Integer id = newProduct(3);
        int buyers = 10;

        Callable<Boolean> buyOne = () -> {
            try {
                service.purchaseProducts(List.of(new ProductPurchaseRequest(id, 1)));
                return true;
            } catch (InsufficientStockException e) {
                return false;
            }
        };
        long succeeded;
        try (ExecutorService pool = Executors.newFixedThreadPool(buyers)) {
            List<Future<Boolean>> results = pool.invokeAll(IntStream.range(0, buyers).mapToObj(i -> buyOne).toList());
            succeeded = results.stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).count();
        }

        assertThat(succeeded).isEqualTo(3);
        assertThat(stockOf(id)).isZero();
    }
}
