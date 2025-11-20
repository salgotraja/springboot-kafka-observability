package app.js.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class WikimediaChangesProducerIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.wikimedia.stream-url", () -> "https://stream.wikimedia.org/v2/stream/recentchange");
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topic}")
    private String topic;

    @Test
    void shouldSendMessageToKafka() throws Exception {
        String testMessage = "{\"test\": \"message\"}";

        kafkaTemplate.send(topic, testMessage).get(10, TimeUnit.SECONDS);

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(Collections.singletonList(topic));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

            assertThat(records.count()).isGreaterThan(0);

            boolean found = false;
            for (ConsumerRecord<String, String> record : records) {
                if (record.value().equals(testMessage)) {
                    found = true;
                    break;
                }
            }
            assertThat(found).isTrue();
        }
    }

    @Test
    void kafkaTemplateShouldBeConfigured() {
        assertThat(kafkaTemplate).isNotNull();
    }

    private KafkaConsumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }
}
