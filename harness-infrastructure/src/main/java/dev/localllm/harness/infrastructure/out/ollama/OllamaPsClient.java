package dev.localllm.harness.infrastructure.out.ollama;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import dev.localllm.harness.infrastructure.out.ollama.config.OllamaProperties;
import dev.localllm.harness.infrastructure.out.ollama.dto.OllamaRunningModels;

/**
 * Calls {@code GET /api/ps}, which Spring AI's {@code OllamaApi} does not cover.
 */
@Component
public class OllamaPsClient {

  private final RestClient restClient;

  /**
   * Creates the client.
   *
   * @param restClientBuilder auto-configured HTTP client builder
   * @param properties Ollama settings with the server address
   */
  public OllamaPsClient(RestClient.Builder restClientBuilder, OllamaProperties properties) {
    this.restClient = restClientBuilder.clone().baseUrl(properties.ollamaBaseUrl()).build();
  }

  /**
   * Returns the models Ollama currently holds in memory.
   *
   * @return loaded models, empty if none is loaded
   * @throws RestClientException if the server cannot be reached or answers with an error
   */
  public List<OllamaRunningModels.RunningModel> runningModels() {
    OllamaRunningModels running = restClient.get()
        .uri("/api/ps")
        .retrieve()
        .body(OllamaRunningModels.class);
    return running == null ? List.of() : Objects.requireNonNullElse(running.models(), List.of());
  }
}
