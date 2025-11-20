package app.js.service;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import app.js.entity.FailedEvent;
import app.js.repository.FailedEventRepository;
import app.js.repository.WikimediaEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class DeadLetterQueueTest {

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
            5,
            200);
  }

  @AfterEach
  void tearDown() {
    service.shutdown();
  }

  @Test
  void shouldSaveToDeadLetterQueueOnPersistenceFailure() {
    when(eventRepository.saveAll(anyList()))
        .thenThrow(new RuntimeException("MongoDB connection failed"));

    for (int i = 0; i < 5; i++) {
      service.submit("{\"id\": " + i + "}");
    }

    await()
        .atMost(3, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              verify(failedEventRepository, times(5)).save(any(FailedEvent.class));
            });
  }

  @Test
  void shouldCaptureErrorDetailsInDeadLetterQueue() {
    String errorMessage = "Duplicate key error";
    when(eventRepository.saveAll(anyList())).thenThrow(new IllegalStateException(errorMessage));

    service.submit("{\"test\": \"event\"}");

    await()
        .atMost(3, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              verify(failedEventRepository)
                  .save(
                      argThat(
                          failedEvent ->
                              failedEvent.getErrorMessage().equals(errorMessage)
                                  && failedEvent.getErrorType().equals("IllegalStateException")
                                  && failedEvent.getEventData().equals("{\"test\": \"event\"}")));
            });
  }

  @Test
  void shouldContinueProcessingAfterFailure() {
    when(eventRepository.saveAll(anyList()))
        .thenThrow(new RuntimeException("First batch fails"))
        .thenReturn(List.of());

    for (int i = 0; i < 5; i++) {
      service.submit("{\"batch1\": " + i + "}");
    }

    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              verify(eventRepository, atLeastOnce()).saveAll(anyList());
            });

    reset(eventRepository);

    for (int i = 0; i < 5; i++) {
      service.submit("{\"batch2\": " + i + "}");
    }

    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              verify(eventRepository, atLeastOnce()).saveAll(anyList());
            });
  }

  @Test
  void shouldHandlePartialBatchFailure() {
    when(eventRepository.saveAll(anyList())).thenThrow(new RuntimeException("Batch insert failed"));

    int eventCount = 10;
    for (int i = 0; i < eventCount; i++) {
      service.submit("{\"event\": " + i + "}");
    }

    await()
        .atMost(3, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              verify(failedEventRepository, atLeast(eventCount)).save(any(FailedEvent.class));
            });
  }

  @Test
  void failedEventShouldHaveTimestamp() {
    when(eventRepository.saveAll(anyList())).thenThrow(new RuntimeException("DB error"));

    service.submit("{\"test\": \"event\"}");

    await()
        .atMost(3, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              verify(failedEventRepository)
                  .save(argThat(failedEvent -> failedEvent.getFailedAt() != null));
            });
  }
}
