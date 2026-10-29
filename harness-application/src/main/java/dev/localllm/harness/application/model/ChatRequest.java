package dev.localllm.harness.application.model;

import java.util.List;

/**
 * Request to generate the next assistant message.
 *
 * @param model name of the Ollama model, for example {@code qwen3.5:27b}
 * @param messages full conversation history, oldest first
 * @param thinkingEnabled whether the model is asked to reason before answering
 */
public record ChatRequest(String model, List<ChatMessage> messages, boolean thinkingEnabled) {

  /**
   * Makes the history immutable.
   *
   * @param model name of the Ollama model
   * @param messages full conversation history
   * @param thinkingEnabled whether thinking is requested
   */
  public ChatRequest {
    messages = List.copyOf(messages);
  }
}
