package app.js.kafka;

import app.js.service.EventPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

@Service
public class KafkaDatabaseConsumer {
    private static final Logger logger = LoggerFactory.getLogger(KafkaDatabaseConsumer.class);

    private final Sinks.Many<String> sink;
    private final EventPersistenceService persistenceService;

    public KafkaDatabaseConsumer(Sinks.Many<String> sink, EventPersistenceService persistenceService) {
        this.sink = sink;
        this.persistenceService = persistenceService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String eventMessage) {
        logger.debug("Event message received");

        if (!persistenceService.submit(eventMessage)) {
            logger.warn("Event queue full, event dropped");
        }

        sink.tryEmitNext(eventMessage);
    }
}
