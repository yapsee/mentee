package com.ecom.customer.customer;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CustomerRepository extends MongoRepository<Customer, String> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, String id);
}
