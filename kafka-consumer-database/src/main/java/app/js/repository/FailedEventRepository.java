package app.js.repository;

import app.js.entity.FailedEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FailedEventRepository extends MongoRepository<FailedEvent, String> {

  List<FailedEvent> findByRetryCountLessThan(int maxRetries);

  Optional<FailedEvent> findByEventData(String eventData);
}
