package dev.localllm.harness.application.port.in;

import dev.localllm.harness.application.dto.command.RunAgentTurnCommand;
import dev.localllm.harness.application.dto.result.TurnResult;

/**
 * Runs an agent turn: the model may call tools until it answers without one.
 */
public interface RunAgentTurnUseCase {

  /**
   * Runs the agent loop for one user message.
   *
   * @param command session, working directory, model, message text and thinking flag
   * @return summary of the finished turn
   */
  TurnResult execute(RunAgentTurnCommand command);
}
