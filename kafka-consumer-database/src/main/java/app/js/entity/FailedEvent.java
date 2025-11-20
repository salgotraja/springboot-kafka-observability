package app.js.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "failed_events")
public class FailedEvent {

    @Id
    private String id;
    private String eventData;
    private String errorMessage;
    private String errorType;
    private Instant failedAt;
    private int retryCount;

    public FailedEvent() {
    }

    public FailedEvent(String eventData, String errorMessage, String errorType) {
        this.eventData = eventData;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
        this.failedAt = Instant.now();
        this.retryCount = 0;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
}
