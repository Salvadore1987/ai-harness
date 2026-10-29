package dev.localllm.harness.application.port.in;

import dev.localllm.harness.application.dto.query.ContextUsageCriteria;
import dev.localllm.harness.application.dto.result.ContextUsageResult;

/**
 * Measures how full the context window is.
 */
public interface GetContextUsageQuery {

  /**
   * Estimates the tokens of the history against the model context window.
   *
   * @param criteria session and model
   * @return estimated usage
   */
  ContextUsageResult execute(ContextUsageCriteria criteria);
}
