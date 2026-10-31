package dev.localllm.harness.application.service;

import dev.localllm.harness.application.dto.command.ToggleThinkingCommand;
import dev.localllm.harness.application.dto.result.ThinkingToggleResult;
import dev.localllm.harness.application.exception.ModelNotFoundException;
import dev.localllm.harness.application.exception.ThinkingNotSupportedException;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ModelCapability;
import dev.localllm.harness.application.model.ModelInfo;
import dev.localllm.harness.application.port.in.ToggleThinkingUseCase;
import dev.localllm.harness.application.port.out.ModelCatalogPort;

/**
 * Allows thinking only for models that declare the thinking capability.
 */
public class ToggleThinkingService implements ToggleThinkingUseCase {

  private final ModelCatalogPort modelCatalogPort;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param modelCatalogPort installed models and their capabilities
   * @param mapper converts the resulting state into the result
   */
  public ToggleThinkingService(ModelCatalogPort modelCatalogPort, TurnApplicationMapper mapper) {
    this.modelCatalogPort = modelCatalogPort;
    this.mapper = mapper;
  }

  @Override
  public ThinkingToggleResult execute(ToggleThinkingCommand command) {
    if (command.enabled()) {
      ModelInfo model = modelCatalogPort.findModel(command.provider(), command.model())
          .orElseThrow(() -> new ModelNotFoundException(command.model()));
      if (!model.capabilities().contains(ModelCapability.THINKING)) {
        throw new ThinkingNotSupportedException(command.model());
      }
    }
    return mapper.toThinkingToggleResult(command);
  }
}
