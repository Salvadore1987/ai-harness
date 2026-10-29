package dev.localllm.harness.application.service;

import java.util.List;

import dev.localllm.harness.application.dto.query.ListSessionsCriteria;
import dev.localllm.harness.application.dto.result.SessionSummaryResult;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.port.in.ListSessionsQuery;
import dev.localllm.harness.application.port.out.SessionCatalogPort;

/**
 * Lists the stored sessions of a directory, newest first.
 */
public class ListSessionsQueryService implements ListSessionsQuery {

  private final SessionCatalogPort sessionCatalogPort;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param sessionCatalogPort stored session metadata
   * @param mapper converts metadata into summaries
   */
  public ListSessionsQueryService(
      SessionCatalogPort sessionCatalogPort, TurnApplicationMapper mapper) {
    this.sessionCatalogPort = sessionCatalogPort;
    this.mapper = mapper;
  }

  @Override
  public List<SessionSummaryResult> execute(ListSessionsCriteria criteria) {
    return sessionCatalogPort.list(criteria.workingDirectory()).stream()
        .map(mapper::toSessionSummary)
        .toList();
  }
}
