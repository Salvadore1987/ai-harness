package dev.localllm.harness.application.port.out;

import java.util.List;

import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.SessionId;

/**
 * Stores the conversation history of a session.
 */
public interface ConversationPort {

  /**
   * Appends a message to the end of the history.
   *
   * @param sessionId session to append to
   * @param message message to append
   */
  void append(SessionId sessionId, ChatMessage message);

  /**
   * Returns the history, oldest message first.
   *
   * @param sessionId session to read
   * @return immutable copy of the history, empty for an unknown session
   */
  List<ChatMessage> history(SessionId sessionId);

  /**
   * Discards the history of a session.
   *
   * @param sessionId session to clear
   */
  void clear(SessionId sessionId);
}
