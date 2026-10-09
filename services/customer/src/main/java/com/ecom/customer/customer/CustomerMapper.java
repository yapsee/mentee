package com.ecom.customer.customer;

import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public Customer toCustomer(CustomerRequest request) {
        return Customer.builder()
                .firstname(request.firstname())
                .lastname(request.lastname())
                .email(request.email())
                .address(toAddress(request.address()))
                .build();
    }

    public CustomerResponse toResponse(Customer customer) {
        Address address = customer.getAddress();
        return new CustomerResponse(
                customer.getId(),
                customer.getFirstname(),
                customer.getLastname(),
                customer.getEmail(),
                address == null ? null
                        : new AddressDto(address.getStreet(), address.getHouseNumber(), address.getZipCode())
        );
    }

    public Address toAddress(AddressDto dto) {
        return Address.builder()
                .street(dto.street())
                .houseNumber(dto.houseNumber())
                .zipCode(dto.zipCode())
                .build();
    }
}
