package com.ecom.order.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", path = "/api/v1/payments")
public interface PaymentClient {

    /** Idempotent on payment-service's side: same order reference = same payment, no double charge. */
    @PostMapping
    PaymentResponse requestPayment(@RequestBody PaymentRequest request);
}
