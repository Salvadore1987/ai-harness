package dev.localllm.harness.application.service;

import dev.localllm.harness.application.dto.command.SwitchModelCommand;
import dev.localllm.harness.application.dto.result.ModelSummaryResult;
import dev.localllm.harness.application.exception.ModelNotFoundException;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.port.in.SwitchModelUseCase;
import dev.localllm.harness.application.port.out.ModelCatalogPort;

/**
 * Checks that a model is installed before the session switches to it.
 */
public class SwitchModelService implements SwitchModelUseCase {

  private final ModelCatalogPort modelCatalogPort;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param modelCatalogPort installed models
   * @param mapper converts model metadata into the result
   */
  public SwitchModelService(ModelCatalogPort modelCatalogPort, TurnApplicationMapper mapper) {
    this.modelCatalogPort = modelCatalogPort;
    this.mapper = mapper;
  }

  @Override
  public ModelSummaryResult execute(SwitchModelCommand command) {
    return modelCatalogPort.findModel(command.model())
        .map(mapper::toModelSummary)
        .orElseThrow(() -> new ModelNotFoundException(command.model()));
  }
}
