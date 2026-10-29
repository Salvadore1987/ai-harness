package dev.localllm.harness.application.service;

import java.util.List;

import dev.localllm.harness.application.dto.query.ListToolsCriteria;
import dev.localllm.harness.application.dto.result.ToolSummaryResult;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.port.in.ListToolsQuery;
import dev.localllm.harness.application.port.out.ToolExecutorPort;

/**
 * Lists the available tools matching a name prefix in registration order.
 */
public class ListToolsQueryService implements ListToolsQuery {

  private final ToolExecutorPort toolExecutorPort;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param toolExecutorPort tool registry
   * @param mapper converts tool descriptions into summaries
   */
  public ListToolsQueryService(ToolExecutorPort toolExecutorPort, TurnApplicationMapper mapper) {
    this.toolExecutorPort = toolExecutorPort;
    this.mapper = mapper;
  }

  @Override
  public List<ToolSummaryResult> execute(ListToolsCriteria criteria) {
    return toolExecutorPort.available().stream()
        .filter(tool -> tool.name().startsWith(criteria.namePrefix()))
        .map(mapper::toToolSummary)
        .toList();
  }
}
