package app.js.api;

import app.js.entity.WikimediaEvent;
import app.js.repository.WikimediaEventRepository;
import app.js.service.EventPersistenceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class WikimediaEventController {

    private final WikimediaEventRepository repository;
    private final EventPersistenceService persistenceService;

    public WikimediaEventController(WikimediaEventRepository repository, EventPersistenceService persistenceService) {
        this.repository = repository;
        this.persistenceService = persistenceService;
    }

    @GetMapping
    public Page<WikimediaEvent> getEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));

        if (search != null && !search.isBlank()) {
            return repository.findByEventDataContaining(search, pageRequest);
        }
        return repository.findAll(pageRequest);
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Instant now = Instant.now();
        Instant oneMinuteAgo = now.minus(1, ChronoUnit.MINUTES);
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);

        long totalEvents = repository.count();
        long eventsLastMinute = repository.countByReceivedAtAfter(oneMinuteAgo);
        long eventsLastHour = repository.countByReceivedAtAfter(oneHourAgo);

        return Map.of(
                "totalEvents", totalEvents,
                "eventsLastMinute", eventsLastMinute,
                "eventsLastHour", eventsLastHour,
                "eventsPerSecond", eventsLastMinute / 60.0,
                "queueSize", persistenceService.getQueueSize()
        );
    }

    @GetMapping("/recent")
    public java.util.List<WikimediaEvent> getRecentEvents(@RequestParam(defaultValue = "10") int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "receivedAt"));
        return repository.findAll(pageRequest).getContent();
    }
}
