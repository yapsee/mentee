package com.ecom.payment.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    /** 201 for a new payment, 200 when this order was already paid (idempotent retry). */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@RequestBody @Valid PaymentRequest request) {
        PaymentService.Result result = service.createPayment(request);
        if (!result.created()) {
            return ResponseEntity.ok(result.payment());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(result.payment().id()).toUri();
        return ResponseEntity.created(location).body(result.payment());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> findById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }
}
