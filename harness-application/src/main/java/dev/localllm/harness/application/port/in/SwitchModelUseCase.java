package dev.localllm.harness.application.port.in;

import dev.localllm.harness.application.dto.command.SwitchModelCommand;
import dev.localllm.harness.application.dto.result.ModelSummaryResult;

/**
 * Validates and selects another installed model.
 */
public interface SwitchModelUseCase {

  /**
   * Checks that the model is installed and returns its capabilities.
   *
   * @param command target model
   * @return the selected model
   * @throws dev.localllm.harness.application.exception.ModelNotFoundException if the model is not
   *     installed
   */
  ModelSummaryResult execute(SwitchModelCommand command);
}
