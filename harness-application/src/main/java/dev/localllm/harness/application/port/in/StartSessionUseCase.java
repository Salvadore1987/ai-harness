package dev.localllm.harness.application.port.in;

import dev.localllm.harness.application.dto.command.StartSessionCommand;
import dev.localllm.harness.application.dto.result.SessionResult;

/**
 * Starts a new chat session.
 */
public interface StartSessionUseCase {

  /**
   * Creates a session and discards the history of the previous one.
   *
   * @param command session being replaced, if any
   * @return the new session
   */
  SessionResult execute(StartSessionCommand command);
}
