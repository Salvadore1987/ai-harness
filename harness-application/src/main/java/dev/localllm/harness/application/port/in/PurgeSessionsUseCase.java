package dev.localllm.harness.application.port.in;

import dev.localllm.harness.application.dto.command.PurgeSessionsCommand;
import dev.localllm.harness.application.dto.result.PurgedSessionsResult;

/**
 * Deletes the stored sessions of a directory.
 */
public interface PurgeSessionsUseCase {

  /**
   * Deletes every stored session of the directory except the current one.
   *
   * @param command directory and the session to keep
   * @return how many sessions were deleted
   */
  PurgedSessionsResult execute(PurgeSessionsCommand command);
}
