package dev.localllm.harness.application.fixture;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.ModelCapability;
import dev.localllm.harness.application.model.ModelInfo;
import dev.localllm.harness.application.port.out.ModelCatalogPort;

/**
 * Fixed list of installed models of one provider; other providers have none.
 */
public final class FakeModelCatalogPort implements ModelCatalogPort {

  /** A thinking- and tools-capable model. */
  public static final ModelInfo QWEN = new ModelInfo(
      "qwen3.5:27b", "27B", "Q4_K_M", Set.of(ModelCapability.THINKING, ModelCapability.TOOLS));

  /** A model without optional capabilities. */
  public static final ModelInfo CODELLAMA = new ModelInfo(
      "codellama:7b-instruct", "7B", "Q4_0", Set.of());

  private final Map<LlmProvider, List<ModelInfo>> models;

  /**
   * Creates a catalog with the given Ollama models.
   *
   * @param models installed models
   */
  public FakeModelCatalogPort(ModelInfo... models) {
    this(LlmProvider.OLLAMA, models);
  }

  /**
   * Creates a catalog with the given models of one provider.
   *
   * @param provider provider offering the models
   * @param models models of the provider
   */
  public FakeModelCatalogPort(LlmProvider provider, ModelInfo... models) {
    this.models = Map.of(provider, List.of(models));
  }

  @Override
  public List<ModelInfo> listModels(LlmProvider provider) {
    return models.getOrDefault(provider, List.of());
  }

  @Override
  public Optional<ModelInfo> findModel(LlmProvider provider, String name) {
    return listModels(provider).stream().filter(model -> model.name().equals(name)).findFirst();
  }
}
