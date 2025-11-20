package app.js.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import app.js.AbstractIT;
import app.js.entity.WikimediaEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class KafkaDatabaseConsumerIntegrationTest extends AbstractIT {

  @Test
  void shouldConsumeMessageAndPersistToMongoDB() {
    String testEvent =
        "{\"title\": \"Test Page\", \"user\": \"TestUser\", \"comment\": \"Test edit\"}";

    sendMessage(testEvent);

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              List<WikimediaEvent> events = eventRepository.findAll();
              assertThat(events).isNotEmpty();
              boolean found = events.stream().anyMatch(e -> e.getEventData().equals(testEvent));
              assertThat(found).isTrue();
            });
  }

  @Test
  void shouldBatchMultipleMessages() {
    int messageCount = 150;

    sendMessages(messageCount, "BatchPage");

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              long count = eventRepository.count();
              assertThat(count).isGreaterThanOrEqualTo(messageCount);
            });
  }

  @Test
  void eventPersistenceServiceShouldBeConfigured() {
    assertThat(persistenceService).isNotNull();
    assertThat(persistenceService.getQueueSize()).isGreaterThanOrEqualTo(0);
  }
}
