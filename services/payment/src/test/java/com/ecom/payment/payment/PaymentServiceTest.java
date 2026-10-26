package com.ecom.payment.payment;

import com.ecom.payment.notification.PaymentConfirmation;
import com.ecom.payment.notification.PaymentNotificationProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository repository;
    @Mock
    private PaymentNotificationProducer producer;
    @Spy
    private PaymentMapper mapper;
    @InjectMocks
    private PaymentService service;

    private final PaymentRequest request = new PaymentRequest(new BigDecimal("279.97"), PaymentMethod.VISA, 1,
            "ORD-20261024-ABC123", new CustomerDto("c-1", "Ada", "Lovelace", "ada@example.com"));

    @Test
    void newPayment_isSavedAndPublishedOnce() {
        when(repository.findByOrderReference("ORD-20261024-ABC123")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(7);
            return payment;
        });

        PaymentService.Result result = service.createPayment(request);

        assertThat(result.created()).isTrue();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        ArgumentCaptor<PaymentConfirmation> event = ArgumentCaptor.forClass(PaymentConfirmation.class);
        verify(producer).sendPaymentConfirmation(event.capture());
        assertThat(event.getValue()).isEqualTo(new PaymentConfirmation("ORD-20261024-ABC123",
                new BigDecimal("279.97"), PaymentMethod.VISA, "Ada", "Lovelace", "ada@example.com"));
    }

    @Test
    void sameOrderTwice_returnsFirstPaymentAndPublishesNothing() {
        Payment first = Payment.builder().id(7).reference("PAY-1").orderReference("ORD-20261024-ABC123")
                .amount(new BigDecimal("279.97")).status(PaymentStatus.SUCCEEDED).build();
        when(repository.findByOrderReference("ORD-20261024-ABC123")).thenReturn(Optional.of(first));

        PaymentService.Result result = service.createPayment(request);

        assertThat(result.created()).isFalse();
        assertThat(result.payment().id()).isEqualTo(7);
        verify(repository, never()).saveAndFlush(any());
        verify(producer, never()).sendPaymentConfirmation(any());
    }

    @Test
    void concurrentDuplicate_losesTheRaceGracefully() {
        Payment winner = Payment.builder().id(8).reference("PAY-2").orderReference("ORD-20261024-ABC123").build();
        when(repository.findByOrderReference("ORD-20261024-ABC123"))
                .thenReturn(Optional.empty())       // the check: nothing yet
                .thenReturn(Optional.of(winner));   // after the UNIQUE violation
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        PaymentService.Result result = service.createPayment(request);

        assertThat(result.created()).isFalse();
        assertThat(result.payment().id()).isEqualTo(8);
        verify(producer, never()).sendPaymentConfirmation(any());
    }
}
