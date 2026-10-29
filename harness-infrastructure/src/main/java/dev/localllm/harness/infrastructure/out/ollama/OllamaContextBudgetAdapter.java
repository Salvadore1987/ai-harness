package dev.localllm.harness.infrastructure.out.ollama;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import dev.localllm.harness.application.port.out.ContextBudgetPort;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.out.ollama.dto.OllamaRunningModels;

/**
 * Reads the context window of a loaded model from {@code GET /api/ps}.
 *
 * <p>The window is fixed when Ollama loads a model; a different {@code num_ctx} in later requests
 * does not reload it (observed on the MLX runner). The configured {@code harness.context.num-ctx}
 * is therefore only a fallback for a model that is not loaded or a server that cannot be asked.
 */
@Component
public class OllamaContextBudgetAdapter implements ContextBudgetPort {

  private static final Logger log = LoggerFactory.getLogger(OllamaContextBudgetAdapter.class);

  private final RestClient restClient;
  private final int configuredContextLength;

  /**
   * Creates the adapter.
   *
   * @param restClientBuilder auto-configured HTTP client builder
   * @param properties harness settings with the Ollama address and the configured window
   */
  public OllamaContextBudgetAdapter(
      RestClient.Builder restClientBuilder, HarnessProperties properties) {
    this.restClient = restClientBuilder.clone().baseUrl(properties.ollamaBaseUrl()).build();
    this.configuredContextLength = properties.context().numCtx();
  }

  @Override
  public int contextLength(String model) {
    try {
      OllamaRunningModels running = restClient.get()
          .uri("/api/ps")
          .retrieve()
          .body(OllamaRunningModels.class);
      List<OllamaRunningModels.RunningModel> models = running == null
          ? List.of()
          : Objects.requireNonNullElse(running.models(), List.of());
      return models.stream()
          .filter(loaded -> model.equals(loaded.name()))
          .map(OllamaRunningModels.RunningModel::contextLength)
          .filter(Objects::nonNull)
          .findFirst()
          .orElse(configuredContextLength);
    } catch (RestClientException e) {
      log.info("Cannot read loaded context of {}, using {}: {}", model, configuredContextLength,
          e.getMessage());
      return configuredContextLength;
    }
  }
}
