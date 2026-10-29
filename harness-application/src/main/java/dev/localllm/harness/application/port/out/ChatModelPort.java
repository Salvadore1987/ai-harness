package dev.localllm.harness.application.port.out;

import java.util.function.Consumer;

import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatRequest;

/**
 * Streams answers from the language model.
 */
public interface ChatModelPort {

  /**
   * Generates the next assistant message, blocking until the stream ends.
   *
   * <p>Failures before the first chunk are thrown; failures after it end the turn with status
   * {@code INTERRUPTED} and the partial content.
   *
   * @param request model, history and thinking flag
   * @param chunkConsumer receives every thinking and content fragment as it arrives
   * @return the outcome of the turn
   */
  ChatCompletion stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer);

  /**
   * Cancels the stream that is currently running, if any; the blocked {@link #stream} call then
   * returns with status {@code CANCELLED}.
   */
  void cancelActive();
}
