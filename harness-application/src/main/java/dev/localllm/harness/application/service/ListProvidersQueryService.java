package dev.localllm.harness.application.service;

import java.util.Comparator;
import java.util.List;

import dev.localllm.harness.application.dto.query.ListProvidersCriteria;
import dev.localllm.harness.application.dto.result.ProviderSummaryResult;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ProviderInfo;
import dev.localllm.harness.application.port.in.ListProvidersQuery;
import dev.localllm.harness.application.port.out.ProviderCatalogPort;

/**
 * Lists the providers in their declaration order with the active one marked.
 */
public class ListProvidersQueryService implements ListProvidersQuery {

  private final ProviderCatalogPort providerCatalogPort;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param providerCatalogPort providers and their availability
   * @param mapper converts the provider state into summaries
   */
  public ListProvidersQueryService(
      ProviderCatalogPort providerCatalogPort, TurnApplicationMapper mapper) {
    this.providerCatalogPort = providerCatalogPort;
    this.mapper = mapper;
  }

  @Override
  public List<ProviderSummaryResult> execute(ListProvidersCriteria criteria) {
    return providerCatalogPort.listProviders().stream()
        .sorted(Comparator.comparing(ProviderInfo::provider))
        .map(info -> mapper.toProviderSummary(info, criteria.activeProvider()))
        .toList();
  }
}
