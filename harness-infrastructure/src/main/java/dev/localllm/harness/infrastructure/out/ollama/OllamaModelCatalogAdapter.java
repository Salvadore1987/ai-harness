package dev.localllm.harness.infrastructure.out.ollama;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.model.ModelInfo;
import dev.localllm.harness.application.port.out.ModelCatalogPort;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ModelInfoOllamaMapper;

/**
 * Reads installed models and their capabilities from {@code /api/tags} and {@code /api/show}.
 */
@Component
public class OllamaModelCatalogAdapter implements ModelCatalogPort {

  private final OllamaApi ollamaApi;
  private final ModelInfoOllamaMapper mapper;
  private final OllamaErrorTranslator errorTranslator;

  /**
   * Creates the adapter.
   *
   * @param ollamaApi Ollama REST client
   * @param mapper converts Ollama metadata into model descriptions
   * @param errorTranslator translates client failures
   */
  public OllamaModelCatalogAdapter(
      OllamaApi ollamaApi,
      ModelInfoOllamaMapper mapper,
      OllamaErrorTranslator errorTranslator) {
    this.ollamaApi = ollamaApi;
    this.mapper = mapper;
    this.errorTranslator = errorTranslator;
  }

  @Override
  public List<ModelInfo> listModels() {
    List<OllamaApi.Model> models;
    try {
      models = Objects.requireNonNullElse(ollamaApi.listModels().models(), List.of());
    } catch (RuntimeException e) {
      throw errorTranslator.translateRestFailure(e);
    }
    return models.stream()
        .map(OllamaApi.Model::name)
        .map(this::findModel)
        .flatMap(Optional::stream)
        .toList();
  }

  @Override
  public Optional<ModelInfo> findModel(String name) {
    try {
      OllamaApi.ShowModelResponse response =
          ollamaApi.showModel(new OllamaApi.ShowModelRequest(name));
      return Optional.of(mapper.toModelInfo(name, response));
    } catch (RuntimeException e) {
      if (errorTranslator.isNotFound(e)) {
        return Optional.empty();
      }
      throw errorTranslator.translateRestFailure(e);
    }
  }
}
