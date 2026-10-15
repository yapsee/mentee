package com.ecom.product.product;

import com.ecom.product.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService service;

    @Test
    void purchase_withEmptyList_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/products/purchase")
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body").value("At least one product is required"));
    }

    @Test
    void purchase_withZeroQuantity_returns400WithIndexedField() throws Exception {
        mockMvc.perform(post("/api/v1/products/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"productId":1,"quantity":1},{"productId":2,"quantity":0}]
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['[1].quantity']").value("Quantity must be positive"));
    }

    @Test
    void purchase_withNotEnoughStock_returns409WithShortages() throws Exception {
        when(service.purchaseProducts(any())).thenThrow(
                new InsufficientStockException(List.of("P1 (id 1): requested 9, available 2")));

        mockMvc.perform(post("/api/v1/products/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"productId":1,"quantity":9}]
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.shortages[0]").value("P1 (id 1): requested 9, available 2"));
    }

    @Test
    void create_withNegativePrice_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","description":"Y","availableQuantity":1,"price":-5,"categoryId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price").value("Price must be positive"));
    }
}
