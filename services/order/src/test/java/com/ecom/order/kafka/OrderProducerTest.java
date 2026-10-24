package com.ecom.order.kafka;

import com.ecom.order.customer.CustomerResponse;
import com.ecom.order.order.PaymentMethod;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderProducerTest {

    @Test
    @SuppressWarnings("unchecked")
    void kafkaDown_neverFailsTheOrder() {
        KafkaTemplate<String, OrderConfirmation> template = mock(KafkaTemplate.class);
        when(template.send(anyString(), anyString(), any())).thenThrow(new KafkaException("Send failed",
                new TimeoutException("Topic order-topic not present in metadata after 5000 ms.")));

        OrderProducer producer = new OrderProducer(template);

        assertThatCode(() -> producer.sendOrderConfirmation(new OrderConfirmation("ORD-1", new BigDecimal("10.00"),
                PaymentMethod.VISA, new CustomerResponse("c-1", "Ada", "Lovelace", "ada@example.com"), List.of())))
                .doesNotThrowAnyException();
    }
}
