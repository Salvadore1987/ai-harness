package dev.localllm.harness.application.port.out;

import dev.localllm.harness.application.dto.result.CompactionResult;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;

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
   * Shows that the model called a tool; the call is executed after this returns.
   *
   * @param call the requested call
   */
  void onToolCall(ToolCall call);

  /**
   * Shows the outcome of a tool call.
   *
   * @param result outcome of the call
   */
  void onToolResult(ToolResult result);

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
