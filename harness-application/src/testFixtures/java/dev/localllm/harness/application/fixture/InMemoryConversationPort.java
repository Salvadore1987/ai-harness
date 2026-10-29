package dev.localllm.harness.application.fixture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.port.out.ConversationPort;

/**
 * Conversation history kept in maps; compaction keeps the summary as a leading system message.
 */
public final class InMemoryConversationPort implements ConversationPort {

  private final Map<SessionId, List<ChatMessage>> histories = new HashMap<>();
  private final Map<SessionId, List<String>> thinking = new HashMap<>();

  @Override
  public void append(SessionId sessionId, ChatMessage message) {
    histories.computeIfAbsent(sessionId, id -> new ArrayList<>()).add(message);
  }

  @Override
  public List<ChatMessage> history(SessionId sessionId) {
    return List.copyOf(histories.getOrDefault(sessionId, List.of()));
  }

  @Override
  public void clear(SessionId sessionId) {
    histories.remove(sessionId);
  }

  @Override
  public void compact(SessionId sessionId, String summary, int keepLastMessages) {
    List<ChatMessage> history = histories.getOrDefault(sessionId, List.of());
    List<ChatMessage> compacted = new ArrayList<>();
    if (!summary.isEmpty()) {
      compacted.add(new ChatMessage(Role.SYSTEM, summary));
    }
    compacted.addAll(history.subList(Math.max(0, history.size() - keepLastMessages),
        history.size()));
    histories.put(sessionId, compacted);
  }

  @Override
  public void recordThinking(SessionId sessionId, String text) {
    thinking.computeIfAbsent(sessionId, id -> new ArrayList<>()).add(text);
  }

  /**
   * Reasoning offered for storage, oldest first.
   *
   * @param sessionId session to read
   * @return recorded reasoning
   */
  public List<String> thinking(SessionId sessionId) {
    return List.copyOf(thinking.getOrDefault(sessionId, List.of()));
  }
}
