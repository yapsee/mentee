package com.ecom.customer.customer;

import com.ecom.customer.exception.CustomerNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer only: routing, validation and error format. The service is mocked. */
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService service;

    @Test
    void create_returns201WithLocation() throws Exception {
        when(service.createCustomer(any())).thenReturn(new CustomerResponse(
                "c-1", "Ada", "Lovelace", "ada@example.com", new AddressDto("Main Street", "12", "75001")));

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstname":"Ada","lastname":"Lovelace","email":"ada@example.com",
                                 "address":{"street":"Main Street","houseNumber":"12","zipCode":"75001"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/customers/c-1"))
                .andExpect(jsonPath("$.id").value("c-1"));
    }

    @Test
    void create_withInvalidEmailAndMissingAddress_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstname":"Ada","lastname":"Lovelace","email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").value("Email is not valid"))
                .andExpect(jsonPath("$.errors.address").value("Address is required"));
    }

    @Test
    void findById_unknown_returns404() throws Exception {
        when(service.findById("unknown")).thenThrow(new CustomerNotFoundException("unknown"));

        mockMvc.perform(get("/api/v1/customers/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No customer found with id unknown"));
    }
}
