package dev.localllm.harness.application.model;

import java.util.Set;

/**
 * Metadata of a model available on the local model server.
 *
 * @param name model tag, for example {@code qwen3.5:27b}
 * @param parameterSize parameter count as reported by the server, for example {@code 27B}
 * @param quantization quantization level, for example {@code Q4_K_M}
 * @param capabilities optional capabilities declared by the model
 */
public record ModelInfo(
    String name,
    String parameterSize,
    String quantization,
    Set<ModelCapability> capabilities) {

  /**
   * Makes the capability set immutable.
   *
   * @param name model tag
   * @param parameterSize parameter count
   * @param quantization quantization level
   * @param capabilities declared capabilities
   */
  public ModelInfo {
    capabilities = Set.copyOf(capabilities);
  }
}
