package com.ecom.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Spring's KafkaAdmin creates the topic at startup if it does not exist yet. */
@Configuration
public class KafkaTopicConfig {

    public static final String PAYMENT_TOPIC = "payment-topic";

    @Bean
    NewTopic paymentTopic() {
        return TopicBuilder.name(PAYMENT_TOPIC).partitions(3).replicas(1).build();
    }
}
