package dev.localllm.harness.infrastructure.out.routing;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.ModelInfo;
import dev.localllm.harness.application.port.out.ModelCatalogPort;

/**
 * Lists and looks up the models of the requested provider.
 */
@Component
public class RoutingModelCatalogAdapter implements ModelCatalogPort {

  private final LlmProviderRegistry registry;

  /**
   * Creates the adapter.
   *
   * @param registry provider adapters
   */
  public RoutingModelCatalogAdapter(LlmProviderRegistry registry) {
    this.registry = registry;
  }

  @Override
  public List<ModelInfo> listModels(LlmProvider provider) {
    return registry.available(provider).listModels();
  }

  @Override
  public Optional<ModelInfo> findModel(LlmProvider provider, String name) {
    return registry.available(provider).findModel(name);
  }
}
