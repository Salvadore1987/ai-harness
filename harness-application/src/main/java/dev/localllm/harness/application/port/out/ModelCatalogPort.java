package dev.localllm.harness.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.localllm.harness.application.model.ModelInfo;

/**
 * Lists the models installed on the local model server.
 */
public interface ModelCatalogPort {

  /**
   * Returns all installed models.
   *
   * @return installed models with their capabilities
   */
  List<ModelInfo> listModels();

  /**
   * Looks up one installed model.
   *
   * @param name model tag
   * @return the model, or empty if it is not installed
   */
  Optional<ModelInfo> findModel(String name);
}
