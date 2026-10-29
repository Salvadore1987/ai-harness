package dev.localllm.harness.application.port.in;

import java.util.List;

import dev.localllm.harness.application.dto.query.ListModelsCriteria;
import dev.localllm.harness.application.dto.result.ModelSummaryResult;

/**
 * Lists the installed models.
 */
public interface ListModelsQuery {

  /**
   * Returns the installed models matching the criteria, sorted by name.
   *
   * @param criteria name filter
   * @return matching models
   */
  List<ModelSummaryResult> execute(ListModelsCriteria criteria);
}
