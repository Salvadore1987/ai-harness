package dev.localllm.harness.application.port.in;

import java.util.List;

import dev.localllm.harness.application.dto.query.ListRunningModelsCriteria;
import dev.localllm.harness.application.dto.result.RunningModelResult;

/**
 * Lists the models loaded into memory.
 */
public interface ListRunningModelsQuery {

  /**
   * Returns the loaded models matching the criteria, sorted by name.
   *
   * @param criteria name filter
   * @return matching loaded models
   */
  List<RunningModelResult> execute(ListRunningModelsCriteria criteria);
}
