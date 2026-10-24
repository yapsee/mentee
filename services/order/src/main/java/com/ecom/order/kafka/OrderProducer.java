package com.ecom.order.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProducer {

    private final KafkaTemplate<String, OrderConfirmation> kafkaTemplate;

    /** Key = order reference, so all events of one order stay in order on one partition. */
    public void sendOrderConfirmation(OrderConfirmation confirmation) {
        // send() can fail in two ways: the returned future fails (broker rejected the record), or
        // send() itself THROWS when Kafka is unreachable (no metadata after max.block.ms).
        // Either way the business operation already succeeded and must not be reported as failed:
        // log loudly and move on. Making the event itself reliable needs an outbox (see day-07.md).
        try {
            kafkaTemplate.send(KafkaTopicConfig.ORDER_TOPIC, confirmation.orderReference(), confirmation)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("FAILED to publish OrderConfirmation for order {}: no email will be sent",
                                    confirmation.orderReference(), ex);
                        } else {
                            log.info("OrderConfirmation for order {} published to {}-{}@{}",
                                    confirmation.orderReference(), result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (RuntimeException e) {
            log.error("FAILED to publish OrderConfirmation for order {} (Kafka unreachable): no email will be sent",
                    confirmation.orderReference(), e);
        }
    }
}
