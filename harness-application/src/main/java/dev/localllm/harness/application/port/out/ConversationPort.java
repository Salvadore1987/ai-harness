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
   * Returns the history sent to the model, oldest message first: the summary of the last
   * compaction as a system message, if any, followed by the messages after it.
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

  /**
   * Replaces all but the last messages of the history with a summary.
   *
   * @param sessionId session to compact
   * @param summary summary of the replaced messages; empty to drop them without a summary
   * @param keepLastMessages number of most recent messages kept verbatim
   */
  void compact(SessionId sessionId, String summary, int keepLastMessages);

  /**
   * Offers the reasoning of a turn for storage; it is never part of {@link #history}.
   * Implementations store it only when configured to.
   *
   * @param sessionId session the reasoning belongs to
   * @param thinking reasoning text of one turn
   */
  void recordThinking(SessionId sessionId, String thinking);
}
