package app.js.api;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@RestController
public class WikimediaStreamController {

  private static final Logger log = LoggerFactory.getLogger(WikimediaStreamController.class);

  private final Sinks.Many<String> sink;
  private final AtomicLong eventCounter = new AtomicLong(0);

  public WikimediaStreamController(Sinks.Many<String> sink) {
    this.sink = sink;
  }

  @GetMapping(value = "/wikimedia/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<String>> stream() {
    return sink.asFlux()
        .map(
            data ->
                ServerSentEvent.<String>builder()
                    .id(String.valueOf(eventCounter.incrementAndGet()))
                    .event("wikimedia-change")
                    .data(data)
                    .build())
        .doOnSubscribe(s -> log.info("SSE client connected"))
        .doOnCancel(() -> log.info("SSE client disconnected"));
  }
}
