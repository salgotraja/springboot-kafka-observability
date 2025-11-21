package app.js.config;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {

  @Bean
  @ConditionalOnProperty(name = "management.otlp.tracing.endpoint", matchIfMissing = false)
  public OtlpHttpSpanExporter otlpHttpSpanExporter(
      @Value("${management.otlp.tracing.endpoint:http://localhost:4318/v1/traces}")
          String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      return null;
    }
    return OtlpHttpSpanExporter.builder().setEndpoint(endpoint).build();
  }
}
