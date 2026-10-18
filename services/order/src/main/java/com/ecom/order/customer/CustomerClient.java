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

    @GetMapping("/exists/{id}")
    boolean existsById(@PathVariable String id);
}
