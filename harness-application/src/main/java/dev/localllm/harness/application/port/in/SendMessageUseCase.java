package dev.localllm.harness.application.port.in;

import dev.localllm.harness.application.dto.command.SendMessageCommand;
import dev.localllm.harness.application.dto.result.TurnResult;

/**
 * Sends a user message and streams the model answer to the user.
 */
public interface SendMessageUseCase {

  /**
   * Runs one chat turn.
   *
   * @param command session, model, message text and thinking flag
   * @return summary of the finished turn
   */
  TurnResult execute(SendMessageCommand command);
}
