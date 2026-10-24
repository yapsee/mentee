package com.ecom.payment.payment;

import com.ecom.payment.exception.PaymentNotFoundException;
import com.ecom.payment.notification.PaymentConfirmation;
import com.ecom.payment.notification.PaymentNotificationProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;
    private final PaymentMapper mapper;
    private final PaymentNotificationProducer producer;

    public record Result(PaymentResponse payment, boolean created) {
    }

    /**
     * Idempotent: paying the same order twice returns the first payment and publishes nothing new.
     * That makes it safe for order-service to retry after a timeout.
     */
    public Result createPayment(PaymentRequest request) {
        var existing = repository.findByOrderReference(request.orderReference());
        if (existing.isPresent()) {
            log.info("Order {} already paid, returning payment {}", request.orderReference(),
                    existing.get().getReference());
            return new Result(mapper.toResponse(existing.get()), false);
        }

        // Saved (and committed) BEFORE publishing: never announce a payment that does not exist.
        Payment payment;
        try {
            payment = repository.saveAndFlush(Payment.builder()
                .reference(newReference())
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .status(PaymentStatus.SUCCEEDED)
                .orderId(request.orderId())
                .orderReference(request.orderReference())
                .customerId(request.customer().id())
                .build());
        } catch (DataIntegrityViolationException e) {
            // Two requests for the same order raced past the check above; the UNIQUE constraint on
            // order_reference let only one insert win. Answer the loser with the winner's payment.
            Payment winner = repository.findByOrderReference(request.orderReference()).orElseThrow(() -> e);
            return new Result(mapper.toResponse(winner), false);
        }

        producer.sendPaymentConfirmation(new PaymentConfirmation(
                payment.getOrderReference(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                request.customer().firstname(),
                request.customer().lastname(),
                request.customer().email()));

        return new Result(mapper.toResponse(payment), true);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(Integer id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private static String newReference() {
        return "PAY-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
