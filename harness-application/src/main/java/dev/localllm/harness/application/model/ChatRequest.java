package dev.localllm.harness.application.model;

import java.util.List;

/**
 * Request to generate the next assistant message.
 *
 * @param model name of the Ollama model, for example {@code qwen3.5:27b}
 * @param messages full conversation history, oldest first
 * @param thinkingEnabled whether the model is asked to reason before answering
 * @param tools tools the model may call; empty for a plain chat turn
 */
public record ChatRequest(
    String model,
    List<ChatMessage> messages,
    boolean thinkingEnabled,
    List<ToolSpec> tools) {

  /**
   * Makes the history and the tool list immutable.
   *
   * @param model name of the Ollama model
   * @param messages full conversation history
   * @param thinkingEnabled whether thinking is requested
   * @param tools tools the model may call
   */
  public ChatRequest {
    messages = List.copyOf(messages);
    tools = List.copyOf(tools);
  }

  /**
   * Creates a request without tools.
   *
   * @param model name of the Ollama model
   * @param messages full conversation history
   * @param thinkingEnabled whether thinking is requested
   */
  public ChatRequest(String model, List<ChatMessage> messages, boolean thinkingEnabled) {
    this(model, messages, thinkingEnabled, List.of());
  }
}
