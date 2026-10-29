package dev.localllm.harness.application.fixture;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.application.port.out.SessionCatalogPort;

/**
 * Session metadata kept in a map.
 */
public final class FakeSessionCatalogPort implements SessionCatalogPort {

  private final Map<SessionId, SessionInfo> sessions = new LinkedHashMap<>();

  @Override
  public void register(SessionInfo info) {
    sessions.putIfAbsent(info.sessionId(), info);
  }

  @Override
  public Optional<SessionInfo> find(SessionId sessionId) {
    return Optional.ofNullable(sessions.get(sessionId));
  }

  @Override
  public List<SessionInfo> list(Path workingDirectory) {
    return sessions.values().stream()
        .filter(session -> session.workingDirectory().equals(workingDirectory))
        .sorted(Comparator.comparing(SessionInfo::createdAt).reversed())
        .toList();
  }
}
