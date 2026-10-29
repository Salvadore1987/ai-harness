package dev.localllm.harness.application.service;

import java.util.ArrayList;
import java.util.List;

import dev.localllm.harness.application.dto.command.SendMessageCommand;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.port.in.SendMessageUseCase;
import dev.localllm.harness.application.port.out.ChatModelPort;
import dev.localllm.harness.application.port.out.ConversationPort;
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

  private final ConversationPort conversationPort;
  private final SessionRegistrar sessionRegistrar;
  private final ChatModelPort chatModelPort;
  private final TurnOutputPort turnOutputPort;
  private final ConversationCompactor compactor;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param conversationPort conversation history storage
   * @param sessionRegistrar stores the session metadata on the first turn
   * @param chatModelPort language model
   * @param turnOutputPort presentation of the streamed turn
   * @param compactor compacts the history when it gets too long
   * @param mapper converts outcomes into results
   */
  public SendMessageService(
      ConversationPort conversationPort,
      SessionRegistrar sessionRegistrar,
      ChatModelPort chatModelPort,
      TurnOutputPort turnOutputPort,
      ConversationCompactor compactor,
      TurnApplicationMapper mapper) {
    this.conversationPort = conversationPort;
    this.sessionRegistrar = sessionRegistrar;
    this.chatModelPort = chatModelPort;
    this.turnOutputPort = turnOutputPort;
    this.compactor = compactor;
    this.mapper = mapper;
  }

  @Override
  public TurnResult execute(SendMessageCommand command) {
    ChatMessage userMessage = new ChatMessage(Role.USER, command.text());
    List<ChatMessage> messages = new ArrayList<>(conversationPort.history(command.sessionId()));
    messages.add(userMessage);
    ChatRequest request = new ChatRequest(command.model(), messages, command.thinkingEnabled());
    StringBuilder thinking = new StringBuilder();

    ChatCompletion completion = chatModelPort.stream(request, chunk -> present(chunk, thinking));

    sessionRegistrar.registerIfNew(command.sessionId(), command.workingDirectory(),
        command.model(), command.text());
    conversationPort.append(command.sessionId(), userMessage);
    conversationPort.append(
        command.sessionId(), new ChatMessage(Role.ASSISTANT, completion.historyContent()));
    if (!thinking.isEmpty()) {
      conversationPort.recordThinking(command.sessionId(), thinking.toString());
    }
    TurnResult result = mapper.toTurnResult(completion);
    turnOutputPort.onComplete(result);
    compactor.compactIfNeeded(command.sessionId(), command.model())
        .ifPresent(outcome -> turnOutputPort.onCompaction(mapper.toCompactionResult(outcome)));
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
}
