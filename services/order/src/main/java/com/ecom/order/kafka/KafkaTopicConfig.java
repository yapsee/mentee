package com.ecom.order.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Spring's KafkaAdmin creates the topic at startup if it does not exist yet. */
@Configuration
public class KafkaTopicConfig {

    public static final String ORDER_TOPIC = "order-topic";

    @Bean
    NewTopic orderTopic() {
        return TopicBuilder.name(ORDER_TOPIC).partitions(3).replicas(1).build();
    }
}
