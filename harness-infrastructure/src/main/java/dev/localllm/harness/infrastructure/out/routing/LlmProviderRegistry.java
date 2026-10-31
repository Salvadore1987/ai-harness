package dev.localllm.harness.infrastructure.out.routing;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.localllm.harness.application.exception.ProviderUnavailableException;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.port.out.LlmProviderAdapters;

/**
 * The provider adapters of the provider modules on the classpath, one per provider.
 */
@Component
public class LlmProviderRegistry {

  private final Map<LlmProvider, LlmProviderAdapters> adapters = new EnumMap<>(LlmProvider.class);

  /**
   * Creates the registry.
   *
   * @param providerAdapters adapters of every provider module
   * @throws IllegalStateException if two modules serve the same provider
   */
  public LlmProviderRegistry(List<LlmProviderAdapters> providerAdapters) {
    for (LlmProviderAdapters provider : providerAdapters) {
      LlmProviderAdapters previous = adapters.putIfAbsent(provider.provider(), provider);
      if (previous != null) {
        throw new IllegalStateException("Two adapters serve provider " + provider.provider());
      }
    }
  }

  /**
   * Returns the adapters of a provider that can be used.
   *
   * @param provider requested provider
   * @return its adapters
   * @throws ProviderUnavailableException if no module serves the provider or it is unavailable
   */
  public LlmProviderAdapters available(LlmProvider provider) {
    LlmProviderAdapters found = adapters.get(provider);
    if (found == null || !found.isAvailable()) {
      throw new ProviderUnavailableException(provider);
    }
    return found;
  }

  /**
   * Returns the adapters of every provider, available or not.
   *
   * @return adapters in the order of {@link LlmProvider}
   */
  public Collection<LlmProviderAdapters> all() {
    return adapters.values();
  }
}
