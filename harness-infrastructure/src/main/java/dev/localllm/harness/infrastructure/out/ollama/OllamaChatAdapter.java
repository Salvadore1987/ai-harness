package dev.localllm.harness.infrastructure.out.ollama;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.TurnStatus;
import dev.localllm.harness.application.port.out.ChatModelPort;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ChatMessageSpringAiMapper;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ChatResponseSpringAiMapper;
import dev.localllm.harness.infrastructure.out.ollama.toolcall.NativeToolCallMode;

/**
 * Streams answers from Ollama through the Spring AI {@link ChatClient}.
 *
 * <p>Per-request options (model, thinking) are passed as a customizer, so the defaults from
 * {@code spring.ai.ollama.chat.*} such as {@code num-ctx} and {@code keep-alive} are kept.
 * Cancellation completes the stream through {@code takeUntilOther}, which disposes the HTTP
 * exchange.
 *
 * <p>Tools are passed natively through {@link NativeToolCallMode}. The {@code ToolCallingAdvisor}
 * that {@link ChatClient} registers by default is turned off: tool calls are returned to the
 * application, which executes them in its own loop.
 */
@Component
public class OllamaChatAdapter implements ChatModelPort {

  private static final Logger log = LoggerFactory.getLogger(OllamaChatAdapter.class);

  private final ChatClient chatClient;
  private final ChatMessageSpringAiMapper messageMapper;
  private final ChatResponseSpringAiMapper responseMapper;
  private final OllamaErrorTranslator errorTranslator;
  private final NativeToolCallMode toolCallMode;
  private final AtomicReference<ActiveTurn> activeTurn = new AtomicReference<>();

  /**
   * Creates the adapter.
   *
   * @param chatClientBuilder auto-configured builder bound to the Ollama chat model
   * @param messageMapper converts the history into Spring AI messages
   * @param responseMapper converts streamed responses into chunks
   * @param errorTranslator translates client failures
   * @param toolCallMode passes tools to the model and reads its tool calls
   */
  public OllamaChatAdapter(
      ChatClient.Builder chatClientBuilder,
      ChatMessageSpringAiMapper messageMapper,
      ChatResponseSpringAiMapper responseMapper,
      OllamaErrorTranslator errorTranslator,
      NativeToolCallMode toolCallMode) {
    this.chatClient = chatClientBuilder.build();
    this.messageMapper = messageMapper;
    this.responseMapper = responseMapper;
    this.errorTranslator = errorTranslator;
    this.toolCallMode = toolCallMode;
  }

  @Override
  public ChatCompletion stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer) {
    long startedAt = System.nanoTime();
    ActiveTurn turn = new ActiveTurn();
    activeTurn.set(turn);
    TurnAccumulator accumulator = new TurnAccumulator(chunkConsumer);
    try {
      chatClient.prompt()
          .messages(messageMapper.toSpringAiMessages(request.messages()))
          .options(options(request))
          .tools(toolCallMode.toolCallbacks(request.tools()).toArray())
          .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
          .stream()
          .chatResponse()
          .takeUntilOther(turn.cancelSignal().asMono())
          .doOnNext(accumulator::accept)
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
    private final StringBuilder content = new StringBuilder();
    private final List<ToolCall> toolCalls = new ArrayList<>();
    private TokenUsage usage = TokenUsage.EMPTY;
    private boolean receivedChunks;

    private TurnAccumulator(Consumer<ChatChunk> chunkConsumer) {
      this.chunkConsumer = chunkConsumer;
    }

    private synchronized void accept(ChatResponse response) {
      List<ChatChunk> chunks = responseMapper.toChunks(response);
      for (ChatChunk chunk : chunks) {
        receivedChunks = true;
        if (chunk instanceof ChatChunk.Content answer) {
          content.append(answer.text());
        }
        chunkConsumer.accept(chunk);
      }
      List<ToolCall> calls = toolCallMode.toolCalls(response);
      if (!calls.isEmpty()) {
        receivedChunks = true;
        toolCalls.addAll(calls);
      }
      TokenUsage reported = responseMapper.toTokenUsage(response.getMetadata().getUsage());
      if (reported.promptTokens() > 0 || reported.completionTokens() > 0) {
        usage = reported;
      }
    }

    private synchronized boolean hasReceivedChunks() {
      return receivedChunks;
    }

    private synchronized ChatCompletion complete(TurnStatus status, long startedAt) {
      Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
      return new ChatCompletion(content.toString(), status, usage, elapsed, toolCalls);
    }
  }
}
