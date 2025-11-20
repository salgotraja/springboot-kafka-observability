package app.js.repository;

import app.js.entity.FailedEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FailedEventRepository extends MongoRepository<FailedEvent, String> {

    List<FailedEvent> findByRetryCountLessThan(int maxRetries);

    Optional<FailedEvent> findByEventData(String eventData);
}
