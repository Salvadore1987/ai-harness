package dev.localllm.harness.application.service;

import java.util.List;

import dev.localllm.harness.application.dto.command.PurgeSessionsCommand;
import dev.localllm.harness.application.dto.result.PurgedSessionsResult;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.application.port.in.PurgeSessionsUseCase;
import dev.localllm.harness.application.port.out.SessionCatalogPort;

/**
 * Deletes the stored sessions of a directory; the current session stays, so the REPL can go on.
 */
public class PurgeSessionsService implements PurgeSessionsUseCase {

  private final SessionCatalogPort sessionCatalogPort;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param sessionCatalogPort stored session metadata
   * @param mapper converts the count into the result
   */
  public PurgeSessionsService(SessionCatalogPort sessionCatalogPort, TurnApplicationMapper mapper) {
    this.sessionCatalogPort = sessionCatalogPort;
    this.mapper = mapper;
  }

  @Override
  public PurgedSessionsResult execute(PurgeSessionsCommand command) {
    List<SessionId> deleted = sessionCatalogPort.list(command.workingDirectory()).stream()
        .map(SessionInfo::sessionId)
        .filter(sessionId -> !sessionId.equals(command.currentSessionId()))
        .toList();
    deleted.forEach(sessionCatalogPort::delete);
    return mapper.toPurgedSessionsResult(deleted.size());
  }
}
