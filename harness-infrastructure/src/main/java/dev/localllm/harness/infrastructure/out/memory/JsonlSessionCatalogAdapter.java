package dev.localllm.harness.infrastructure.out.memory;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.application.port.out.SessionCatalogPort;

/**
 * Session metadata from the meta lines of the JSONL session files.
 */
@Component
public class JsonlSessionCatalogAdapter implements SessionCatalogPort {

  private final JsonlChatMemoryRepository repository;

  /**
   * Creates the adapter.
   *
   * @param repository JSONL storage
   */
  public JsonlSessionCatalogAdapter(JsonlChatMemoryRepository repository) {
    this.repository = repository;
  }

  @Override
  public void register(SessionInfo info) {
    repository.writeMeta(info);
  }

  @Override
  public Optional<SessionInfo> find(SessionId sessionId) {
    return repository.readSession(sessionId.toString());
  }

  @Override
  public List<SessionInfo> list(Path workingDirectory) {
    Path directory = workingDirectory.toAbsolutePath().normalize();
    return repository.findConversationIds().stream()
        .map(repository::readSession)
        .flatMap(Optional::stream)
        .filter(session -> session.workingDirectory().equals(directory))
        .sorted(Comparator.comparing(SessionInfo::createdAt).reversed())
        .toList();
  }

  @Override
  public void delete(SessionId sessionId) {
    repository.deleteByConversationId(sessionId.toString());
  }
}
