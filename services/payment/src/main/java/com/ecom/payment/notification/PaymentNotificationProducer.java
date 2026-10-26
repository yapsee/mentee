package com.ecom.payment.notification;

import com.ecom.payment.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationProducer {

    private final KafkaTemplate<String, PaymentConfirmation> kafkaTemplate;

    /**
     * Key = order reference: all events of one order land on the same partition, so they stay in order.
     * The send is asynchronous: the HTTP response does not wait for Kafka.
     */
    public void sendPaymentConfirmation(PaymentConfirmation confirmation) {
        // send() can fail in two ways: the returned future fails (broker rejected the record), or
        // send() itself THROWS when Kafka is unreachable (no metadata after max.block.ms).
        // Either way the business operation already succeeded and must not be reported as failed:
        // log loudly and move on. Making the event itself reliable needs an outbox (see day-07.md).
        try {
            kafkaTemplate.send(KafkaTopicConfig.PAYMENT_TOPIC, confirmation.orderReference(), confirmation)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("FAILED to publish PaymentConfirmation for order {}: no email will be sent",
                                    confirmation.orderReference(), ex);
                        } else {
                            log.info("PaymentConfirmation for order {} published to {}-{}@{}",
                                    confirmation.orderReference(), result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (RuntimeException e) {
            log.error("FAILED to publish PaymentConfirmation for order {} (Kafka unreachable): no email will be sent",
                    confirmation.orderReference(), e);
        }
    }
}
