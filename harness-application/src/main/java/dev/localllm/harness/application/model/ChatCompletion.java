package dev.localllm.harness.application.model;

import java.time.Duration;
import java.util.List;

/**
 * Outcome of one streamed model turn.
 *
 * @param content answer text received so far (complete unless the turn was cut short)
 * @param status how the turn ended
 * @param usage token counters, {@link TokenUsage#EMPTY} if the server sent none
 * @param elapsed wall-clock duration of the turn
 * @param toolCalls tool calls requested by the model, empty for a final answer
 */
public record ChatCompletion(
    String content,
    TurnStatus status,
    TokenUsage usage,
    Duration elapsed,
    List<ToolCall> toolCalls) {

  private static final String CANCELLED_MARKER = "\n[cancelled]";
  private static final String INTERRUPTED_MARKER = "\n[interrupted]";

  /**
   * Makes the tool call list immutable.
   *
   * @param content answer text
   * @param status how the turn ended
   * @param usage token counters
   * @param elapsed wall-clock duration
   * @param toolCalls requested tool calls
   */
  public ChatCompletion {
    toolCalls = List.copyOf(toolCalls);
  }

  /**
   * Creates the outcome of a turn without tool calls.
   *
   * @param content answer text
   * @param status how the turn ended
   * @param usage token counters
   * @param elapsed wall-clock duration
   */
  public ChatCompletion(String content, TurnStatus status, TokenUsage usage, Duration elapsed) {
    this(content, status, usage, elapsed, List.of());
  }

  /**
   * Answer text as it is stored in the history: a cut-short answer is marked so that the model
   * knows it was not finished.
   *
   * @return content with a status marker
   */
  public String historyContent() {
    return switch (status) {
      case COMPLETED -> content;
      case CANCELLED -> content + CANCELLED_MARKER;
      case INTERRUPTED -> content + INTERRUPTED_MARKER;
    };
  }
}
