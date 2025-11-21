package app.js.repository;

import app.js.entity.FailedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FailedEventRepository extends MongoRepository<FailedEvent, String> {

  List<FailedEvent> findByRetryCountLessThan(int maxRetries);

  Optional<FailedEvent> findByEventData(String eventData);

  long countByFailedAtAfter(Instant timestamp);

  List<FailedEvent> findByErrorType(String errorType);

  Page<FailedEvent> findAllByOrderByFailedAtDesc(Pageable pageable);

  long countByRetryCountGreaterThanEqual(int retryCount);
}
