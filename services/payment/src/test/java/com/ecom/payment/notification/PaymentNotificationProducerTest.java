package com.ecom.payment.notification;

import com.ecom.payment.payment.PaymentMethod;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentNotificationProducerTest {

    @Test
    @SuppressWarnings("unchecked")
    void kafkaDown_neverFailsThePayment() {
        KafkaTemplate<String, PaymentConfirmation> template = mock(KafkaTemplate.class);
        // What Kafka really does when the broker is unreachable: send() throws after max.block.ms.
        when(template.send(anyString(), anyString(), any())).thenThrow(new KafkaException("Send failed",
                new TimeoutException("Topic payment-topic not present in metadata after 5000 ms.")));

        PaymentNotificationProducer producer = new PaymentNotificationProducer(template);

        assertThatCode(() -> producer.sendPaymentConfirmation(new PaymentConfirmation("ORD-1",
                new BigDecimal("10.00"), PaymentMethod.VISA, "Ada", "Lovelace", "ada@example.com")))
                .doesNotThrowAnyException();
    }
}
