package com.ecom.order.order;

import com.ecom.order.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService service;

    @Test
    void create_withInvalidLine_returns400WithIndexedField() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId":"c-1","paymentMethod":"VISA","products":[{"productId":1,"quantity":0}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['products[0].quantity']").value("Quantity must be positive"));
    }

    @Test
    void create_withUnknownPaymentMethod_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId":"c-1","paymentMethod":"CASH","products":[{"productId":1,"quantity":1}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void create_whenStockIsShort_passesProductServiceErrorThrough() throws Exception {
        when(service.createOrder(any())).thenThrow(new BusinessException(HttpStatus.CONFLICT,
                "Insufficient stock", "Not enough stock for 1 product(s)",
                Map.of("shortages", List.of("Keychron K2 (id 1): requested 99, available 40"))));

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId":"c-1","paymentMethod":"VISA","products":[{"productId":1,"quantity":99}]}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient stock"))
                .andExpect(jsonPath("$.shortages[0]").value("Keychron K2 (id 1): requested 99, available 40"));
    }
}
