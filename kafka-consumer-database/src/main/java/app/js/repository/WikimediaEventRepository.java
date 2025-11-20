package app.js.repository;

import app.js.entity.WikimediaEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface WikimediaEventRepository extends MongoRepository<WikimediaEvent, String> {

  @Query("{ 'eventData': { $regex: ?0, $options: 'i' } }")
  Page<WikimediaEvent> findByEventDataContaining(String searchTerm, Pageable pageable);

  List<WikimediaEvent> findByReceivedAtBetween(Instant start, Instant end);

  long countByReceivedAtAfter(Instant timestamp);
}
