package dev.localllm.harness.infrastructure.out.routing;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.ProviderInfo;
import dev.localllm.harness.application.port.out.ProviderCatalogPort;
import dev.localllm.harness.infrastructure.config.HarnessProperties;

/**
 * Reports Ollama as the only available provider: the Anthropic adapter does not exist yet.
 *
 * <p>Ollama starts with {@code harness.model}; Anthropic is listed as unavailable with the model
 * it will start with once its adapter and settings are in place.
 */
@Component
public class ProviderCatalogAdapter implements ProviderCatalogPort {

  private static final String ANTHROPIC_DEFAULT_MODEL = "claude-opus-5-5";

  private final List<ProviderInfo> providers;

  /**
   * Creates the adapter.
   *
   * @param properties harness settings with the start model of Ollama
   */
  public ProviderCatalogAdapter(HarnessProperties properties) {
    this.providers = List.of(
        new ProviderInfo(LlmProvider.OLLAMA, true, properties.model()),
        new ProviderInfo(LlmProvider.ANTHROPIC, false, ANTHROPIC_DEFAULT_MODEL));
  }

  @Override
  public List<ProviderInfo> listProviders() {
    return providers;
  }
}
