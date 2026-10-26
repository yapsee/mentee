package com.ecom.order.customer;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * OpenFeign: declare an interface, Spring generates the HTTP client.
 * name = the Eureka service id. No URL: Spring Cloud LoadBalancer asks Eureka for an instance.
 */
@FeignClient(name = "customer-service", path = "/api/v1/customers")
public interface CustomerClient {

    /** 404 from customer-service becomes a BusinessException "Customer not found" (FeignConfig). */
    @GetMapping("/{id}")
    CustomerResponse findById(@PathVariable String id);
}
