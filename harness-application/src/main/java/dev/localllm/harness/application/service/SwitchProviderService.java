package dev.localllm.harness.application.service;

import dev.localllm.harness.application.dto.command.SwitchProviderCommand;
import dev.localllm.harness.application.dto.result.ProviderResult;
import dev.localllm.harness.application.exception.ProviderUnavailableException;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.port.in.SwitchProviderUseCase;
import dev.localllm.harness.application.port.out.ProviderCatalogPort;

/**
 * Checks that a provider is available before the CLI switches to it.
 */
public class SwitchProviderService implements SwitchProviderUseCase {

  private final ProviderCatalogPort providerCatalogPort;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param providerCatalogPort providers and their availability
   * @param mapper converts the provider state into the result
   */
  public SwitchProviderService(
      ProviderCatalogPort providerCatalogPort, TurnApplicationMapper mapper) {
    this.providerCatalogPort = providerCatalogPort;
    this.mapper = mapper;
  }

  @Override
  public ProviderResult execute(SwitchProviderCommand command) {
    return providerCatalogPort.listProviders().stream()
        .filter(info -> info.provider() == command.provider() && info.available())
        .findFirst()
        .map(mapper::toProviderResult)
        .orElseThrow(() -> new ProviderUnavailableException(command.provider()));
  }
}
