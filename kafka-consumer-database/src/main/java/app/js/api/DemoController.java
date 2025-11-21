package app.js.api;

import app.js.entity.WikimediaEvent;
import app.js.repository.FailedEventRepository;
import app.js.repository.WikimediaEventRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

  private static final Logger log = LoggerFactory.getLogger(DemoController.class);
  private final Random random = new Random();
  private final WikimediaEventRepository eventRepository;
  private final FailedEventRepository failedEventRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObservationRegistry observationRegistry;

  public DemoController(
      WikimediaEventRepository eventRepository,
      FailedEventRepository failedEventRepository,
      KafkaTemplate<String, String> kafkaTemplate,
      ObservationRegistry observationRegistry) {
    this.eventRepository = eventRepository;
    this.failedEventRepository = failedEventRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.observationRegistry = observationRegistry;
  }

  @GetMapping("/chain/{depth}")
  public Map<String, Object> chainedCall(@PathVariable int depth) throws InterruptedException {
    log.info("Chained call at depth: {}", depth);
    if (depth > 0) {
      Thread.sleep(100);
      chainedCall(depth - 1);
    }
    return Map.of(
        "status", "success",
        "depth", depth,
        "message", "Chained operation completed");
  }


  @PostMapping("/lifecycle")
  public Map<String, Object> completeLifecycle(@RequestParam(defaultValue = "test-event") String message) {
    log.info("Starting complete lifecycle trace");

    long totalEvents = Observation.createNotStarted("demo.count.events", observationRegistry)
        .observe(() -> {
          log.info("Counting total events in MongoDB");
          return eventRepository.count();
        });

    List<WikimediaEvent> recentEvents = Observation.createNotStarted("demo.fetch.recent", observationRegistry)
        .observe(() -> {
          log.info("Fetching recent events from MongoDB");
          return eventRepository.findAll().stream().limit(5).toList();
        });

    long failedCount = Observation.createNotStarted("demo.count.failed", observationRegistry)
        .observe(() -> {
          log.info("Counting failed events");
          return failedEventRepository.count();
        });

    String eventId = Observation.createNotStarted("demo.save.event", observationRegistry)
        .observe(() -> {
          log.info("Saving new event to MongoDB");
          String eventData = String.format(
              "{\"type\":\"demo\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
              message, Instant.now());
          WikimediaEvent event = eventRepository.save(new WikimediaEvent(eventData));
          return event.getId();
        });

    Observation.createNotStarted("demo.send.kafka", observationRegistry)
        .observe(() -> {
          log.info("Sending notification to Kafka");
          String notification = String.format(
              "{\"type\":\"demo-notification\",\"eventId\":\"%s\",\"action\":\"created\"}",
              eventId);
          kafkaTemplate.send("wikimedia_recent_change", notification);
        });

    log.info("Lifecycle complete - created event: {}", eventId);

    return Map.of(
        "status", "success",
        "eventId", eventId,
        "totalEvents", totalEvents,
        "recentEventsCount", recentEvents.size(),
        "failedEventsCount", failedCount,
        "message", "Complete lifecycle executed with HTTP → MongoDB → Kafka");
  }

  @GetMapping("/lifecycle/{id}")
  public Map<String, Object> getEventLifecycle(@PathVariable String id) {
    log.info("Fetching event lifecycle for id: {}", id);

    return Observation.createNotStarted("demo.get.event", observationRegistry)
        .observe(() -> {
          log.info("Querying MongoDB for event");
          return eventRepository.findById(id)
              .map(event -> Map.<String, Object>of(
                  "status", "found",
                  "id", event.getId(),
                  "data", event.getEventData(),
                  "receivedAt", event.getReceivedAt().toString()))
              .orElseGet(() -> Map.of(
                  "status", "not_found",
                  "id", id,
                  "message", "Event not found"));
        });
  }

  @GetMapping("/lifecycle/search")
  public Map<String, Object> searchEventsLifecycle(
      @RequestParam(defaultValue = "10") int limit) {
    log.info("Searching events with limit: {}", limit);

    List<Map<String, Object>> events = Observation.createNotStarted("demo.search.events", observationRegistry)
        .observe(() -> {
          log.info("Executing MongoDB search query");
          return eventRepository.findAll().stream()
              .limit(limit)
              .map(e -> Map.<String, Object>of(
                  "id", e.getId(),
                  "receivedAt", e.getReceivedAt().toString()))
              .toList();
        });

    long total = Observation.createNotStarted("demo.count.total", observationRegistry)
        .observe(() -> {
          log.info("Counting total documents");
          return eventRepository.count();
        });

    return Map.of(
        "status", "success",
        "events", events,
        "returned", events.size(),
        "total", total);
  }
}
