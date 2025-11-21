package app.js.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import app.js.entity.FailedEvent;
import app.js.repository.FailedEventRepository;
import app.js.repository.WikimediaEventRepository;
import app.js.service.EventPersistenceService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SystemControllerTest {

  @Mock private WikimediaEventRepository eventRepository;

  @Mock private FailedEventRepository failedEventRepository;

  @Mock private EventPersistenceService persistenceService;

  private SystemController systemController;

  @BeforeEach
  void setUp() {
    systemController =
        new SystemController(eventRepository, failedEventRepository, persistenceService);
  }

  @Test
  void shouldReturnHealthyStatus() {
    when(eventRepository.count()).thenReturn(1000L);
    when(eventRepository.countByReceivedAtAfter(any())).thenReturn(60L);
    when(failedEventRepository.count()).thenReturn(5L);
    when(failedEventRepository.countByFailedAtAfter(any())).thenReturn(0L);
    when(failedEventRepository.countByRetryCountGreaterThanEqual(anyInt())).thenReturn(0L);
    when(persistenceService.getQueueSize()).thenReturn(100);

    Map<String, Object> result = systemController.getSystemStatus();

    assertThat(result.get("status")).isEqualTo("HEALTHY");
    assertThat(result).containsKey("events");
    assertThat(result).containsKey("queue");
    assertThat(result).containsKey("dlq");
  }

  @Test
  void shouldReturnWarningStatusWhenQueueHigh() {
    when(eventRepository.count()).thenReturn(1000L);
    when(eventRepository.countByReceivedAtAfter(any())).thenReturn(60L);
    when(failedEventRepository.count()).thenReturn(5L);
    when(failedEventRepository.countByFailedAtAfter(any())).thenReturn(0L);
    when(failedEventRepository.countByRetryCountGreaterThanEqual(anyInt())).thenReturn(0L);
    when(persistenceService.getQueueSize()).thenReturn(6000);

    Map<String, Object> result = systemController.getSystemStatus();

    assertThat(result.get("status")).isEqualTo("WARNING");
  }

  @Test
  void shouldReturnCriticalStatusWhenQueueCritical() {
    when(eventRepository.count()).thenReturn(1000L);
    when(eventRepository.countByReceivedAtAfter(any())).thenReturn(60L);
    when(failedEventRepository.count()).thenReturn(5L);
    when(failedEventRepository.countByFailedAtAfter(any())).thenReturn(0L);
    when(failedEventRepository.countByRetryCountGreaterThanEqual(anyInt())).thenReturn(0L);
    when(persistenceService.getQueueSize()).thenReturn(9000);

    Map<String, Object> result = systemController.getSystemStatus();

    assertThat(result.get("status")).isEqualTo("CRITICAL");
  }

  @Test
  void shouldReturnDlqStatus() {
    FailedEvent failedEvent =
        new FailedEvent("test data", "Connection refused", "MongoTimeoutException");
    failedEvent.setId("test-id-123");

    when(failedEventRepository.count()).thenReturn(10L);
    when(failedEventRepository.findByRetryCountLessThan(anyInt())).thenReturn(List.of(failedEvent));
    when(failedEventRepository.countByRetryCountGreaterThanEqual(anyInt())).thenReturn(2L);
    when(failedEventRepository.findAllByOrderByFailedAtDesc(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(failedEvent)));
    when(failedEventRepository.findAll()).thenReturn(List.of(failedEvent));
    when(failedEventRepository.countByFailedAtAfter(any())).thenReturn(5L);

    Map<String, Object> result = systemController.getDlqStatus(10);

    assertThat(result).containsKey("summary");
    assertThat(result).containsKey("byErrorType");
    assertThat(result).containsKey("recentFailures");

    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) result.get("summary");
    assertThat(summary.get("total")).isEqualTo(10L);
    assertThat(summary.get("exhausted")).isEqualTo(2L);
  }

  @Test
  void shouldReturnThroughputMetrics() {
    when(eventRepository.countByReceivedAtAfter(any()))
        .thenReturn(120L)
        .thenReturn(500L)
        .thenReturn(1200L)
        .thenReturn(4500L);
    when(persistenceService.getQueueSize()).thenReturn(50);

    Map<String, Object> result = systemController.getThroughput();

    assertThat(result).containsKey("current");
    assertThat(result).containsKey("averages");
    assertThat(result).containsKey("totals");
    assertThat(result.get("queueSize")).isEqualTo(50);
  }

  @Test
  void shouldReturnLatencyEstimate() {
    when(eventRepository.countByReceivedAtAfter(any())).thenReturn(120L);
    when(persistenceService.getQueueSize()).thenReturn(100);

    Map<String, Object> result = systemController.getLatencyEstimate();

    assertThat(result).containsKey("status");
    assertThat(result).containsKey("queueSize");
    assertThat(result).containsKey("processingRatePerSecond");
    assertThat(result).containsKey("estimatedLatencySeconds");
    assertThat(result).containsKey("estimatedLatencyFormatted");
  }

  @Test
  void shouldReturnGoodLatencyStatus() {
    when(eventRepository.countByReceivedAtAfter(any())).thenReturn(120L);
    when(persistenceService.getQueueSize()).thenReturn(10);

    Map<String, Object> result = systemController.getLatencyEstimate();

    assertThat(result.get("status")).isEqualTo("GOOD");
  }

  @Test
  void shouldReturnWarningLatencyStatus() {
    when(eventRepository.countByReceivedAtAfter(any())).thenReturn(60L);
    when(persistenceService.getQueueSize()).thenReturn(50);

    Map<String, Object> result = systemController.getLatencyEstimate();

    assertThat(result.get("status")).isEqualTo("WARNING");
  }

  @Test
  void shouldReturnCriticalLatencyStatus() {
    when(eventRepository.countByReceivedAtAfter(any())).thenReturn(60L);
    when(persistenceService.getQueueSize()).thenReturn(100);

    Map<String, Object> result = systemController.getLatencyEstimate();

    assertThat(result.get("status")).isEqualTo("CRITICAL");
  }

  @Test
  void shouldHandleZeroProcessingRate() {
    when(eventRepository.countByReceivedAtAfter(any())).thenReturn(0L);
    when(persistenceService.getQueueSize()).thenReturn(100);

    Map<String, Object> result = systemController.getLatencyEstimate();

    assertThat(result.get("estimatedLatencySeconds")).isEqualTo(0.0);
  }
}
