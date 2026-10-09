package com.ecom.customer.customer;

import com.ecom.customer.exception.CustomerNotFoundException;
import com.ecom.customer.exception.EmailAlreadyUsedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;

    public CustomerResponse createCustomer(CustomerRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }
        Customer saved = repository.save(mapper.toCustomer(request));
        return mapper.toResponse(saved);
    }

    public CustomerResponse updateCustomer(String id, CustomerRequest request) {
        Customer customer = repository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        if (repository.existsByEmailAndIdNot(request.email(), id)) {
            throw new EmailAlreadyUsedException(request.email());
        }
        customer.setFirstname(request.firstname());
        customer.setLastname(request.lastname());
        customer.setEmail(request.email());
        customer.setAddress(mapper.toAddress(request.address()));
        return mapper.toResponse(repository.save(customer));
    }

    public List<CustomerResponse> findAllCustomers() {
        return repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    public CustomerResponse findById(String id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    public boolean existsById(String id) {
        return repository.existsById(id);
    }

    public void deleteCustomer(String id) {
        if (!repository.existsById(id)) {
            throw new CustomerNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
