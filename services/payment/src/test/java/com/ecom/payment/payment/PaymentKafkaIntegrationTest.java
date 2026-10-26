package com.ecom.payment.payment;

import com.ecom.payment.TestcontainersConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaAdmin;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real PostgreSQL + real Kafka (Testcontainers): checks what notification-service will actually receive. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentKafkaIntegrationTest {

    @Autowired
    private PaymentService service;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Test
    void payment_publishesOneConfirmation_evenWhenRequestedTwice() {
        String orderReference = "ORD-TEST-" + UUID.randomUUID().toString().substring(0, 6);
        PaymentRequest request = new PaymentRequest(new BigDecimal("279.97"), PaymentMethod.VISA, 1, orderReference,
                new CustomerDto("c-1", "Ada", "Lovelace", "ada@example.com"));

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaAdmin.getConfigurationProperties()
                        .get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("payment-topic"));

            assertThat(service.createPayment(request).created()).isTrue();
            assertThat(service.createPayment(request).created()).isFalse(); // retry: same payment

            List<ConsumerRecord<String, String>> ours = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(r -> {
                    if (orderReference.equals(r.key())) {
                        ours.add(r);
                    }
                });
                if (!ours.isEmpty() && System.currentTimeMillis() > deadline - 10_000) {
                    break; // got it, and waited a few more seconds for an unwanted duplicate
                }
            }

            assertThat(ours).hasSize(1);
            ConsumerRecord<String, String> record = ours.getFirst();
            assertThat(record.value())
                    .contains("\"orderReference\":\"" + orderReference + "\"")
                    .contains("\"customerEmail\":\"ada@example.com\"")
                    .contains("\"paymentMethod\":\"VISA\"");
            // The consumer (Day 8) maps this token to its own class: no shared Java classes needed.
            assertThat(new String(record.headers().lastHeader("__TypeId__").value(), StandardCharsets.UTF_8))
                    .isEqualTo("paymentConfirmation");
        }
    }
}
