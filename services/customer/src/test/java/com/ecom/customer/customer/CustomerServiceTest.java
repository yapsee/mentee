package com.ecom.customer.customer;

import com.ecom.customer.exception.CustomerNotFoundException;
import com.ecom.customer.exception.EmailAlreadyUsedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository repository;

    @Spy
    private CustomerMapper mapper;

    @InjectMocks
    private CustomerService service;

    private final CustomerRequest request = new CustomerRequest(
            "Ada", "Lovelace", "ada@example.com",
            new AddressDto("Main Street", "12", "75001"));

    @Test
    void createCustomer_savesAndReturnsCustomer() {
        when(repository.existsByEmail("ada@example.com")).thenReturn(false);
        when(repository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId("c-1");
            return customer;
        });

        CustomerResponse response = service.createCustomer(request);

        assertThat(response.id()).isEqualTo("c-1");
        assertThat(response.email()).isEqualTo("ada@example.com");
        assertThat(response.address().zipCode()).isEqualTo("75001");
    }

    @Test
    void createCustomer_rejectsDuplicateEmail() {
        when(repository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createCustomer(request))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void findById_throwsWhenMissing() {
        when(repository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("unknown"))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void updateCustomer_replacesFields() {
        Customer existing = Customer.builder().id("c-1").firstname("Old").lastname("Name")
                .email("old@example.com").build();
        when(repository.findById("c-1")).thenReturn(Optional.of(existing));
        when(repository.existsByEmailAndIdNot("ada@example.com", "c-1")).thenReturn(false);
        when(repository.save(existing)).thenReturn(existing);

        CustomerResponse response = service.updateCustomer("c-1", request);

        assertThat(response.firstname()).isEqualTo("Ada");
        assertThat(response.address().street()).isEqualTo("Main Street");
    }

    @Test
    void deleteCustomer_throwsWhenMissing() {
        when(repository.existsById("unknown")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteCustomer("unknown"))
                .isInstanceOf(CustomerNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }
}
