package app.js.config;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {

  private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);

  @Bean
  public OtlpHttpSpanExporter otlpHttpSpanExporter(
      @Value("${app.tracing.endpoint:http://localhost:4318/v1/traces}") String endpoint) {
    log.info("Configuring OTLP span exporter with endpoint: {}", endpoint);
    return OtlpHttpSpanExporter.builder()
        .setEndpoint(endpoint)
        .build();
  }
}
