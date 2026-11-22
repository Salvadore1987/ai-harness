package dev.localllm.harness.application.port.in;

import dev.localllm.harness.application.dto.command.ResumeSessionCommand;
import dev.localllm.harness.application.dto.result.SessionSummaryResult;

/**
 * Continues a stored session.
 */
public interface ResumeSessionUseCase {

  /**
   * Finds the session to continue.
   *
   * @param command directory and session reference
   * @return the session to continue
   * @throws dev.localllm.harness.application.exception.SessionNotFoundException if no session
   *     matches
   * @throws dev.localllm.harness.application.exception.ProviderUnavailableException if the
   *     provider of the session cannot be used
   */
  SessionSummaryResult execute(ResumeSessionCommand command);
}
