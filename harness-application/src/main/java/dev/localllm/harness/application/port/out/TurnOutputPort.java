package dev.localllm.harness.application.port.out;

import dev.localllm.harness.application.dto.result.CompactionResult;
import dev.localllm.harness.application.dto.result.TurnResult;

/**
 * Presents a streamed turn to the user.
 */
public interface TurnOutputPort {

  /**
   * Shows a fragment of the model reasoning.
   *
   * @param text reasoning fragment
   */
  void onThinking(String text);

  /**
   * Shows a fragment of the answer.
   *
   * @param text answer fragment
   */
  void onContent(String text);

  /**
   * Finishes the turn presentation.
   *
   * @param result summary of the finished turn
   */
  void onComplete(TurnResult result);

  /**
   * Tells the user that the history was compacted automatically after the turn.
   *
   * @param result outcome of the compaction
   */
  void onCompaction(CompactionResult result);
}
