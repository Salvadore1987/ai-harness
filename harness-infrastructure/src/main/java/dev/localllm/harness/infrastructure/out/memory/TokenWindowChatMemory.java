package dev.localllm.harness.infrastructure.out.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

import dev.localllm.harness.infrastructure.out.memory.dto.JsonlLine;

/**
 * Spring AI chat memory that returns the history after the latest compaction.
 *
 * <p>Unlike {@code MessageWindowChatMemory}, it never trims by message count: the window only
 * changes when the history is compacted, so the prompt prefix stays stable between turns and
 * Ollama can reuse its prompt cache. The size check that triggers compaction lives in the
 * application layer.
 */
@Component
public class TokenWindowChatMemory implements ChatMemory {

  private final JsonlChatMemoryRepository repository;

  /**
   * Creates the memory.
   *
   * @param repository JSONL storage of the messages and compactions
   */
  public TokenWindowChatMemory(JsonlChatMemoryRepository repository) {
    this.repository = repository;
  }

  @Override
  public void add(String conversationId, List<Message> messages) {
    List<Message> all = new ArrayList<>(repository.findByConversationId(conversationId));
    all.addAll(messages);
    repository.saveAll(conversationId, all);
  }

  @Override
  public List<Message> get(String conversationId) {
    List<Message> stored = repository.findByConversationId(conversationId);
    Optional<JsonlLine> compaction = repository.latestCompaction(conversationId);
    if (compaction.isEmpty()) {
      return stored;
    }
    int replaced = Math.min(
        Objects.requireNonNullElse(compaction.get().replacedCount(), 0), stored.size());
    String summary = Objects.requireNonNullElse(compaction.get().summary(), "");
    List<Message> window = new ArrayList<>(stored.size() - replaced + 1);
    if (!summary.isEmpty()) {
      window.add(new SystemMessage(summary));
    }
    window.addAll(stored.subList(replaced, stored.size()));
    return window;
  }

  @Override
  public void clear(String conversationId) {
    repository.deleteByConversationId(conversationId);
  }

  /**
   * Replaces all stored messages except the last ones with a summary.
   *
   * @param conversationId session identifier
   * @param summary summary of the replaced messages; empty to drop them without a summary
   * @param keepLastMessages number of most recent messages kept verbatim
   */
  public void compact(String conversationId, String summary, int keepLastMessages) {
    int stored = repository.findByConversationId(conversationId).size();
    int previouslyReplaced = repository.latestCompaction(conversationId)
        .map(JsonlLine::replacedCount)
        .orElse(0);
    int replaced = Math.max(previouslyReplaced, Math.max(0, stored - keepLastMessages));
    repository.appendCompaction(conversationId, summary, replaced);
  }
}
