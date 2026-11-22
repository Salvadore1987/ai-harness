package dev.localllm.harness.adapter.anthropic.toolcall;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.localllm.harness.application.model.ToolCall;

/**
 * Tool calls read from one answer.
 *
 * @param calls calls whose input was read and matches the schema
 * @param error why the calls are not returned, {@code null} if they all were read
 */
public record CollectedToolCalls(List<ToolCall> calls, @Nullable String error) {

  /**
   * Makes the call list immutable.
   *
   * @param calls calls with a valid input
   * @param error reading error
   */
  public CollectedToolCalls {
    calls = List.copyOf(calls);
  }
}
