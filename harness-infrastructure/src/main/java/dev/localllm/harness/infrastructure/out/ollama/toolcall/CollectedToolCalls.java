package dev.localllm.harness.infrastructure.out.ollama.toolcall;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.localllm.harness.application.model.ToolCall;

/**
 * Tool calls read from one answer.
 *
 * @param content answer text for the history, without a tool call that was read
 * @param calls calls the model requested
 * @param error why a call written as text could not be read, {@code null} otherwise
 */
public record CollectedToolCalls(String content, List<ToolCall> calls, @Nullable String error) {

  /**
   * Makes the call list immutable.
   *
   * @param content answer text
   * @param calls requested calls
   * @param error reading error
   */
  public CollectedToolCalls {
    calls = List.copyOf(calls);
  }
}
