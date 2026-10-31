package dev.localllm.harness.application.port.in;

import java.util.List;

import dev.localllm.harness.application.dto.query.ListProvidersCriteria;
import dev.localllm.harness.application.dto.result.ProviderSummaryResult;

/**
 * Lists the LLM providers.
 */
public interface ListProvidersQuery {

  /**
   * Returns every provider with its availability, the active one marked.
   *
   * @param criteria active provider
   * @return providers in the order of {@link dev.localllm.harness.application.model.LlmProvider}
   */
  List<ProviderSummaryResult> execute(ListProvidersCriteria criteria);
}
