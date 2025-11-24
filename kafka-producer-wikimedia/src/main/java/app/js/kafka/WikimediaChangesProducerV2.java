package app.js.kafka;

import app.js.client.WikimediaStreamClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Service
@ConditionalOnProperty(name = "app.wikimedia.client-type", havingValue = "httpexchange")
public class WikimediaChangesProducerV2 {

  private static final Logger log = LoggerFactory.getLogger(WikimediaChangesProducerV2.class);

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final WikimediaStreamClient wikimediaStreamClient;
  private final ObservationRegistry observationRegistry;
  private final String topic;
  private final int maxRetryAttempts;
  private final int initialBackoffSeconds;
  private final int maxBackoffMinutes;

  private final AtomicInteger counter = new AtomicInteger(1);
  private final Counter eventsProducedCounter;
  private final Counter reconnectCounter;

  public WikimediaChangesProducerV2(
      KafkaTemplate<String, String> kafkaTemplate,
      WikimediaStreamClient wikimediaStreamClient,
      ObservationRegistry observationRegistry,
      MeterRegistry meterRegistry,
      @Value("${app.kafka.topic}") String topic,
      @Value("${app.wikimedia.retry.max-attempts}") int maxRetryAttempts,
      @Value("${app.wikimedia.retry.initial-backoff-seconds}") int initialBackoffSeconds,
      @Value("${app.wikimedia.retry.max-backoff-minutes}") int maxBackoffMinutes) {
    this.kafkaTemplate = kafkaTemplate;
    this.wikimediaStreamClient = wikimediaStreamClient;
    this.observationRegistry = observationRegistry;
    this.topic = topic;
    this.maxRetryAttempts = maxRetryAttempts;
    this.initialBackoffSeconds = initialBackoffSeconds;
    this.maxBackoffMinutes = maxBackoffMinutes;

    this.eventsProducedCounter =
        Counter.builder("wikimedia.events.produced")
            .description("Total number of events produced to Kafka")
            .register(meterRegistry);

    this.reconnectCounter =
        Counter.builder("wikimedia.stream.reconnects")
            .description("Number of reconnection attempts to Wikimedia stream")
            .register(meterRegistry);

    startStreamingForPocOnly();
  }

  private void startStreaming() {
    connectWithRetry()
        .subscribe(
            jsonEvent -> {
              log.info("Sending Wikimedia change to Kafka (size: {} chars)", jsonEvent.length());
              kafkaTemplate.send(topic, jsonEvent);
              eventsProducedCounter.increment();
            },
            error -> log.error("SSE error: {}", error.toString()),
            () -> log.warn("Stream completed - should not happen"));
  }

  private void startStreamingForPocOnly() {
    connectWithRetry()
        .take(200)
        .subscribe(
            data ->
                Observation.createNotStarted("wikimedia.event.process", observationRegistry)
                    .observe(
                        () -> {
                          log.info(
                              "[POC #{}/200] Sending Wikimedia event to Kafka",
                              counter.getAndIncrement());
                          kafkaTemplate.send(topic, data);
                        }),
            error -> log.error("Stream error: {}", error.toString()),
            () -> log.info("POC COMPLETE - 200 real Wikimedia events consumed and sent to Kafka"));
  }

  private Flux<String> connectWithRetry() {
    return wikimediaStreamClient
        .streamRecentChanges()
        .filter(data -> data.trim().startsWith("{"))
        .doOnSubscribe(s -> log.info("Connected to Wikimedia recent change stream via HTTP Service Client"))
        .doOnNext(d -> log.debug("Raw event size: {} bytes", d.length()))
        .retryWhen(
            Retry.backoff(maxRetryAttempts, Duration.ofSeconds(initialBackoffSeconds))
                .maxBackoff(Duration.ofMinutes(maxBackoffMinutes))
                .doAfterRetry(
                    rs -> {
                      reconnectCounter.increment();
                      log.warn("Reconnecting to Wikimedia, attempt {}", rs.totalRetries() + 1);
                    }))
        .repeat()
        .onErrorContinue((err, obj) -> log.error("Dropped bad event due to: {}", err.toString()));
  }
}
