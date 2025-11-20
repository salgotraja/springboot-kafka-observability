package app.js.service;

import app.js.entity.FailedEvent;
import app.js.entity.WikimediaEvent;
import app.js.repository.FailedEventRepository;
import app.js.repository.WikimediaEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventPersistenceService {

  private static final Logger log = LoggerFactory.getLogger(EventPersistenceService.class);

  private final WikimediaEventRepository eventRepository;
  private final FailedEventRepository failedEventRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final String dlqTopic;
  private final BlockingQueue<String> eventQueue;
  private final Thread batchWorker;
  private final AtomicBoolean running = new AtomicBoolean(true);

  private final int batchSize;
  private final long flushIntervalMs;

  private final Counter eventsPersistedCounter;
  private final Counter eventsDlqCounter;

  public EventPersistenceService(
      WikimediaEventRepository eventRepository,
      FailedEventRepository failedEventRepository,
      KafkaTemplate<String, String> kafkaTemplate,
      MeterRegistry meterRegistry,
      @Value("${app.kafka.dlq-topic}") String dlqTopic,
      @Value("${app.persistence.queue-capacity:10000}") int queueCapacity,
      @Value("${app.persistence.batch-size:100}") int batchSize,
      @Value("${app.persistence.flush-interval-ms:1000}") long flushIntervalMs) {

    this.eventRepository = eventRepository;
    this.failedEventRepository = failedEventRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.dlqTopic = dlqTopic;
    this.eventQueue = new LinkedBlockingQueue<>(queueCapacity);
    this.batchSize = batchSize;
    this.flushIntervalMs = flushIntervalMs;

    this.eventsPersistedCounter =
        Counter.builder("wikimedia.events.persisted")
            .description("Total number of events persisted to MongoDB")
            .register(meterRegistry);

    this.eventsDlqCounter =
        Counter.builder("wikimedia.events.dlq")
            .description("Total number of events sent to DLQ")
            .register(meterRegistry);

    Gauge.builder("wikimedia.events.queue.size", eventQueue, BlockingQueue::size)
        .description("Current size of the event queue")
        .register(meterRegistry);

    this.batchWorker = new Thread(this::processBatches, "event-persistence-worker");
    this.batchWorker.setDaemon(false);
    this.batchWorker.start();

    log.info(
        "EventPersistenceService started with queue capacity: {}, batch size: {}, flush interval: {}ms",
        queueCapacity,
        batchSize,
        flushIntervalMs);
  }

  public boolean submit(String eventData) {
    return eventQueue.offer(eventData);
  }

  private void processBatches() {
    List<String> batch = new ArrayList<>(batchSize);
    long lastFlushTime = System.currentTimeMillis();

    while (running.get() || !eventQueue.isEmpty()) {
      try {
        String event = eventQueue.poll(100, TimeUnit.MILLISECONDS);

        if (event != null) {
          batch.add(event);
        }

        long now = System.currentTimeMillis();
        boolean shouldFlush =
            batch.size() >= batchSize
                || (now - lastFlushTime >= flushIntervalMs && !batch.isEmpty());

        if (shouldFlush) {
          flushBatch(batch);
          batch.clear();
          lastFlushTime = now;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    if (!batch.isEmpty()) {
      flushBatch(batch);
    }

    log.info("Event persistence worker stopped");
  }

  private void flushBatch(List<String> batch) {
    if (batch.isEmpty()) return;

    try {
      List<WikimediaEvent> events = batch.stream().map(WikimediaEvent::new).toList();

      eventRepository.saveAll(events);
      eventsPersistedCounter.increment(batch.size());
      log.debug("Flushed batch of {} events to MongoDB", batch.size());
    } catch (Exception e) {
      log.error("Failed to persist batch of {} events: {}", batch.size(), e.getMessage());
      batch.forEach(
          eventData -> {
            kafkaTemplate.send(dlqTopic, eventData);
            eventsDlqCounter.increment();
            try {
              failedEventRepository.save(
                  new FailedEvent(eventData, e.getMessage(), e.getClass().getSimpleName()));
            } catch (Exception ex) {
              log.error("Failed to save to MongoDB DLQ: {}", ex.getMessage());
            }
          });
      log.info("Sent {} failed events to Kafka DLQ topic: {}", batch.size(), dlqTopic);
    }
  }

  @PreDestroy
  public void shutdown() {
    log.info("Shutting down EventPersistenceService, queue size: {}", eventQueue.size());
    running.set(false);

    try {
      batchWorker.join(30000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while waiting for batch worker to finish");
    }
  }

  public int getQueueSize() {
    return eventQueue.size();
  }
}
