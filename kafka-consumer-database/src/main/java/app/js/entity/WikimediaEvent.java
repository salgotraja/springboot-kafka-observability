package app.js.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "wikimedia_events")
public class WikimediaEvent {

  @Id private String id;
  private String eventData;
  private Instant receivedAt;

  public WikimediaEvent() {}

  public WikimediaEvent(String eventData) {
    this.eventData = eventData;
    this.receivedAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getEventData() {
    return eventData;
  }

  public void setEventData(String eventData) {
    this.eventData = eventData;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(Instant receivedAt) {
    this.receivedAt = receivedAt;
  }
}
