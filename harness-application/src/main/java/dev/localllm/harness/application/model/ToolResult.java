package dev.localllm.harness.application.model;

import java.time.Duration;

/**
 * Outcome of one tool call.
 *
 * @param callId identifier of the call this result answers
 * @param toolName name of the called tool
 * @param output text sent back to the model; the error message if the call failed
 * @param error whether the call failed
 * @param truncated whether the output was cut to the configured size
 * @param duration time the tool took
 */
public record ToolResult(
    String callId,
    String toolName,
    String output,
    boolean error,
    boolean truncated,
    Duration duration) {

  /**
   * Builds the result of a call that was not executed.
   *
   * @param call the rejected call
   * @param reason message sent back to the model
   * @return failed result
   */
  public static ToolResult rejected(ToolCall call, String reason) {
    return new ToolResult(call.id(), call.name(), reason, true, false, Duration.ZERO);
  }
}
