package app.js.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Sinks;

@Configuration
public class SinkConfig {

    @Bean
    public Sinks.Many<String> eventSink() {
        return Sinks.many().replay().limit(100);
    }
}
