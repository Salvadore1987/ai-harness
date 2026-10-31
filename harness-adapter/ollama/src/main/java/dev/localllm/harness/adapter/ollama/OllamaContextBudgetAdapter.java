package dev.localllm.harness.adapter.ollama;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import dev.localllm.harness.adapter.ollama.config.OllamaProperties;
import dev.localllm.harness.adapter.ollama.dto.OllamaRunningModels;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.port.out.ContextBudgetPort;

/**
 * Reads the context window of a loaded model from {@code GET /api/ps}.
 *
 * <p>The window is fixed when Ollama loads a model; a different {@code num_ctx} in later requests
 * does not reload it (observed on the MLX runner). The configured {@code harness.context.num-ctx}
 * is therefore only a fallback for a model that is not loaded or a server that cannot be asked.
 * The {@code provider} argument is not checked: only requests for {@link LlmProvider#OLLAMA}
 * reach this adapter.
 */
@Component
public class OllamaContextBudgetAdapter implements ContextBudgetPort {

  private static final Logger log = LoggerFactory.getLogger(OllamaContextBudgetAdapter.class);

  private final OllamaPsClient psClient;
  private final int configuredContextLength;

  /**
   * Creates the adapter.
   *
   * @param psClient client of {@code /api/ps}
   * @param properties Ollama settings with the configured window
   */
  public OllamaContextBudgetAdapter(OllamaPsClient psClient, OllamaProperties properties) {
    this.psClient = psClient;
    this.configuredContextLength = properties.context().numCtx();
  }

  @Override
  public int contextLength(LlmProvider provider, String model) {
    try {
      return psClient.runningModels().stream()
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
