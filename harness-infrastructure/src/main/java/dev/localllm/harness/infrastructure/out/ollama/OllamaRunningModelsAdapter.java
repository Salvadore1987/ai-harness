package dev.localllm.harness.infrastructure.out.ollama;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.localllm.harness.application.model.RunningModelInfo;
import dev.localllm.harness.application.port.out.RunningModelsPort;
import dev.localllm.harness.infrastructure.out.ollama.mapper.RunningModelOllamaMapper;

/**
 * Reads the models loaded by Ollama from {@code GET /api/ps}.
 */
@Component
public class OllamaRunningModelsAdapter implements RunningModelsPort {

  private final OllamaPsClient psClient;
  private final RunningModelOllamaMapper mapper;
  private final OllamaErrorTranslator errorTranslator;

  /**
   * Creates the adapter.
   *
   * @param psClient client of {@code /api/ps}
   * @param mapper converts the answer into loaded model descriptions
   * @param errorTranslator translates client failures
   */
  public OllamaRunningModelsAdapter(
      OllamaPsClient psClient,
      RunningModelOllamaMapper mapper,
      OllamaErrorTranslator errorTranslator) {
    this.psClient = psClient;
    this.mapper = mapper;
    this.errorTranslator = errorTranslator;
  }

  @Override
  public List<RunningModelInfo> listRunning() {
    try {
      return psClient.runningModels().stream()
          .filter(model -> model.name() != null)
          .map(mapper::toRunningModelInfo)
          .toList();
    } catch (RuntimeException e) {
      throw errorTranslator.translateRestFailure(e);
    }
  }
}
