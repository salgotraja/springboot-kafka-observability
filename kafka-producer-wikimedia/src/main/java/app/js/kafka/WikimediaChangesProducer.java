package app.js.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WikimediaChangesProducer {

    private static final Logger log = LoggerFactory.getLogger(WikimediaChangesProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final WebClient webClient;
    private final String topic;
    private final String streamUrl;
    private final int maxRetryAttempts;
    private final int initialBackoffSeconds;
    private final int maxBackoffMinutes;

    private final AtomicInteger counter = new AtomicInteger(1);
    private final Counter eventsProducedCounter;
    private final Counter reconnectCounter;

    public WikimediaChangesProducer(KafkaTemplate<String, String> kafkaTemplate,
                                    WebClient webClient,
                                    MeterRegistry meterRegistry,
                                    @Value("${app.kafka.topic}") String topic,
                                    @Value("${app.wikimedia.stream-url}") String streamUrl,
                                    @Value("${app.wikimedia.retry.max-attempts}") int maxRetryAttempts,
                                    @Value("${app.wikimedia.retry.initial-backoff-seconds}") int initialBackoffSeconds,
                                    @Value("${app.wikimedia.retry.max-backoff-minutes}") int maxBackoffMinutes) {
        this.kafkaTemplate = kafkaTemplate;
        this.webClient = webClient;
        this.topic = topic;
        this.streamUrl = streamUrl;
        this.maxRetryAttempts = maxRetryAttempts;
        this.initialBackoffSeconds = initialBackoffSeconds;
        this.maxBackoffMinutes = maxBackoffMinutes;

        this.eventsProducedCounter = Counter.builder("wikimedia.events.produced")
                .description("Total number of events produced to Kafka")
                .register(meterRegistry);

        this.reconnectCounter = Counter.builder("wikimedia.stream.reconnects")
                .description("Number of reconnection attempts to Wikimedia stream")
                .register(meterRegistry);

        //startStreaming();
        startStreamingForPocOnly();
    }

    private void startStreaming() {
        connectWithRetry().subscribe(
                jsonEvent -> {
                    log.info("Sending Wikimedia change to Kafka (size: {} chars)", jsonEvent.length());
                    kafkaTemplate.send(topic, jsonEvent);
                    eventsProducedCounter.increment();
                },
                error -> log.error("SSE error: {}", error.toString()),
                () -> log.warn("Stream completed - should not happen")
        );
    }

    private void startStreamingForPocOnly() {
        connectWithRetry()
                .take(200)
                .subscribe(
                        data -> {
                            log.info("[POC #{}/100] Sending Wikimedia event to Kafka", counter.getAndIncrement());
                            kafkaTemplate.send(topic, data);
                        },
                        error -> log.error("Stream error: {}", error.toString()),
                        () -> log.info("POC COMPLETE - 100 real Wikimedia events consumed and sent to Kafka")
                );
    }

    private Flux<String> connectWithRetry() {
        return webClient.get()
                .uri(streamUrl)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(data -> data.trim().startsWith("{"))
                .doOnSubscribe(s -> log.info("Connected to Wikimedia recent change stream"))
                .doOnNext(d -> log.debug("Raw event size: {} bytes", d.length()))
                .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofSeconds(initialBackoffSeconds))
                        .maxBackoff(Duration.ofMinutes(maxBackoffMinutes))
                        .doAfterRetry(rs -> {
                            reconnectCounter.increment();
                            log.warn("Reconnecting to Wikimedia, attempt {}", rs.totalRetries() + 1);
                        }))
                .repeat()
                .onErrorContinue((err, obj) -> log.error("Dropped bad event due to: {}", err.toString()));
    }
}