package app.js.kafka;

import app.js.entity.FailedEvent;
import app.js.entity.WikimediaEvent;
import app.js.repository.FailedEventRepository;
import app.js.repository.WikimediaEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class DlqConsumer {
  private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

  private static final int MAX_RETRY_COUNT = 3;

  private final WikimediaEventRepository eventRepository;
  private final FailedEventRepository failedEventRepository;

  public DlqConsumer(
      WikimediaEventRepository eventRepository, FailedEventRepository failedEventRepository) {
    this.eventRepository = eventRepository;
    this.failedEventRepository = failedEventRepository;
  }

  @KafkaListener(
      topics = "${app.kafka.dlq-topic}",
      groupId = "${spring.kafka.consumer.group-id}-dlq")
  public void consume(String eventData) {
    log.debug("Processing DLQ event");

    try {
      eventRepository.save(new WikimediaEvent(eventData));
      log.debug("Successfully reprocessed DLQ event");

      failedEventRepository
          .findByEventData(eventData)
          .ifPresent(
              failedEvent -> {
                failedEventRepository.delete(failedEvent);
                log.debug("Removed successfully reprocessed event from failed_events collection");
              });
    } catch (Exception e) {
      log.error("Failed to reprocess DLQ event: {}", e.getMessage());

      failedEventRepository
          .findByEventData(eventData)
          .ifPresentOrElse(
              failedEvent -> {
                int newRetryCount = failedEvent.getRetryCount() + 1;
                if (newRetryCount >= MAX_RETRY_COUNT) {
                  log.error(
                      "Event exceeded max retry count ({}), marking as permanently failed",
                      MAX_RETRY_COUNT);
                }
                failedEvent.setRetryCount(newRetryCount);
                failedEvent.setErrorMessage(e.getMessage());
                failedEvent.setErrorType(e.getClass().getSimpleName());
                failedEventRepository.save(failedEvent);
              },
              () -> {
                try {
                  failedEventRepository.save(
                      new FailedEvent(eventData, e.getMessage(), e.getClass().getSimpleName()));
                } catch (Exception ex) {
                  log.error("Failed to save retry failure to MongoDB: {}", ex.getMessage());
                }
              });
    }
  }
}
