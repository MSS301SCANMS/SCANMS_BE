package com.scanms.user.config;

import com.scanms.user.event.UserRegisteredEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Bean
    NewTopic userRegisteredTopic() {
        return TopicBuilder.name(UserRegisteredEvent.TOPIC).partitions(3).replicas(1).build();
    }
}
