package dev.localllm.harness.infrastructure.out.memory;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.port.out.ConversationPort;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.out.memory.mapper.ChatMessageMemoryMapper;

/**
 * Conversation history on the Spring AI chat memory backed by JSONL files.
 */
@Component
public class ChatMemoryConversationAdapter implements ConversationPort {

  private final TokenWindowChatMemory chatMemory;
  private final JsonlChatMemoryRepository repository;
  private final ChatMessageMemoryMapper mapper;
  private final boolean persistThinking;

  /**
   * Creates the adapter.
   *
   * @param chatMemory chat memory with the compaction window
   * @param repository JSONL storage, used for reasoning lines
   * @param mapper converts between core and Spring AI messages
   * @param properties harness settings with the reasoning persistence flag
   */
  public ChatMemoryConversationAdapter(
      TokenWindowChatMemory chatMemory,
      JsonlChatMemoryRepository repository,
      ChatMessageMemoryMapper mapper,
      HarnessProperties properties) {
    this.chatMemory = chatMemory;
    this.repository = repository;
    this.mapper = mapper;
    this.persistThinking = properties.thinking().persist();
  }

  @Override
  public void append(SessionId sessionId, ChatMessage message) {
    chatMemory.add(sessionId.toString(), mapper.toSpringAiMessage(message));
  }

  @Override
  public List<ChatMessage> history(SessionId sessionId) {
    return mapper.toChatMessages(chatMemory.get(sessionId.toString()));
  }

  @Override
  public void clear(SessionId sessionId) {
    chatMemory.clear(sessionId.toString());
  }

  @Override
  public void compact(SessionId sessionId, String summary, int keepLastMessages) {
    chatMemory.compact(sessionId.toString(), summary, keepLastMessages);
  }

  @Override
  public void recordThinking(SessionId sessionId, String thinking) {
    if (persistThinking) {
      repository.appendThinking(sessionId.toString(), thinking);
    }
  }
}
