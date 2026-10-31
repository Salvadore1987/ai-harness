package dev.localllm.harness.adapter.ollama;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.stereotype.Component;

import dev.localllm.harness.adapter.ollama.config.OllamaProperties.ToolCallModeType;
import dev.localllm.harness.adapter.ollama.mapper.ModelInfoOllamaMapper;
import dev.localllm.harness.adapter.ollama.toolcall.ToolCallModeResolver;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.ModelCapability;
import dev.localllm.harness.application.model.ModelInfo;
import dev.localllm.harness.application.port.out.ModelCatalogPort;

/**
 * Reads installed models and their capabilities from {@code /api/tags} and {@code /api/show}.
 *
 * <p>A model configured for the {@code text-json} tool call mode gets the
 * {@link ModelCapability#TOOLS} capability even if it has no native tool calling: the harness
 * describes the tools in the prompt. The {@code provider} argument is not checked: only requests
 * for {@link LlmProvider#OLLAMA} reach this adapter.
 */
@Component
public class OllamaModelCatalogAdapter implements ModelCatalogPort {

  private final OllamaApi ollamaApi;
  private final ModelInfoOllamaMapper mapper;
  private final OllamaErrorTranslator errorTranslator;
  private final ToolCallModeResolver toolCallModes;

  /**
   * Creates the adapter.
   *
   * @param ollamaApi Ollama REST client
   * @param mapper converts Ollama metadata into model descriptions
   * @param errorTranslator translates client failures
   * @param toolCallModes tool call mode configured for each model
   */
  public OllamaModelCatalogAdapter(
      OllamaApi ollamaApi,
      ModelInfoOllamaMapper mapper,
      OllamaErrorTranslator errorTranslator,
      ToolCallModeResolver toolCallModes) {
    this.ollamaApi = ollamaApi;
    this.mapper = mapper;
    this.errorTranslator = errorTranslator;
    this.toolCallModes = toolCallModes;
  }

  @Override
  public List<ModelInfo> listModels(LlmProvider provider) {
    List<OllamaApi.Model> models;
    try {
      models = Objects.requireNonNullElse(ollamaApi.listModels().models(), List.of());
    } catch (RuntimeException e) {
      throw errorTranslator.translateRestFailure(e);
    }
    return models.stream()
        .map(OllamaApi.Model::name)
        .map(this::showModel)
        .flatMap(Optional::stream)
        .toList();
  }

  @Override
  public Optional<ModelInfo> findModel(LlmProvider provider, String name) {
    return showModel(name);
  }

  private Optional<ModelInfo> showModel(String name) {
    try {
      OllamaApi.ShowModelResponse response =
          ollamaApi.showModel(new OllamaApi.ShowModelRequest(name));
      return Optional.of(withToolCallMode(mapper.toModelInfo(name, response)));
    } catch (RuntimeException e) {
      if (errorTranslator.isNotFound(e)) {
        return Optional.empty();
      }
      throw errorTranslator.translateRestFailure(e);
    }
  }

  private ModelInfo withToolCallMode(ModelInfo model) {
    if (toolCallModes.type(model.name()) != ToolCallModeType.TEXT_JSON
        || model.capabilities().contains(ModelCapability.TOOLS)) {
      return model;
    }
    Set<ModelCapability> capabilities = EnumSet.of(ModelCapability.TOOLS);
    capabilities.addAll(model.capabilities());
    return new ModelInfo(model.name(), model.parameterSize(), model.quantization(), capabilities);
  }
}
