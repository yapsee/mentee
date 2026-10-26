package com.ecom.payment.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    private static final String BODY = """
            {"amount":279.97,"paymentMethod":"VISA","orderId":1,"orderReference":"ORD-1",
             "customer":{"id":"c-1","firstname":"Ada","lastname":"Lovelace","email":"ada@example.com"}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService service;

    private PaymentResponse payment() {
        return new PaymentResponse(7, "PAY-1", new BigDecimal("279.97"), PaymentMethod.VISA,
                PaymentStatus.SUCCEEDED, 1, "ORD-1", null);
    }

    @Test
    void newPayment_returns201() throws Exception {
        when(service.createPayment(any())).thenReturn(new PaymentService.Result(payment(), true));

        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void alreadyPaid_returns200() throws Exception {
        when(service.createPayment(any())).thenReturn(new PaymentService.Result(payment(), false));

        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void invalidCustomerEmail_returns400WithNestedField() throws Exception {
        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("ada@example.com", "not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['customer.email']").value("Customer email is not valid"));
    }
}
