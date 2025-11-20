package app.js.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.dlq-topic}")
    private String dlqTopicName;

    @Bean
    public NewTopic dlqTopic() {
        return TopicBuilder.name(dlqTopicName)
                .build();
    }
}
