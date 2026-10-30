package dev.localllm.harness.application.port.out;

import java.util.List;

import dev.localllm.harness.application.model.RunningModelInfo;

/**
 * Lists the models the local model server currently holds in memory.
 */
public interface RunningModelsPort {

  /**
   * Returns the loaded models.
   *
   * @return loaded models, empty if none is loaded
   */
  List<RunningModelInfo> listRunning();
}
