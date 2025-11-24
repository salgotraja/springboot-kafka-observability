package app.js.client;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import reactor.core.publisher.Flux;

public interface WikimediaStreamClient {

  @GetExchange
  Flux<String> streamRecentChanges(@RequestHeader("Accept") String accept);

  default Flux<String> streamRecentChanges() {
    return streamRecentChanges(MediaType.TEXT_EVENT_STREAM_VALUE);
  }
}
