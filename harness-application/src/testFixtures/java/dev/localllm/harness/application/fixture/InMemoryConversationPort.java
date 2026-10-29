package dev.localllm.harness.application.fixture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.port.out.ConversationPort;

/**
 * Conversation history kept in a map.
 */
public final class InMemoryConversationPort implements ConversationPort {

  private final Map<SessionId, List<ChatMessage>> histories = new HashMap<>();

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
}
