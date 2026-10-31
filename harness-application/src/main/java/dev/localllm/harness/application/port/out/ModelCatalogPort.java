package dev.localllm.harness.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.ModelInfo;

/**
 * Lists the models a provider offers.
 */
public interface ModelCatalogPort {

  /**
   * Returns all models of a provider.
   *
   * @param provider provider whose models are listed
   * @return installed models with their capabilities
   */
  List<ModelInfo> listModels(LlmProvider provider);

  /**
   * Looks up one model of a provider.
   *
   * @param provider provider of the model
   * @param name model tag
   * @return the model, or empty if the provider does not offer it
   */
  Optional<ModelInfo> findModel(LlmProvider provider, String name);
}
