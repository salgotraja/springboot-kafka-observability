package app.js.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

  private final MongoTemplate mongoTemplate;
  private final ObjectMapper objectMapper;

  public AnalyticsService(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
    this.objectMapper = new ObjectMapper();
  }

  public Map<String, Long> getEventsByWiki(int hours) {
    Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
    return aggregateByField("wiki", since);
  }

  public Map<String, Long> getEventsByType(int hours) {
    Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
    return aggregateByField("type", since);
  }

  public List<Map<String, Object>> getTopUsers(int hours, int limit) {
    Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
    Map<String, Long> userCounts = aggregateByField("user", since);

    return userCounts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(limit)
        .map(
            e -> {
              Map<String, Object> result = new HashMap<>();
              result.put("user", e.getKey());
              result.put("count", e.getValue());
              return result;
            })
        .toList();
  }

  public List<Map<String, Object>> getTopPages(int hours, int limit) {
    Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
    Map<String, Long> pageCounts = aggregateByField("title", since);

    return pageCounts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(limit)
        .map(
            e -> {
              Map<String, Object> result = new HashMap<>();
              result.put("title", e.getKey());
              result.put("count", e.getValue());
              return result;
            })
        .toList();
  }

  public Map<String, Long> getHourlyDistribution(int hours) {
    Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
    Query query = new Query(Criteria.where("receivedAt").gte(since));
    query.with(Sort.by(Sort.Direction.ASC, "receivedAt"));

    List<org.bson.Document> events =
        mongoTemplate.find(query, org.bson.Document.class, "wikimedia_events");

    Map<String, Long> hourlyCount = new LinkedHashMap<>();
    for (org.bson.Document event : events) {
      Instant receivedAt = event.getDate("receivedAt").toInstant();
      String hourKey = receivedAt.truncatedTo(ChronoUnit.HOURS).toString();
      hourlyCount.merge(hourKey, 1L, Long::sum);
    }

    return hourlyCount;
  }

  public Map<String, Object> getEventBreakdown(int hours) {
    Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);

    Query query = new Query(Criteria.where("receivedAt").gte(since));
    List<org.bson.Document> events =
        mongoTemplate.find(query, org.bson.Document.class, "wikimedia_events");

    Map<String, Long> byBot = new HashMap<>();
    Map<String, Long> byNamespace = new HashMap<>();
    long totalEvents = events.size();

    for (org.bson.Document event : events) {
      String eventData = event.getString("eventData");
      try {
        JsonNode json = objectMapper.readTree(eventData);

        boolean isBot = json.path("bot").asBoolean(false);
        byBot.merge(isBot ? "bot" : "human", 1L, Long::sum);

        int namespace = json.path("namespace").asInt(0);
        String nsKey =
            switch (namespace) {
              case 0 -> "Main";
              case 1 -> "Talk";
              case 2 -> "User";
              case 3 -> "User talk";
              case 4 -> "Project";
              case 6 -> "File";
              case 10 -> "Template";
              case 14 -> "Category";
              default -> "Other (" + namespace + ")";
            };
        byNamespace.merge(nsKey, 1L, Long::sum);

      } catch (JsonProcessingException e) {
        // Skip malformed events
      }
    }

    Map<String, Object> result = new HashMap<>();
    result.put("totalEvents", totalEvents);
    result.put("byBot", byBot);
    result.put("byNamespace", byNamespace);
    result.put("periodHours", hours);

    return result;
  }

  private Map<String, Long> aggregateByField(String fieldName, Instant since) {
    Query query = new Query(Criteria.where("receivedAt").gte(since));
    List<org.bson.Document> events =
        mongoTemplate.find(query, org.bson.Document.class, "wikimedia_events");

    Map<String, Long> counts = new HashMap<>();

    for (org.bson.Document event : events) {
      String eventData = event.getString("eventData");
      try {
        JsonNode json = objectMapper.readTree(eventData);
        String value = json.path(fieldName).asText("");
        if (!value.isEmpty()) {
          counts.merge(value, 1L, Long::sum);
        }
      } catch (JsonProcessingException e) {
        // Skip malformed events
      }
    }

    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (e1, _) -> e1, LinkedHashMap::new));
  }
}
