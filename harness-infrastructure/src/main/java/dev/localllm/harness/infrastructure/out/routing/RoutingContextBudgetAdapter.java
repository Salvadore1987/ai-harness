package dev.localllm.harness.infrastructure.out.routing;

import org.springframework.stereotype.Component;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.port.out.ContextBudgetPort;

/**
 * Reads the context window from the provider of the model.
 */
@Component
public class RoutingContextBudgetAdapter implements ContextBudgetPort {

  private final LlmProviderRegistry registry;

  /**
   * Creates the adapter.
   *
   * @param registry provider adapters
   */
  public RoutingContextBudgetAdapter(LlmProviderRegistry registry) {
    this.registry = registry;
  }

  @Override
  public int contextLength(LlmProvider provider, String model) {
    return registry.available(provider).contextLength(model);
  }
}
