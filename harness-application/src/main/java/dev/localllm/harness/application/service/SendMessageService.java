package dev.localllm.harness.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import dev.localllm.harness.application.dto.command.SendMessageCommand;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.CompactionOutcome;
import dev.localllm.harness.application.model.ContextUsage;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.application.port.in.SendMessageUseCase;
import dev.localllm.harness.application.port.out.ChatModelPort;
import dev.localllm.harness.application.port.out.ConversationPort;
import dev.localllm.harness.application.port.out.SessionCatalogPort;
import dev.localllm.harness.application.port.out.TurnOutputPort;

/**
 * Runs one chat turn: streams the answer to the user and records the exchange in the history.
 *
 * <p>The user message is stored only after the model has answered, so a failed request leaves the
 * history unchanged. Reasoning is presented, offered to {@link ConversationPort#recordThinking}
 * and never added to the history. When the history reaches the compaction threshold after the
 * turn, it is compacted before the next request.
 */
public class SendMessageService implements SendMessageUseCase {

  private static final String CANCELLED_MARKER = "\n[cancelled]";
  private static final String INTERRUPTED_MARKER = "\n[interrupted]";
  private static final int TITLE_LENGTH = 60;

  private final ConversationPort conversationPort;
  private final SessionCatalogPort sessionCatalogPort;
  private final ChatModelPort chatModelPort;
  private final TurnOutputPort turnOutputPort;
  private final ContextMeter contextMeter;
  private final ConversationCompactor compactor;
  private final TurnApplicationMapper mapper;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param conversationPort conversation history storage
   * @param sessionCatalogPort session metadata storage
   * @param chatModelPort language model
   * @param turnOutputPort presentation of the streamed turn
   * @param contextMeter measures the history against the context window
   * @param compactor compacts the history when it gets too long
   * @param mapper converts outcomes into results
   * @param clock source of the session creation time
   */
  public SendMessageService(
      ConversationPort conversationPort,
      SessionCatalogPort sessionCatalogPort,
      ChatModelPort chatModelPort,
      TurnOutputPort turnOutputPort,
      ContextMeter contextMeter,
      ConversationCompactor compactor,
      TurnApplicationMapper mapper,
      Clock clock) {
    this.conversationPort = conversationPort;
    this.sessionCatalogPort = sessionCatalogPort;
    this.chatModelPort = chatModelPort;
    this.turnOutputPort = turnOutputPort;
    this.contextMeter = contextMeter;
    this.compactor = compactor;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Override
  public TurnResult execute(SendMessageCommand command) {
    ChatMessage userMessage = new ChatMessage(Role.USER, command.text());
    List<ChatMessage> messages = new ArrayList<>(conversationPort.history(command.sessionId()));
    messages.add(userMessage);
    ChatRequest request = new ChatRequest(command.model(), messages, command.thinkingEnabled());
    StringBuilder thinking = new StringBuilder();

    ChatCompletion completion = chatModelPort.stream(request, chunk -> present(chunk, thinking));

    registerSession(command);
    conversationPort.append(command.sessionId(), userMessage);
    conversationPort.append(
        command.sessionId(), new ChatMessage(Role.ASSISTANT, historyContent(completion)));
    if (!thinking.isEmpty()) {
      conversationPort.recordThinking(command.sessionId(), thinking.toString());
    }
    TurnResult result = mapper.toTurnResult(completion);
    turnOutputPort.onComplete(result);
    compactIfNeeded(command);
    return result;
  }

  private void present(ChatChunk chunk, StringBuilder thinking) {
    switch (chunk) {
      case ChatChunk.Thinking reasoning -> {
        thinking.append(reasoning.text());
        turnOutputPort.onThinking(reasoning.text());
      }
      case ChatChunk.Content content -> turnOutputPort.onContent(content.text());
    }
  }

  private void registerSession(SendMessageCommand command) {
    if (sessionCatalogPort.find(command.sessionId()).isEmpty()) {
      sessionCatalogPort.register(new SessionInfo(command.sessionId(),
          command.workingDirectory(), command.model(), title(command.text()),
          Instant.now(clock), 0));
    }
  }

  private void compactIfNeeded(SendMessageCommand command) {
    ContextUsage usage = contextMeter.measure(
        conversationPort.history(command.sessionId()), command.model());
    if (contextMeter.isOverThreshold(usage)) {
      CompactionOutcome outcome = compactor.compact(command.sessionId(), command.model());
      turnOutputPort.onCompaction(mapper.toCompactionResult(outcome));
    }
  }

  private static String title(String text) {
    String singleLine = text.strip().replaceAll("\\s+", " ");
    return singleLine.length() <= TITLE_LENGTH ? singleLine : singleLine.substring(0, TITLE_LENGTH);
  }

  private static String historyContent(ChatCompletion completion) {
    return switch (completion.status()) {
      case COMPLETED -> completion.content();
      case CANCELLED -> completion.content() + CANCELLED_MARKER;
      case INTERRUPTED -> completion.content() + INTERRUPTED_MARKER;
    };
  }
}
