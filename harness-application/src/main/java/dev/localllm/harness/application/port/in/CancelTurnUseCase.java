package dev.localllm.harness.application.port.in;

import dev.localllm.harness.application.dto.command.CancelTurnCommand;

/**
 * Cancels the generation that is currently running.
 */
public interface CancelTurnUseCase {

  /**
   * Cancels the running turn; does nothing if no turn is running.
   *
   * @param command session whose turn is cancelled
   */
  void execute(CancelTurnCommand command);
}
