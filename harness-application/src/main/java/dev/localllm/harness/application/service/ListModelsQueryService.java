package dev.localllm.harness.application.service;

import java.util.Comparator;
import java.util.List;

import dev.localllm.harness.application.dto.query.ListModelsCriteria;
import dev.localllm.harness.application.dto.result.ModelSummaryResult;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ModelInfo;
import dev.localllm.harness.application.port.in.ListModelsQuery;
import dev.localllm.harness.application.port.out.ModelCatalogPort;

/**
 * Lists the installed models matching a name prefix, sorted by name.
 */
public class ListModelsQueryService implements ListModelsQuery {

  private final ModelCatalogPort modelCatalogPort;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param modelCatalogPort installed models
   * @param mapper converts model metadata into summaries
   */
  public ListModelsQueryService(ModelCatalogPort modelCatalogPort, TurnApplicationMapper mapper) {
    this.modelCatalogPort = modelCatalogPort;
    this.mapper = mapper;
  }

  @Override
  public List<ModelSummaryResult> execute(ListModelsCriteria criteria) {
    return modelCatalogPort.listModels(criteria.provider()).stream()
        .filter(model -> model.name().startsWith(criteria.namePrefix()))
        .sorted(Comparator.comparing(ModelInfo::name))
        .map(mapper::toModelSummary)
        .toList();
  }
}
