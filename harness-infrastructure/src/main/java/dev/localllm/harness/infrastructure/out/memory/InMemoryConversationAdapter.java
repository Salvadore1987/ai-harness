package dev.localllm.harness.infrastructure.out.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.port.out.ConversationPort;

/**
 * Keeps conversation histories in process memory; replaced by JSONL storage in M2.
 */
@Component
public class InMemoryConversationAdapter implements ConversationPort {

  private final Map<SessionId, List<ChatMessage>> histories = new ConcurrentHashMap<>();

  @Override
  public void append(SessionId sessionId, ChatMessage message) {
    histories.compute(sessionId, (id, existing) -> {
      List<ChatMessage> updated = existing == null ? new ArrayList<>() : existing;
      updated.add(message);
      return updated;
    });
  }

  @Override
  public List<ChatMessage> history(SessionId sessionId) {
    List<ChatMessage> history = histories.get(sessionId);
    return history == null ? List.of() : List.copyOf(history);
  }

  @Override
  public void clear(SessionId sessionId) {
    histories.remove(sessionId);
  }
}
