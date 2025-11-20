package app.js;

import app.js.repository.FailedEventRepository;
import app.js.repository.WikimediaEventRepository;
import app.js.service.EventPersistenceService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;

import java.util.Properties;

@SpringBootTest
@Import(ContainersConfig.class)
@ActiveProfiles("test")
public abstract class AbstractIT {

    @Autowired
    protected WikimediaEventRepository eventRepository;

    @Autowired
    protected FailedEventRepository failedEventRepository;

    @Autowired
    protected EventPersistenceService persistenceService;

    @Autowired
    protected KafkaContainer kafkaContainer;

    @Value("${app.kafka.topic}")
    protected String topic;

    @BeforeEach
    void cleanUp() {
        eventRepository.deleteAll();
        failedEventRepository.deleteAll();
    }

    protected KafkaProducer<String, String> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    protected void sendMessage(String message) {
        try (KafkaProducer<String, String> producer = createProducer()) {
            producer.send(new ProducerRecord<>(topic, message));
            producer.flush();
        }
    }

    protected void sendMessages(int count, String messagePrefix) {
        try (KafkaProducer<String, String> producer = createProducer()) {
            for (int i = 0; i < count; i++) {
                String event = "{\"title\": \"" + messagePrefix + i + "\", \"user\": \"User" + i + "\"}";
                producer.send(new ProducerRecord<>(topic, event));
            }
            producer.flush();
        }
    }
}
