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
 * history unchanged. Reasoning chunks are presented but never stored.
 */
public class SendMessageService implements SendMessageUseCase {

  private static final String CANCELLED_MARKER = "\n[cancelled]";
  private static final String INTERRUPTED_MARKER = "\n[interrupted]";

  private final ConversationPort conversationPort;
  private final ChatModelPort chatModelPort;
  private final TurnOutputPort turnOutputPort;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param conversationPort conversation history storage
   * @param chatModelPort language model
   * @param turnOutputPort presentation of the streamed turn
   * @param mapper converts the completion into the result
   */
  public SendMessageService(
      ConversationPort conversationPort,
      ChatModelPort chatModelPort,
      TurnOutputPort turnOutputPort,
      TurnApplicationMapper mapper) {
    this.conversationPort = conversationPort;
    this.chatModelPort = chatModelPort;
    this.turnOutputPort = turnOutputPort;
    this.mapper = mapper;
  }

  @Override
  public TurnResult execute(SendMessageCommand command) {
    ChatMessage userMessage = new ChatMessage(Role.USER, command.text());
    List<ChatMessage> messages = new ArrayList<>(conversationPort.history(command.sessionId()));
    messages.add(userMessage);
    ChatRequest request = new ChatRequest(command.model(), messages, command.thinkingEnabled());

    ChatCompletion completion = chatModelPort.stream(request, this::present);

    conversationPort.append(command.sessionId(), userMessage);
    conversationPort.append(
        command.sessionId(), new ChatMessage(Role.ASSISTANT, historyContent(completion)));
    TurnResult result = mapper.toTurnResult(completion);
    turnOutputPort.onComplete(result);
    return result;
  }

  private void present(ChatChunk chunk) {
    switch (chunk) {
      case ChatChunk.Thinking thinking -> turnOutputPort.onThinking(thinking.text());
      case ChatChunk.Content content -> turnOutputPort.onContent(content.text());
    }
  }

  private static String historyContent(ChatCompletion completion) {
    return switch (completion.status()) {
      case COMPLETED -> completion.content();
      case CANCELLED -> completion.content() + CANCELLED_MARKER;
      case INTERRUPTED -> completion.content() + INTERRUPTED_MARKER;
    };
  }
}
