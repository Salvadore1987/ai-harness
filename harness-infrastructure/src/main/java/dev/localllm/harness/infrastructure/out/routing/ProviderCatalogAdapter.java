package dev.localllm.harness.infrastructure.out.routing;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.localllm.harness.application.model.ProviderInfo;
import dev.localllm.harness.application.port.out.ProviderCatalogPort;
import dev.localllm.harness.infrastructure.out.routing.mapper.ProviderInfoMapper;

/**
 * Describes the providers whose modules are on the classpath.
 */
@Component
public class ProviderCatalogAdapter implements ProviderCatalogPort {

  private final LlmProviderRegistry registry;
  private final ProviderInfoMapper mapper;

  /**
   * Creates the adapter.
   *
   * @param registry provider adapters
   * @param mapper reads the provider state from the adapters
   */
  public ProviderCatalogAdapter(LlmProviderRegistry registry, ProviderInfoMapper mapper) {
    this.registry = registry;
    this.mapper = mapper;
  }

  @Override
  public List<ProviderInfo> listProviders() {
    return registry.all().stream().map(mapper::toProviderInfo).toList();
  }
}
