package dev.localllm.harness.application.fixture;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.TokenUsage;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.TurnStatus;
import dev.localllm.harness.application.port.out.ChatModelPort;

/**
 * Scripted language model: replays configured chunks and records every request.
 *
 * <p>Steps queued with {@link #thenCall} and {@link #thenAnswer} are replayed one per request
 * before falling back to the chunks of {@link #respondWith}.
 */
public final class FakeChatModelPort implements ChatModelPort {

  private final List<ChatChunk> chunks = new ArrayList<>();
  private final List<ChatRequest> requests = new ArrayList<>();
  private final Deque<Step> steps = new ArrayDeque<>();
  private TurnStatus status = TurnStatus.COMPLETED;
  private TokenUsage usage = new TokenUsage(12, 34);
  private @Nullable RuntimeException failure;
  private int cancelCount;

  /**
   * Replays the given chunks on the next calls.
   *
   * @param scripted chunks to replay
   * @return this fake
   */
  public FakeChatModelPort respondWith(ChatChunk... scripted) {
    chunks.clear();
    chunks.addAll(List.of(scripted));
    return this;
  }

  /**
   * Queues a step in which the model requests tool calls.
   *
   * @param calls tool calls of the step
   * @return this fake
   */
  public FakeChatModelPort thenCall(ToolCall... calls) {
    steps.add(new Step(List.of(), List.of(calls)));
    return this;
  }

  /**
   * Queues a step in which the model answers without tool calls.
   *
   * @param scripted chunks of the answer
   * @return this fake
   */
  public FakeChatModelPort thenAnswer(ChatChunk... scripted) {
    steps.add(new Step(List.of(scripted), List.of()));
    return this;
  }

  /**
   * Ends the next turns with the given status.
   *
   * @param turnStatus status to report
   * @return this fake
   */
  public FakeChatModelPort endWith(TurnStatus turnStatus) {
    this.status = turnStatus;
    return this;
  }

  /**
   * Reports the given token usage.
   *
   * @param tokenUsage usage to report
   * @return this fake
   */
  public FakeChatModelPort reportUsage(TokenUsage tokenUsage) {
    this.usage = tokenUsage;
    return this;
  }

  /**
   * Throws the given exception instead of streaming.
   *
   * @param exception exception to throw
   * @return this fake
   */
  public FakeChatModelPort failWith(RuntimeException exception) {
    this.failure = exception;
    return this;
  }

  @Override
  public ChatCompletion stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer) {
    requests.add(request);
    RuntimeException toThrow = failure;
    if (toThrow != null) {
      throw toThrow;
    }
    Step step = steps.isEmpty() ? new Step(chunks, List.of()) : steps.poll();
    StringBuilder content = new StringBuilder();
    for (ChatChunk chunk : step.chunks()) {
      chunkConsumer.accept(chunk);
      if (chunk instanceof ChatChunk.Content contentChunk) {
        content.append(contentChunk.text());
      }
    }
    return new ChatCompletion(content.toString(), status, usage, Duration.ofMillis(250),
        step.calls());
  }

  @Override
  public void cancelActive() {
    cancelCount++;
  }

  /**
   * Requests received so far, oldest first.
   *
   * @return recorded requests
   */
  public List<ChatRequest> requests() {
    return List.copyOf(requests);
  }

  /**
   * How many times {@link #cancelActive()} was called.
   *
   * @return cancel count
   */
  public int cancelCount() {
    return cancelCount;
  }

  private record Step(List<ChatChunk> chunks, List<ToolCall> calls) {
  }
}
