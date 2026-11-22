package dev.localllm.harness.adapter.anthropic;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.AsyncStreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawContentBlockDeltaEvent;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawMessageDeltaEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.RefusalStopDetails;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.Usage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.localllm.harness.adapter.anthropic.config.AnthropicProperties;
import dev.localllm.harness.adapter.anthropic.mapper.ChatMessageAnthropicMapper;
import dev.localllm.harness.adapter.anthropic.mapper.ToolSpecAnthropicMapper;
import dev.localllm.harness.adapter.anthropic.mapper.UsageAnthropicMapper;
import dev.localllm.harness.adapter.anthropic.toolcall.CollectedToolCalls;
import dev.localllm.harness.adapter.anthropic.toolcall.ToolInputValidator;
import dev.localllm.harness.adapter.anthropic.toolcall.ToolUseCollector;
import dev.localllm.harness.application.exception.LlmResponseException;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.TokenUsage;
import dev.localllm.harness.application.model.TurnStatus;

/**
 * Streams answers from the Claude Messages API through the Anthropic Java SDK.
 *
 * <p>Thinking is adaptive and cannot be turned off on current Claude models, so the thinking
 * flag of the request only controls whether the reasoning is shown: {@code display: summarized}
 * streams a readable summary, {@code omitted} streams nothing. How deeply the model thinks is the
 * {@code effort} from the settings.
 *
 * <p>Tools stream their input eagerly and are checked against their schema here. Tool calls are
 * returned only for an answer that ended normally: {@code max_tokens} cuts the answer and possibly
 * a tool input short, so it ends as {@code INTERRUPTED} without calls; a {@code refusal} is
 * reported as {@link LlmResponseException} with the refusal category.
 *
 * <p>The stream is read through the asynchronous client: the calling thread only waits for its
 * end, so a cancellation returns at once, even while the request still waits for the response
 * headers, and closes the HTTP stream. A stream that breaks after the first chunk ends as
 * {@code INTERRUPTED}, as with Ollama.
 */
@Component
public class AnthropicChatAdapter {

  private static final Logger log = LoggerFactory.getLogger(AnthropicChatAdapter.class);

  private final AnthropicClient client;
  private final AnthropicProperties properties;
  private final ChatMessageAnthropicMapper messageMapper;
  private final ToolSpecAnthropicMapper toolMapper;
  private final UsageAnthropicMapper usageMapper;
  private final ToolInputValidator toolInputValidator;
  private final AnthropicErrorTranslator errorTranslator;
  private final AtomicReference<ActiveTurn> activeTurn = new AtomicReference<>();

  /**
   * Creates the adapter.
   *
   * @param client Claude API client
   * @param properties Anthropic settings with the output limit and the effort
   * @param messageMapper converts the history into request messages
   * @param toolMapper converts the offered tools
   * @param usageMapper converts the token counters
   * @param toolInputValidator checks tool inputs against their schema
   * @param errorTranslator translates SDK failures
   */
  public AnthropicChatAdapter(
      AnthropicClient client,
      AnthropicProperties properties,
      ChatMessageAnthropicMapper messageMapper,
      ToolSpecAnthropicMapper toolMapper,
      UsageAnthropicMapper usageMapper,
      ToolInputValidator toolInputValidator,
      AnthropicErrorTranslator errorTranslator) {
    this.client = client;
    this.properties = properties;
    this.messageMapper = messageMapper;
    this.toolMapper = toolMapper;
    this.usageMapper = usageMapper;
    this.toolInputValidator = toolInputValidator;
    this.errorTranslator = errorTranslator;
  }

  /**
   * Generates the next assistant message, blocking until the stream ends.
   *
   * @param request model, history, thinking flag and tools
   * @param chunkConsumer receives every thinking and content fragment as it arrives
   * @return the outcome of the turn
   * @throws LlmResponseException if the model refused to answer or the request failed
   */
  public ChatCompletion stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer) {
    long startedAt = System.nanoTime();
    ActiveTurn turn = new ActiveTurn();
    activeTurn.set(turn);
    TurnAccumulator accumulator = new TurnAccumulator(
        chunkConsumer, new ToolUseCollector(request.tools(), toolInputValidator));
    Optional<Throwable> failure;
    try {
      AsyncStreamResponse<RawMessageStreamEvent> response =
          client.async().messages().createStreaming(params(request));
      turn.attach(response);
      response.subscribe(new AsyncStreamResponse.Handler<RawMessageStreamEvent>() {
        @Override
        public void onNext(RawMessageStreamEvent event) {
          accumulator.accept(event);
        }

        @Override
        public void onComplete(Optional<Throwable> error) {
          turn.finish(error);
        }
      });
      failure = turn.awaitEnd();
    } catch (RuntimeException e) {
      failure = Optional.of(e);
    } finally {
      activeTurn.compareAndSet(turn, null);
    }
    accumulator.seal();
    if (turn.cancelled().get()) {
      return accumulator.complete(TurnStatus.CANCELLED, startedAt);
    }
    if (failure.isPresent()) {
      if (!accumulator.hasReceivedChunks()) {
        throw errorTranslator.translateStreamFailure(failure.get(), request.model());
      }
      log.error("Claude stream for model {} broke after a partial answer", request.model(),
          failure.get());
      return accumulator.complete(TurnStatus.INTERRUPTED, startedAt);
    }
    return accumulator.complete(endStatus(accumulator, request.model()), startedAt);
  }

  /**
   * Cancels the running stream, if any; the blocked {@link #stream} call then returns with
   * status {@code CANCELLED}.
   */
  public void cancelActive() {
    ActiveTurn turn = activeTurn.get();
    if (turn != null) {
      turn.cancel();
    }
  }

  private MessageCreateParams params(ChatRequest request) {
    MessageCreateParams.Builder builder = MessageCreateParams.builder()
        .model(request.model())
        .maxTokens(properties.maxTokens())
        .messages(messageMapper.toMessageParams(request.messages()))
        .thinking(ThinkingConfigAdaptive.builder()
            .display(request.thinkingEnabled()
                ? ThinkingConfigAdaptive.Display.SUMMARIZED
                : ThinkingConfigAdaptive.Display.OMITTED)
            .build())
        .outputConfig(OutputConfig.builder()
            .effort(OutputConfig.Effort.of(properties.effort().name().toLowerCase(Locale.ROOT)))
            .build());
    String system = messageMapper.toSystemPrompt(request.messages());
    if (!system.isEmpty()) {
      builder.system(system);
    }
    toolMapper.toTools(request.tools()).forEach(builder::addTool);
    return builder.build();
  }

  private static TurnStatus endStatus(TurnAccumulator accumulator, String model) {
    StopReason stopReason = accumulator.stopReason();
    if (stopReason == null) {
      if (!accumulator.hasReceivedChunks()) {
        throw new LlmResponseException(
            "Claude API stream for model " + model + " ended without an answer");
      }
      log.error("Claude stream for model {} ended without a stop reason", model);
      return TurnStatus.INTERRUPTED;
    }
    if (StopReason.REFUSAL.equals(stopReason)) {
      throw new LlmResponseException(refusalMessage(accumulator.refusal()));
    }
    if (StopReason.MAX_TOKENS.equals(stopReason)
        || StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED.equals(stopReason)) {
      log.warn("Claude answer of model {} was cut short: {}", model, stopReason.asString());
      return TurnStatus.INTERRUPTED;
    }
    return TurnStatus.COMPLETED;
  }

  private static String refusalMessage(@Nullable RefusalStopDetails details) {
    StringBuilder message = new StringBuilder("Claude declined the request");
    if (details != null) {
      details.category().ifPresent(
          category -> message.append(" (category: ").append(category.asString()).append(')'));
      details.explanation().ifPresent(explanation -> message.append(": ").append(explanation));
    }
    return message.toString();
  }

  private static final class ActiveTurn {

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CompletableFuture<Optional<Throwable>> end = new CompletableFuture<>();
    private final AtomicReference<@Nullable AsyncStreamResponse<RawMessageStreamEvent>>
        response = new AtomicReference<>();

    private AtomicBoolean cancelled() {
      return cancelled;
    }

    private void attach(AsyncStreamResponse<RawMessageStreamEvent> stream) {
      response.set(stream);
      if (cancelled.get()) {
        stream.close();
      }
    }

    private void finish(Optional<Throwable> error) {
      end.complete(error);
    }

    /** Waits until the stream ends, fails or is cancelled. */
    private Optional<Throwable> awaitEnd() {
      try {
        return end.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        cancel();
        return Optional.empty();
      } catch (ExecutionException e) {
        return Optional.of(e.getCause() == null ? e : e.getCause());
      }
    }

    private void cancel() {
      cancelled.set(true);
      end.complete(Optional.empty());
      AsyncStreamResponse<RawMessageStreamEvent> stream = response.get();
      if (stream != null) {
        stream.close();
      }
    }
  }

  private final class TurnAccumulator {

    private final Consumer<ChatChunk> chunkConsumer;
    private final ToolUseCollector toolCalls;
    private final StringBuilder content = new StringBuilder();
    private @Nullable Usage startUsage;
    private long outputTokens;
    private @Nullable StopReason stopReason;
    private @Nullable RefusalStopDetails refusal;
    private boolean receivedChunks;
    private boolean sealed;

    private TurnAccumulator(Consumer<ChatChunk> chunkConsumer, ToolUseCollector toolCalls) {
      this.chunkConsumer = chunkConsumer;
      this.toolCalls = toolCalls;
    }

    /** Called on a thread of the SDK; events after the turn has ended are dropped. */
    private synchronized void accept(RawMessageStreamEvent event) {
      if (sealed) {
        return;
      }
      event.messageStart().ifPresent(start -> startUsage = start.message().usage());
      event.contentBlockStart().ifPresent(this::acceptBlockStart);
      event.contentBlockDelta().ifPresent(this::acceptDelta);
      event.messageDelta().ifPresent(this::acceptMessageDelta);
    }

    private void acceptBlockStart(RawContentBlockStartEvent start) {
      start.contentBlock().toolUse().ifPresent(toolUse -> {
        receivedChunks = true;
        toolCalls.start(start.index(), toolUse.id(), toolUse.name());
      });
    }

    private void acceptDelta(RawContentBlockDeltaEvent event) {
      event.delta().text().ifPresent(delta -> {
        content.append(delta.text());
        emit(new ChatChunk.Content(delta.text()));
      });
      event.delta().thinking().ifPresent(delta -> emit(new ChatChunk.Thinking(delta.thinking())));
      event.delta().inputJson().ifPresent(
          delta -> toolCalls.append(event.index(), delta.partialJson()));
    }

    private void acceptMessageDelta(RawMessageDeltaEvent event) {
      outputTokens = event.usage().outputTokens();
      event.delta().stopReason().ifPresent(reason -> stopReason = reason);
      event.delta().stopDetails().ifPresent(details -> refusal = details);
    }

    private void emit(ChatChunk chunk) {
      if (!chunk.text().isEmpty()) {
        receivedChunks = true;
        chunkConsumer.accept(chunk);
      }
    }

    private synchronized void seal() {
      sealed = true;
    }

    private synchronized boolean hasReceivedChunks() {
      return receivedChunks;
    }

    private synchronized @Nullable StopReason stopReason() {
      return stopReason;
    }

    private synchronized @Nullable RefusalStopDetails refusal() {
      return refusal;
    }

    private synchronized ChatCompletion complete(TurnStatus status, long startedAt) {
      TokenUsage usage = startUsage == null
          ? TokenUsage.EMPTY : usageMapper.toTokenUsage(startUsage, outputTokens);
      Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
      if (status != TurnStatus.COMPLETED || !toolCalls.hasCalls()) {
        return new ChatCompletion(content.toString(), status, usage, elapsed);
      }
      CollectedToolCalls collected = toolCalls.finish();
      return new ChatCompletion(content.toString(), status, usage, elapsed, collected.calls(),
          collected.error());
    }
  }
}
