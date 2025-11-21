package app.js.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

  @Mock private MongoTemplate mongoTemplate;

  private AnalyticsService analyticsService;

  @BeforeEach
  void setUp() {
    analyticsService = new AnalyticsService(mongoTemplate);
  }

  @Test
  void shouldGroupEventsByWiki() {
    List<Document> events =
        List.of(
            createEventDocument("{\"wiki\": \"enwiki\", \"type\": \"edit\"}"),
            createEventDocument("{\"wiki\": \"enwiki\", \"type\": \"edit\"}"),
            createEventDocument("{\"wiki\": \"dewiki\", \"type\": \"edit\"}"));

    when(mongoTemplate.find(any(), eq(Document.class), eq("wikimedia_events"))).thenReturn(events);

    Map<String, Long> result = analyticsService.getEventsByWiki(24);

    assertThat(result).containsEntry("enwiki", 2L);
    assertThat(result).containsEntry("dewiki", 1L);
  }

  @Test
  void shouldGroupEventsByType() {
    List<Document> events =
        List.of(
            createEventDocument("{\"wiki\": \"enwiki\", \"type\": \"edit\"}"),
            createEventDocument("{\"wiki\": \"enwiki\", \"type\": \"edit\"}"),
            createEventDocument("{\"wiki\": \"enwiki\", \"type\": \"categorize\"}"));

    when(mongoTemplate.find(any(), eq(Document.class), eq("wikimedia_events"))).thenReturn(events);

    Map<String, Long> result = analyticsService.getEventsByType(24);

    assertThat(result).containsEntry("edit", 2L);
    assertThat(result).containsEntry("categorize", 1L);
  }

  @Test
  void shouldGetTopUsers() {
    List<Document> events =
        List.of(
            createEventDocument("{\"user\": \"Alice\", \"type\": \"edit\"}"),
            createEventDocument("{\"user\": \"Alice\", \"type\": \"edit\"}"),
            createEventDocument("{\"user\": \"Alice\", \"type\": \"edit\"}"),
            createEventDocument("{\"user\": \"Bob\", \"type\": \"edit\"}"),
            createEventDocument("{\"user\": \"Bob\", \"type\": \"edit\"}"),
            createEventDocument("{\"user\": \"Charlie\", \"type\": \"edit\"}"));

    when(mongoTemplate.find(any(), eq(Document.class), eq("wikimedia_events"))).thenReturn(events);

    List<Map<String, Object>> result = analyticsService.getTopUsers(24, 2);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).get("user")).isEqualTo("Alice");
    assertThat(result.get(0).get("count")).isEqualTo(3L);
    assertThat(result.get(1).get("user")).isEqualTo("Bob");
    assertThat(result.get(1).get("count")).isEqualTo(2L);
  }

  @Test
  void shouldGetTopPages() {
    List<Document> events =
        List.of(
            createEventDocument("{\"title\": \"Main Page\", \"type\": \"edit\"}"),
            createEventDocument("{\"title\": \"Main Page\", \"type\": \"edit\"}"),
            createEventDocument("{\"title\": \"Test Article\", \"type\": \"edit\"}"));

    when(mongoTemplate.find(any(), eq(Document.class), eq("wikimedia_events"))).thenReturn(events);

    List<Map<String, Object>> result = analyticsService.getTopPages(24, 10);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).get("title")).isEqualTo("Main Page");
    assertThat(result.get(0).get("count")).isEqualTo(2L);
  }

  @Test
  void shouldGetEventBreakdown() {
    List<Document> events =
        List.of(
            createEventDocument("{\"bot\": true, \"namespace\": 0}"),
            createEventDocument("{\"bot\": false, \"namespace\": 0}"),
            createEventDocument("{\"bot\": false, \"namespace\": 1}"),
            createEventDocument("{\"bot\": true, \"namespace\": 2}"));

    when(mongoTemplate.find(any(), eq(Document.class), eq("wikimedia_events"))).thenReturn(events);

    Map<String, Object> result = analyticsService.getEventBreakdown(24);

    assertThat(result.get("totalEvents")).isEqualTo(4L);

    @SuppressWarnings("unchecked")
    Map<String, Long> byBot = (Map<String, Long>) result.get("byBot");
    assertThat(byBot.get("bot")).isEqualTo(2L);
    assertThat(byBot.get("human")).isEqualTo(2L);

    @SuppressWarnings("unchecked")
    Map<String, Long> byNamespace = (Map<String, Long>) result.get("byNamespace");
    assertThat(byNamespace.get("Main")).isEqualTo(2L);
    assertThat(byNamespace.get("Talk")).isEqualTo(1L);
    assertThat(byNamespace.get("User")).isEqualTo(1L);
  }

  @Test
  void shouldHandleEmptyResults() {
    when(mongoTemplate.find(any(), eq(Document.class), eq("wikimedia_events")))
        .thenReturn(List.of());

    Map<String, Long> result = analyticsService.getEventsByWiki(24);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldHandleMalformedJson() {
    List<Document> events =
        List.of(
            createEventDocument("not valid json"), createEventDocument("{\"wiki\": \"enwiki\"}"));

    when(mongoTemplate.find(any(), eq(Document.class), eq("wikimedia_events"))).thenReturn(events);

    Map<String, Long> result = analyticsService.getEventsByWiki(24);

    assertThat(result).containsEntry("enwiki", 1L);
  }

  @Test
  void shouldGetHourlyDistribution() {
    Instant now = Instant.now();
    List<Document> events =
        List.of(
            createEventDocumentWithTime("{\"type\": \"edit\"}", now),
            createEventDocumentWithTime("{\"type\": \"edit\"}", now),
            createEventDocumentWithTime("{\"type\": \"edit\"}", now.minusSeconds(3600)));

    when(mongoTemplate.find(any(), eq(Document.class), eq("wikimedia_events"))).thenReturn(events);

    Map<String, Long> result = analyticsService.getHourlyDistribution(24);

    assertThat(result).hasSize(2);
  }

  private Document createEventDocument(String eventData) {
    Document doc = new Document();
    doc.put("eventData", eventData);
    doc.put("receivedAt", java.util.Date.from(Instant.now()));
    return doc;
  }

  private Document createEventDocumentWithTime(String eventData, Instant time) {
    Document doc = new Document();
    doc.put("eventData", eventData);
    doc.put("receivedAt", java.util.Date.from(time));
    return doc;
  }
}
