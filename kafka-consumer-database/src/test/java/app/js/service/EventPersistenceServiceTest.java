package app.js.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import app.js.entity.WikimediaEvent;
import app.js.repository.FailedEventRepository;
import app.js.repository.WikimediaEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class EventPersistenceServiceTest {

  @Mock private WikimediaEventRepository eventRepository;

  @Mock private FailedEventRepository failedEventRepository;

  @Mock private KafkaTemplate<String, String> kafkaTemplate;

  private EventPersistenceService service;

  @BeforeEach
  void setUp() {
    service =
        new EventPersistenceService(
            eventRepository,
            failedEventRepository,
            kafkaTemplate,
            new SimpleMeterRegistry(),
            "test-dlq-topic",
            100,
            10,
            500);
  }

  @AfterEach
  void tearDown() {
    service.shutdown();
  }

  @Test
  void shouldSubmitEventToQueue() {
    boolean result = service.submit("{\"test\": \"event\"}");
    assertThat(result).isTrue();
  }

  @Test
  void shouldBatchAndPersistEvents() {
    for (int i = 0; i < 10; i++) {
      service.submit("{\"id\": " + i + "}");
    }

    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              verify(eventRepository, atLeastOnce()).saveAll(anyList());
            });
  }

  @Test
  void shouldFlushOnTimeInterval() {
    service.submit("{\"single\": \"event\"}");

    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              verify(eventRepository, atLeastOnce()).saveAll(anyList());
            });
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldSaveCorrectEventData() {
    String eventData = "{\"title\": \"Test\"}";
    service.submit(eventData);

    ArgumentCaptor<List<WikimediaEvent>> captor = ArgumentCaptor.forClass(List.class);

    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              verify(eventRepository, atLeastOnce()).saveAll(captor.capture());
            });

    List<WikimediaEvent> savedEvents = captor.getValue();
    assertThat(savedEvents).hasSize(1);
    assertThat(savedEvents.get(0).getEventData()).isEqualTo(eventData);
  }

  @Test
  void shouldHandleRepositoryFailure() {
    when(eventRepository.saveAll(anyList())).thenThrow(new RuntimeException("DB error"));

    service.submit("{\"fail\": \"event\"}");

    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              verify(failedEventRepository, atLeastOnce()).save(any());
            });
  }

  @Test
  void shouldRejectWhenQueueFull() {
    EventPersistenceService smallQueueService =
        new EventPersistenceService(
            eventRepository,
            failedEventRepository,
            kafkaTemplate,
            new SimpleMeterRegistry(),
            "test-dlq-topic",
            5,
            100,
            10000);

    try {
      for (int i = 0; i < 10; i++) {
        smallQueueService.submit("{\"id\": " + i + "}");
      }

      assertThat(smallQueueService.getQueueSize()).isLessThanOrEqualTo(5);
    } finally {
      smallQueueService.shutdown();
    }
  }

  @Test
  void shouldFlushOnShutdown() {
    for (int i = 0; i < 5; i++) {
      service.submit("{\"id\": " + i + "}");
    }

    service.shutdown();

    verify(eventRepository, atLeastOnce()).saveAll(anyList());
  }
}
