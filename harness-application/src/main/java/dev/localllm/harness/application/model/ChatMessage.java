package dev.localllm.harness.application.model;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A single message of the conversation history sent to the model.
 *
 * <p>Model reasoning (thinking) is never part of a message: it is streamed to the user only and
 * kept out of the context window. An assistant message may carry the tool calls it requested; a
 * {@link Role#TOOL_RESULT} message carries the output of one call and references it.
 *
 * @param role author of the message
 * @param content message text; the tool output for {@link Role#TOOL_RESULT}
 * @param toolCalls tool calls requested by an assistant message, empty otherwise
 * @param toolCallId call answered by a {@link Role#TOOL_RESULT} message, {@code null} otherwise
 * @param toolName tool of the answered call, {@code null} for other roles
 */
public record ChatMessage(
    Role role,
    String content,
    List<ToolCall> toolCalls,
    @Nullable String toolCallId,
    @Nullable String toolName) {

  /**
   * Makes the tool call list immutable.
   *
   * @param role author of the message
   * @param content message text
   * @param toolCalls requested tool calls
   * @param toolCallId answered call
   * @param toolName tool of the answered call
   */
  public ChatMessage {
    toolCalls = List.copyOf(toolCalls);
  }

  /**
   * Creates a plain text message without tool calls.
   *
   * @param role author of the message
   * @param content message text
   */
  public ChatMessage(Role role, String content) {
    this(role, content, List.of(), null, null);
  }

  /**
   * Creates an assistant message with the tool calls it requested.
   *
   * @param content answer text, possibly empty
   * @param toolCalls requested tool calls
   * @return assistant message
   */
  public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
    return new ChatMessage(Role.ASSISTANT, content, toolCalls, null, null);
  }

  /**
   * Creates the message that returns a tool result to the model.
   *
   * @param result outcome of the call
   * @return tool result message
   */
  public static ChatMessage toolResult(ToolResult result) {
    return new ChatMessage(
        Role.TOOL_RESULT, result.output(), List.of(), result.callId(), result.toolName());
  }
}
