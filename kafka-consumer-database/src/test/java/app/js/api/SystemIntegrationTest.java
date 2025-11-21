package app.js.api;

import static org.assertj.core.api.Assertions.assertThat;

import app.js.ContainersConfig;
import app.js.entity.FailedEvent;
import app.js.entity.WikimediaEvent;
import app.js.repository.FailedEventRepository;
import app.js.repository.WikimediaEventRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(ContainersConfig.class)
@ActiveProfiles("test")
class SystemIntegrationTest {

  private WebTestClient webTestClient;

  @Autowired private WebApplicationContext wac;

  @Autowired private WikimediaEventRepository eventRepository;

  @Autowired private FailedEventRepository failedEventRepository;

  @BeforeEach
  void setUp() {
    webTestClient = MockMvcWebTestClient.bindToApplicationContext(wac).build();
    eventRepository.deleteAll();
    failedEventRepository.deleteAll();
  }

  @Test
  void shouldGetSystemStatus() {
    seedEvents(5);

    webTestClient
        .get()
        .uri("/api/system/status")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
        .value(
            body -> {
              assertThat(body).containsKey("status");
              assertThat(body).containsKey("timestamp");
              assertThat(body).containsKey("events");
              assertThat(body).containsKey("queue");
              assertThat(body).containsKey("dlq");
            });
  }

  @Test
  void shouldReturnHealthyStatusWithNoIssues() {
    seedEvents(10);

    webTestClient
        .get()
        .uri("/api/system/status")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
        .value(body -> assertThat(body.get("status")).isEqualTo("HEALTHY"));
  }

  @Test
  void shouldGetDlqStatus() {
    seedFailedEvents(3);

    webTestClient
        .get()
        .uri("/api/system/dlq?limit=10")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
        .value(
            body -> {
              assertThat(body).containsKey("summary");
              assertThat(body).containsKey("byErrorType");
              assertThat(body).containsKey("recentFailures");

              @SuppressWarnings("unchecked")
              Map<String, Object> summary = (Map<String, Object>) body.get("summary");
              assertThat(((Number) summary.get("total")).longValue()).isEqualTo(3L);
            });
  }

  @Test
  void shouldGetDlqStatusWithErrorTypeBreakdown() {
    failedEventRepository.save(
        new FailedEvent("data1", "Connection refused", "MongoTimeoutException"));
    failedEventRepository.save(
        new FailedEvent("data2", "Connection refused", "MongoTimeoutException"));
    failedEventRepository.save(new FailedEvent("data3", "Validation error", "ValidationException"));

    webTestClient
        .get()
        .uri("/api/system/dlq")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
        .value(
            body -> {
              @SuppressWarnings("unchecked")
              Map<String, Object> byErrorType = (Map<String, Object>) body.get("byErrorType");
              assertThat(((Number) byErrorType.get("MongoTimeoutException")).longValue())
                  .isEqualTo(2L);
              assertThat(((Number) byErrorType.get("ValidationException")).longValue())
                  .isEqualTo(1L);
            });
  }

  @Test
  void shouldGetThroughput() {
    seedEvents(10);

    webTestClient
        .get()
        .uri("/api/system/throughput")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
        .value(
            body -> {
              assertThat(body).containsKey("current");
              assertThat(body).containsKey("averages");
              assertThat(body).containsKey("totals");
              assertThat(body).containsKey("queueSize");
            });
  }

  @Test
  void shouldGetLatencyEstimate() {
    seedEvents(5);

    webTestClient
        .get()
        .uri("/api/system/latency")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
        .value(
            body -> {
              assertThat(body).containsKey("status");
              assertThat(body).containsKey("queueSize");
              assertThat(body).containsKey("processingRatePerSecond");
              assertThat(body).containsKey("estimatedLatencySeconds");
              assertThat(body).containsKey("estimatedLatencyFormatted");
            });
  }

  @Test
  void shouldHandleEmptyDlq() {
    webTestClient
        .get()
        .uri("/api/system/dlq")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
        .value(
            body -> {
              @SuppressWarnings("unchecked")
              Map<String, Object> summary = (Map<String, Object>) body.get("summary");
              assertThat(((Number) summary.get("total")).longValue()).isEqualTo(0L);
            });
  }

  @Test
  void shouldReturnCorrectEventCounts() {
    seedEvents(20);

    webTestClient
        .get()
        .uri("/api/system/status")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
        .value(
            body -> {
              @SuppressWarnings("unchecked")
              Map<String, Object> events = (Map<String, Object>) body.get("events");
              assertThat(((Number) events.get("total")).longValue()).isEqualTo(20L);
            });
  }

  @Test
  void shouldReturnThroughputTotals() {
    seedEvents(15);

    webTestClient
        .get()
        .uri("/api/system/throughput")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
        .value(
            body -> {
              @SuppressWarnings("unchecked")
              Map<String, Object> totals = (Map<String, Object>) body.get("totals");
              assertThat(((Number) totals.get("lastMinute")).longValue()).isEqualTo(15L);
            });
  }

  private void seedEvents(int count) {
    for (int i = 0; i < count; i++) {
      eventRepository.save(new WikimediaEvent("{\"id\": " + i + ", \"type\": \"edit\"}"));
    }
  }

  private void seedFailedEvents(int count) {
    for (int i = 0; i < count; i++) {
      failedEventRepository.save(
          new FailedEvent("{\"id\": " + i + "}", "Error message " + i, "TestException"));
    }
  }
}
