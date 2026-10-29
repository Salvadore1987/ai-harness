package dev.localllm.harness.application.port.in;

import java.util.List;

import dev.localllm.harness.application.dto.query.ListToolsCriteria;
import dev.localllm.harness.application.dto.result.ToolSummaryResult;

/**
 * Lists the tools available to the agent.
 */
public interface ListToolsQuery {

  /**
   * Returns the tools matching the criteria in registration order.
   *
   * @param criteria name filter
   * @return matching tools
   */
  List<ToolSummaryResult> execute(ListToolsCriteria criteria);
}
