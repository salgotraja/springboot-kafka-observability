package app.js.api;

import app.js.entity.FailedEvent;
import app.js.repository.FailedEventRepository;
import app.js.repository.WikimediaEventRepository;
import app.js.service.EventPersistenceService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

  private final WikimediaEventRepository eventRepository;
  private final FailedEventRepository failedEventRepository;
  private final EventPersistenceService persistenceService;

  public SystemController(
      WikimediaEventRepository eventRepository,
      FailedEventRepository failedEventRepository,
      EventPersistenceService persistenceService) {
    this.eventRepository = eventRepository;
    this.failedEventRepository = failedEventRepository;
    this.persistenceService = persistenceService;
  }

  @GetMapping("/status")
  public Map<String, Object> getSystemStatus() {
    Instant now = Instant.now();
    Instant oneMinuteAgo = now.minus(1, ChronoUnit.MINUTES);
    Instant fiveMinutesAgo = now.minus(5, ChronoUnit.MINUTES);

    long totalEvents = eventRepository.count();
    long eventsLastMinute = eventRepository.countByReceivedAtAfter(oneMinuteAgo);
    long eventsLast5Minutes = eventRepository.countByReceivedAtAfter(fiveMinutesAgo);

    long totalFailed = failedEventRepository.count();
    long failedLastMinute = failedEventRepository.countByFailedAtAfter(oneMinuteAgo);
    long exhaustedRetries = failedEventRepository.countByRetryCountGreaterThanEqual(3);

    int queueSize = persistenceService.getQueueSize();

    String healthStatus = "HEALTHY";
    if (queueSize > 8000 || exhaustedRetries > 100) {
      healthStatus = "CRITICAL";
    } else if (queueSize > 5000 || exhaustedRetries > 50 || failedLastMinute > 10) {
      healthStatus = "WARNING";
    }

    Map<String, Object> result = new HashMap<>();
    result.put("status", healthStatus);
    result.put("timestamp", now.toString());
    result.put(
        "events",
        Map.of(
            "total", totalEvents,
            "lastMinute", eventsLastMinute,
            "last5Minutes", eventsLast5Minutes,
            "ratePerSecond", eventsLastMinute / 60.0));
    result.put("queue", Map.of("size", queueSize, "capacityUsed", queueSize / 100.0 + "%"));
    result.put(
        "dlq",
        Map.of(
            "total", totalFailed,
            "lastMinute", failedLastMinute,
            "exhaustedRetries", exhaustedRetries));

    return result;
  }

  @GetMapping("/dlq")
  public Map<String, Object> getDlqStatus(@RequestParam(defaultValue = "10") int limit) {
    long total = failedEventRepository.count();
    long retryable = failedEventRepository.findByRetryCountLessThan(3).size();
    long exhausted = failedEventRepository.countByRetryCountGreaterThanEqual(3);

    List<FailedEvent> recentFailures =
        failedEventRepository.findAllByOrderByFailedAtDesc(PageRequest.of(0, limit)).getContent();

    Map<String, Long> byErrorType =
        failedEventRepository.findAll().stream()
            .collect(Collectors.groupingBy(FailedEvent::getErrorType, Collectors.counting()));

    Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
    long failedLastHour = failedEventRepository.countByFailedAtAfter(oneHourAgo);

    Map<String, Object> result = new HashMap<>();
    result.put(
        "summary",
        Map.of(
            "total", total,
            "retryable", retryable,
            "exhausted", exhausted,
            "lastHour", failedLastHour));
    result.put("byErrorType", byErrorType);
    result.put(
        "recentFailures",
        recentFailures.stream()
            .map(
                f ->
                    Map.of(
                        "id", f.getId(),
                        "errorType", f.getErrorType(),
                        "errorMessage", f.getErrorMessage(),
                        "failedAt", f.getFailedAt().toString(),
                        "retryCount", f.getRetryCount()))
            .toList());

    return result;
  }

  @GetMapping("/throughput")
  public Map<String, Object> getThroughput() {
    Instant now = Instant.now();
    Instant oneMinuteAgo = now.minus(1, ChronoUnit.MINUTES);
    Instant fiveMinutesAgo = now.minus(5, ChronoUnit.MINUTES);
    Instant fifteenMinutesAgo = now.minus(15, ChronoUnit.MINUTES);
    Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);

    long eventsLastMinute = eventRepository.countByReceivedAtAfter(oneMinuteAgo);
    long eventsLast5Minutes = eventRepository.countByReceivedAtAfter(fiveMinutesAgo);
    long eventsLast15Minutes = eventRepository.countByReceivedAtAfter(fifteenMinutesAgo);
    long eventsLastHour = eventRepository.countByReceivedAtAfter(oneHourAgo);

    Map<String, Object> result = new HashMap<>();
    result.put(
        "current",
        Map.of("eventsPerSecond", eventsLastMinute / 60.0, "eventsPerMinute", eventsLastMinute));
    result.put(
        "averages",
        Map.of(
            "last5Minutes", eventsLast5Minutes / 5.0,
            "last15Minutes", eventsLast15Minutes / 15.0,
            "lastHour", eventsLastHour / 60.0));
    result.put(
        "totals",
        Map.of(
            "lastMinute", eventsLastMinute,
            "last5Minutes", eventsLast5Minutes,
            "last15Minutes", eventsLast15Minutes,
            "lastHour", eventsLastHour));
    result.put("queueSize", persistenceService.getQueueSize());

    return result;
  }

  @GetMapping("/latency")
  public Map<String, Object> getLatencyEstimate() {
    int queueSize = persistenceService.getQueueSize();

    Instant oneMinuteAgo = Instant.now().minus(1, ChronoUnit.MINUTES);
    long eventsLastMinute = eventRepository.countByReceivedAtAfter(oneMinuteAgo);
    double eventsPerSecond = eventsLastMinute / 60.0;

    double estimatedLatencySeconds = eventsPerSecond > 0 ? queueSize / eventsPerSecond : 0;

    String latencyStatus = "GOOD";
    if (estimatedLatencySeconds > 60) {
      latencyStatus = "CRITICAL";
    } else if (estimatedLatencySeconds > 30) {
      latencyStatus = "WARNING";
    }

    Map<String, Object> result = new HashMap<>();
    result.put("status", latencyStatus);
    result.put("queueSize", queueSize);
    result.put("processingRatePerSecond", eventsPerSecond);
    result.put("estimatedLatencySeconds", estimatedLatencySeconds);
    result.put("estimatedLatencyFormatted", formatDuration(estimatedLatencySeconds));

    return result;
  }

  private String formatDuration(double seconds) {
    if (seconds < 1) {
      return String.format("%.0fms", seconds * 1000);
    } else if (seconds < 60) {
      return String.format("%.1fs", seconds);
    } else {
      return String.format("%.1fm", seconds / 60);
    }
  }
}
