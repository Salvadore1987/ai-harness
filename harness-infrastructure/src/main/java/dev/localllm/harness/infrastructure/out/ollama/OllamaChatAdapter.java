package dev.localllm.harness.infrastructure.out.ollama;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.TokenUsage;
import dev.localllm.harness.application.model.TurnStatus;
import dev.localllm.harness.application.port.out.ChatModelPort;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ChatResponseSpringAiMapper;
import dev.localllm.harness.infrastructure.out.ollama.toolcall.CollectedToolCalls;
import dev.localllm.harness.infrastructure.out.ollama.toolcall.ToolCallCollector;
import dev.localllm.harness.infrastructure.out.ollama.toolcall.ToolCallMode;
import dev.localllm.harness.infrastructure.out.ollama.toolcall.ToolCallModeResolver;

/**
 * Streams answers from Ollama through the Spring AI {@link ChatClient}.
 *
 * <p>Per-request options (model, thinking) are passed as a customizer, so the defaults from
 * {@code spring.ai.ollama.chat.*} such as {@code num-ctx} and {@code keep-alive} are kept.
 * Cancellation completes the stream through {@code takeUntilOther}, which disposes the HTTP
 * exchange.
 *
 * <p>Tools are offered in the {@link ToolCallMode} configured for the model: natively or as a
 * text protocol. The {@code ToolCallingAdvisor} that {@link ChatClient} registers by default is
 * turned off: tool calls are returned to the application, which executes them in its own loop.
 * The stream ends early when the mode reports the answer complete. An answer that ends without
 * a single token, a known failure of the MLX runner, is requested once more.
 */
@Component
public class OllamaChatAdapter implements ChatModelPort {

  private static final Logger log = LoggerFactory.getLogger(OllamaChatAdapter.class);

  private final ChatClient chatClient;
  private final ChatResponseSpringAiMapper responseMapper;
  private final OllamaErrorTranslator errorTranslator;
  private final ToolCallModeResolver toolCallModes;
  private final AtomicReference<ActiveTurn> activeTurn = new AtomicReference<>();

  /**
   * Creates the adapter.
   *
   * @param chatClientBuilder auto-configured builder bound to the Ollama chat model
   * @param responseMapper converts streamed responses into chunks
   * @param errorTranslator translates client failures
   * @param toolCallModes selects how tools are passed to a model and its calls are read
   */
  public OllamaChatAdapter(
      ChatClient.Builder chatClientBuilder,
      ChatResponseSpringAiMapper responseMapper,
      OllamaErrorTranslator errorTranslator,
      ToolCallModeResolver toolCallModes) {
    this.chatClient = chatClientBuilder.build();
    this.responseMapper = responseMapper;
    this.errorTranslator = errorTranslator;
    this.toolCallModes = toolCallModes;
  }

  @Override
  public ChatCompletion stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer) {
    long startedAt = System.nanoTime();
    ChatCompletion completion = streamOnce(request, chunkConsumer, startedAt);
    if (isEmpty(completion)) {
      log.warn("Ollama ended the answer of model {} without a single token; asking again",
          request.model());
      completion = streamOnce(request, chunkConsumer, startedAt);
    }
    return completion;
  }

  private ChatCompletion streamOnce(
      ChatRequest request, Consumer<ChatChunk> chunkConsumer, long startedAt) {
    ActiveTurn turn = new ActiveTurn();
    activeTurn.set(turn);
    ToolCallMode toolCallMode = toolCallModes.resolve(request.model());
    TurnAccumulator accumulator =
        new TurnAccumulator(chunkConsumer, toolCallMode.newCollector(request.tools()));
    try {
      chatClient.prompt()
          .messages(toolCallMode.toMessages(request))
          .options(options(request))
          .tools(toolCallMode.toolCallbacks(request.tools()).toArray())
          .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
          .stream()
          .chatResponse()
          .takeUntilOther(turn.cancelSignal().asMono())
          .doOnNext(accumulator::accept)
          .takeUntil(response -> accumulator.isComplete())
          .blockLast();
    } catch (RuntimeException e) {
      if (!accumulator.hasReceivedChunks()) {
        throw errorTranslator.translateStreamFailure(e, request.model());
      }
      log.error("Ollama stream for model {} broke after a partial answer", request.model(), e);
      return accumulator.complete(TurnStatus.INTERRUPTED, startedAt);
    } finally {
      activeTurn.compareAndSet(turn, null);
    }
    TurnStatus status = turn.cancelled().get() ? TurnStatus.CANCELLED : TurnStatus.COMPLETED;
    return accumulator.complete(status, startedAt);
  }

  @Override
  public void cancelActive() {
    ActiveTurn turn = activeTurn.get();
    if (turn != null) {
      turn.cancelled().set(true);
      turn.cancelSignal().tryEmitEmpty();
    }
  }

  /**
   * The MLX runner of Ollama 0.40 sometimes ends a stream at once when the prompt is almost
   * fully cached: {@code done} without content, reasoning, tool calls or {@code eval_count}.
   */
  private static boolean isEmpty(ChatCompletion completion) {
    return completion.status() == TurnStatus.COMPLETED
        && completion.content().isEmpty()
        && completion.toolCalls().isEmpty()
        && completion.toolCallError() == null
        && completion.usage().completionTokens() == 0;
  }

  private static OllamaChatOptions.Builder options(ChatRequest request) {
    OllamaChatOptions.Builder builder = OllamaChatOptions.builder().model(request.model());
    return request.thinkingEnabled() ? builder.enableThinking() : builder.disableThinking();
  }

  private record ActiveTurn(Sinks.Empty<Void> cancelSignal, AtomicBoolean cancelled) {

    ActiveTurn() {
      this(Sinks.empty(), new AtomicBoolean());
    }
  }

  private final class TurnAccumulator {

    private final Consumer<ChatChunk> chunkConsumer;
    private final ToolCallCollector toolCalls;
    private final StringBuilder content = new StringBuilder();
    private TokenUsage usage = TokenUsage.EMPTY;
    private boolean receivedChunks;

    private TurnAccumulator(Consumer<ChatChunk> chunkConsumer, ToolCallCollector toolCalls) {
      this.chunkConsumer = chunkConsumer;
      this.toolCalls = toolCalls;
    }

    private synchronized void accept(ChatResponse response) {
      for (ChatChunk chunk : responseMapper.toChunks(response)) {
        receivedChunks = true;
        if (chunk instanceof ChatChunk.Content answer) {
          content.append(answer.text());
          show(toolCalls.visible(answer.text()));
        } else {
          chunkConsumer.accept(chunk);
        }
      }
      if (toolCalls.accept(response)) {
        receivedChunks = true;
      }
      TokenUsage reported = responseMapper.toTokenUsage(response.getMetadata().getUsage());
      if (reported.promptTokens() > 0 || reported.completionTokens() > 0) {
        usage = reported;
      }
    }

    private synchronized boolean isComplete() {
      return toolCalls.isComplete();
    }

    private synchronized boolean hasReceivedChunks() {
      return receivedChunks;
    }

    private synchronized ChatCompletion complete(TurnStatus status, long startedAt) {
      show(toolCalls.flush());
      CollectedToolCalls collected = toolCalls.finish(content.toString());
      Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
      return new ChatCompletion(collected.content(), status, usage, elapsed, collected.calls(),
          collected.error());
    }

    private void show(String text) {
      if (!text.isEmpty()) {
        chunkConsumer.accept(new ChatChunk.Content(text));
      }
    }
  }
}
