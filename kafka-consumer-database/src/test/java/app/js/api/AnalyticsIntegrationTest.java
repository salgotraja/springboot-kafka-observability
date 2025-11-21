package app.js.api;

import static org.assertj.core.api.Assertions.assertThat;

import app.js.ContainersConfig;
import app.js.entity.WikimediaEvent;
import app.js.repository.WikimediaEventRepository;
import java.util.List;
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
class AnalyticsIntegrationTest {

  private WebTestClient webTestClient;

  @Autowired private WebApplicationContext wac;

  @Autowired private WikimediaEventRepository eventRepository;

  @BeforeEach
  void setUp() {
    webTestClient = MockMvcWebTestClient.bindToApplicationContext(wac).build();
    eventRepository.deleteAll();
  }

  @Test
  void shouldGetEventsByWiki() {
    seedEvents(
        List.of(
            "{\"wiki\": \"enwiki\", \"type\": \"edit\", \"user\": \"Test\"}",
            "{\"wiki\": \"enwiki\", \"type\": \"edit\", \"user\": \"Test\"}",
            "{\"wiki\": \"dewiki\", \"type\": \"edit\", \"user\": \"Test\"}"));

    webTestClient
        .get()
        .uri("/api/analytics/by-wiki?hours=1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Long>>() {})
        .value(
            body -> {
              assertThat(body).containsEntry("enwiki", 2L);
              assertThat(body).containsEntry("dewiki", 1L);
            });
  }

  @Test
  void shouldGetEventsByType() {
    seedEvents(
        List.of(
            "{\"wiki\": \"enwiki\", \"type\": \"edit\", \"user\": \"Test\"}",
            "{\"wiki\": \"enwiki\", \"type\": \"categorize\", \"user\": \"Test\"}",
            "{\"wiki\": \"enwiki\", \"type\": \"edit\", \"user\": \"Test\"}"));

    webTestClient
        .get()
        .uri("/api/analytics/by-type?hours=1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Long>>() {})
        .value(
            body -> {
              assertThat(body).containsEntry("edit", 2L);
              assertThat(body).containsEntry("categorize", 1L);
            });
  }

  @Test
  void shouldGetTopUsers() {
    seedEvents(
        List.of(
            "{\"wiki\": \"enwiki\", \"type\": \"edit\", \"user\": \"Alice\"}",
            "{\"wiki\": \"enwiki\", \"type\": \"edit\", \"user\": \"Alice\"}",
            "{\"wiki\": \"enwiki\", \"type\": \"edit\", \"user\": \"Alice\"}",
            "{\"wiki\": \"enwiki\", \"type\": \"edit\", \"user\": \"Bob\"}",
            "{\"wiki\": \"enwiki\", \"type\": \"edit\", \"user\": \"Bob\"}"));

    webTestClient
        .get()
        .uri("/api/analytics/top-users?hours=1&limit=2")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
        .value(
            body -> {
              assertThat(body).hasSize(2);
              assertThat(body.get(0).get("user")).isEqualTo("Alice");
            });
  }

  @Test
  void shouldGetTopPages() {
    seedEvents(
        List.of(
            "{\"wiki\": \"enwiki\", \"type\": \"edit\", \"title\": \"Main Page\"}",
            "{\"wiki\": \"enwiki\", \"type\": \"edit\", \"title\": \"Main Page\"}",
            "{\"wiki\": \"enwiki\", \"type\": \"edit\", \"title\": \"Test Article\"}"));

    webTestClient
        .get()
        .uri("/api/analytics/top-pages?hours=1&limit=10")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
        .value(
            body -> {
              assertThat(body).hasSize(2);
              assertThat(body.get(0).get("title")).isEqualTo("Main Page");
            });
  }

  @Test
  void shouldGetHourlyDistribution() {
    seedEvents(
        List.of(
            "{\"wiki\": \"enwiki\", \"type\": \"edit\"}",
            "{\"wiki\": \"enwiki\", \"type\": \"edit\"}",
            "{\"wiki\": \"enwiki\", \"type\": \"edit\"}"));

    webTestClient
        .get()
        .uri("/api/analytics/hourly?hours=1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Long>>() {})
        .value(body -> assertThat(body).isNotEmpty());
  }

  @Test
  void shouldGetEventBreakdown() {
    seedEvents(
        List.of(
            "{\"bot\": true, \"namespace\": 0}",
            "{\"bot\": false, \"namespace\": 0}",
            "{\"bot\": false, \"namespace\": 1}"));

    webTestClient
        .get()
        .uri("/api/analytics/breakdown?hours=1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
        .value(
            body -> {
              assertThat(body).containsKey("totalEvents");
              assertThat(body).containsKey("byBot");
              assertThat(body).containsKey("byNamespace");
            });
  }

  @Test
  void shouldHandleEmptyDatabase() {
    webTestClient
        .get()
        .uri("/api/analytics/by-wiki?hours=1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<Map<String, Long>>() {})
        .value(body -> assertThat(body).isEmpty());
  }

  private void seedEvents(List<String> events) {
    List<WikimediaEvent> entities = events.stream().map(WikimediaEvent::new).toList();
    eventRepository.saveAll(entities);
  }
}
