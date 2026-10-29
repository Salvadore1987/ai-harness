package dev.localllm.harness.application.port.in;

import dev.localllm.harness.application.dto.command.ToggleThinkingCommand;
import dev.localllm.harness.application.dto.result.ThinkingToggleResult;

/**
 * Turns the thinking mode on or off.
 */
public interface ToggleThinkingUseCase {

  /**
   * Validates the requested thinking state against the model capabilities.
   *
   * @param command current model and requested state
   * @return the resulting state
   * @throws dev.localllm.harness.application.exception.ThinkingNotSupportedException if thinking
   *     is enabled for a model without thinking support
   */
  ThinkingToggleResult execute(ToggleThinkingCommand command);
}
