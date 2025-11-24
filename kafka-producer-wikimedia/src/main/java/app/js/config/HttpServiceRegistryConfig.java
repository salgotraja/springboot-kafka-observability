package app.js.config;

import app.js.client.WikimediaStreamClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
@ConditionalOnProperty(name = "app.wikimedia.client-type", havingValue = "httpexchange")
public class HttpServiceRegistryConfig {

  @Bean
  public WikimediaStreamClient wikimediaStreamClient(
      WebClient.Builder webClientBuilder, @Value("${app.wikimedia.stream-url}") String streamUrl) {

    WebClient webClient =
        webClientBuilder
            .baseUrl(streamUrl)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

    HttpServiceProxyFactory factory =
        HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient)).build();

    return factory.createClient(WikimediaStreamClient.class);
  }
}
