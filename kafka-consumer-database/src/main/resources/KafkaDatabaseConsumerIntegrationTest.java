package app.js.kafka;

import app.js.entity.WikimediaEvent;
import app.js.repository.WikimediaEventRepository;
import app.js.service.EventPersistenceService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class KafkaDatabaseConsumerIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.mongodb.uri", mongodb::getReplicaSetUrl);
    }

    @Autowired
    private WikimediaEventRepository eventRepository;

    @Autowired
    private EventPersistenceService persistenceService;

    @Value("${app.kafka.topic}")
    private String topic;

    @Test
    void shouldConsumeMessageAndPersistToMongoDB() {
        String testEvent = "{\"title\": \"Test Page\", \"user\": \"TestUser\", \"comment\": \"Test edit\"}";

        try (KafkaProducer<String, String> producer = createProducer()) {
            producer.send(new ProducerRecord<>(topic, testEvent));
            producer.flush();
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<WikimediaEvent> events = eventRepository.findAll();
            assertThat(events).isNotEmpty();
            assertThat(events.get(0).getEventData()).isEqualTo(testEvent);
        });
    }

    @Test
    void shouldBatchMultipleMessages() {
        int messageCount = 150;

        try (KafkaProducer<String, String> producer = createProducer()) {
            for (int i = 0; i < messageCount; i++) {
                String event = "{\"title\": \"Page " + i + "\", \"user\": \"User" + i + "\"}";
                producer.send(new ProducerRecord<>(topic, event));
            }
            producer.flush();
        }

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = eventRepository.count();
            assertThat(count).isGreaterThanOrEqualTo(messageCount);
        });
    }

    @Test
    void eventPersistenceServiceShouldBeConfigured() {
        assertThat(persistenceService).isNotNull();
        assertThat(persistenceService.getQueueSize()).isGreaterThanOrEqualTo(0);
    }

    private KafkaProducer<String, String> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }
}
