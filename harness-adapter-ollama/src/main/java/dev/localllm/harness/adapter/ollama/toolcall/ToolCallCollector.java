package dev.localllm.harness.adapter.ollama.toolcall;

import org.springframework.ai.chat.model.ChatResponse;

/**
 * Reads the tool calls of one streamed answer and decides which answer text the user sees.
 */
public interface ToolCallCollector {

  /**
   * Filters an answer fragment before it is shown; a fragment may be held back until it is
   * clear whether it starts a tool call.
   *
   * @param fragment answer fragment as streamed
   * @return text to show now, possibly empty
   */
  String visible(String fragment);

  /**
   * Reads the native tool calls of a streamed response.
   *
   * @param response streamed response
   * @return whether the response carried tool calls
   */
  boolean accept(ChatResponse response);

  /**
   * Whether the answer is complete and the rest of the stream can be dropped: the model has
   * written its tool call and anything after it would be ignored.
   *
   * @return {@code true} to stop the stream
   */
  boolean isComplete();

  /**
   * Releases the text held back by {@link #visible} once the stream has ended.
   *
   * @return text to show, possibly empty
   */
  String flush();

  /**
   * Reads the tool calls of the whole answer.
   *
   * @param content full answer text as streamed
   * @return answer text for the history, the calls and the reading error
   */
  CollectedToolCalls finish(String content);
}
