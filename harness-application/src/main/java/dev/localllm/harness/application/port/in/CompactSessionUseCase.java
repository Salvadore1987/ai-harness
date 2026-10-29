package dev.localllm.harness.application.port.in;

import dev.localllm.harness.application.dto.command.CompactSessionCommand;
import dev.localllm.harness.application.dto.result.CompactionResult;

/**
 * Compacts the history of a session on request.
 */
public interface CompactSessionUseCase {

  /**
   * Replaces the older part of the history with a model-written summary.
   *
   * @param command session and summarizing model
   * @return outcome of the compaction
   */
  CompactionResult execute(CompactSessionCommand command);
}
