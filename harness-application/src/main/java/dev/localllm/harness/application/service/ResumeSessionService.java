package dev.localllm.harness.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.localllm.harness.application.dto.command.ResumeSessionCommand;
import dev.localllm.harness.application.dto.result.SessionSummaryResult;
import dev.localllm.harness.application.exception.SessionNotFoundException;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.application.port.in.ResumeSessionUseCase;
import dev.localllm.harness.application.port.out.SessionCatalogPort;

/**
 * Finds the session to continue: the latest of the directory, a full identifier from any
 * directory, or a unique identifier prefix among the sessions of the directory.
 */
public class ResumeSessionService implements ResumeSessionUseCase {

  private final SessionCatalogPort sessionCatalogPort;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param sessionCatalogPort stored session metadata
   * @param mapper converts metadata into the result
   */
  public ResumeSessionService(SessionCatalogPort sessionCatalogPort, TurnApplicationMapper mapper) {
    this.sessionCatalogPort = sessionCatalogPort;
    this.mapper = mapper;
  }

  @Override
  public SessionSummaryResult execute(ResumeSessionCommand command) {
    String reference = command.reference().strip();
    return resolve(command, reference)
        .map(mapper::toSessionSummary)
        .orElseThrow(() -> new SessionNotFoundException(reference));
  }

  private Optional<SessionInfo> resolve(ResumeSessionCommand command, String reference) {
    List<SessionInfo> sessions = sessionCatalogPort.list(command.workingDirectory());
    if (reference.isEmpty()) {
      return sessions.stream().findFirst();
    }
    Optional<SessionId> exact = parse(reference);
    if (exact.isPresent()) {
      return sessionCatalogPort.find(exact.get());
    }
    List<SessionInfo> matches = sessions.stream()
        .filter(session -> session.sessionId().toString().startsWith(reference))
        .toList();
    return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
  }

  private static Optional<SessionId> parse(String reference) {
    try {
      return Optional.of(new SessionId(UUID.fromString(reference)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
